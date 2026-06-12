package com.dreamy.domain.member.service;

import com.dreamy.enums.AssignStatus;
import com.dreamy.enums.VoteValue;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository.VoteCounts;
import com.dreamy.domain.member.service.ShowroomInteractionService.Interactor;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import com.dreamy.dto.ShowroomDtos.CommentCreate;
import com.dreamy.dto.ShowroomDtos.ShowroomCommentDto;
import com.dreamy.dto.ShowroomDtos.VoteRequest;
import com.dreamy.dto.ShowroomDtos.VoteResultDto;
import com.dreamy.error.ShowroomErrorCode;
import com.dreamy.error.ShowroomException;
import com.dreamy.testsupport.ImmediateShowroomTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-SHR-10/11 投票/留言双态互动单元测试。
 * L2 TRACE: TC-SHR-014 单测面（UPSERT 幂等承载 + 实时聚合回读）/ TC-SHR-015 单测面（owner 自动建
 * member 落投票）/ TC-SHR-024 单测面（member_id 仅取鉴权主体，bs-727~730 不可达）/
 * TC-SHR-036 单测面（其他登录用户 404101 / item 跨房 404102）/ TC-SHR-040 单测面（双身份各自 member）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomInteractionServiceTest {

    private static final long ROOM = 8L;
    private static final long ITEM = 21L;
    private static final long OWNER = 1L;
    private static final long GUEST_MEMBER = 77L;

    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomItemRepository itemRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    ShowroomVoteRepository voteRepository;
    @Mock
    ShowroomCommentRepository commentRepository;
    @Mock
    MemberResolver memberResolver;

    ShowroomInteractionService service;

    @BeforeEach
    void setUp() {
        service = new ShowroomInteractionService(showroomRepository, itemRepository, memberRepository,
                voteRepository, commentRepository, memberResolver, new ImmediateShowroomTxRunner());
    }

    private void stubRoomAndItem() {
        Showroom room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        lenient().when(showroomRepository.findById(ROOM)).thenReturn(room);
        lenient().when(showroomRepository.findByIdAndOwner(ROOM, OWNER)).thenReturn(room);
        ShowroomItem item = new ShowroomItem();
        item.setId(ITEM);
        item.setShowroomId(ROOM);
        item.setProductId(11L);
        lenient().when(itemRepository.findByIdAndShowroom(ITEM, ROOM)).thenReturn(item);
    }

    @Test
    @DisplayName("guest 投票：member_id 仅取 GuestContext（不信任请求体）→ UPSERT + 实时聚合")
    void guestVoteUpsert() {
        stubRoomAndItem();
        when(voteRepository.aggregateByItems(anyCollection()))
                .thenReturn(Map.of(ITEM, new VoteCounts(3, 1)));

        VoteResultDto dto = service.vote(Interactor.guest(GUEST_MEMBER), ROOM, ITEM,
                new VoteRequest(1));

        verify(voteRepository).upsert(ITEM, GUEST_MEMBER, VoteValue.LIKE);
        assertThat(dto.likeCount()).isEqualTo(3);
        assertThat(dto.dislikeCount()).isEqualTo(1);
        assertThat(dto.myVote()).isEqualTo(1);
    }

    @Test
    @DisplayName("owner 投票：首次互动经 MemberResolver 自动建 member 落该 member（TC-SHR-015）")
    void ownerVoteAutoMember() {
        stubRoomAndItem();
        ShowroomMember member = new ShowroomMember();
        member.setId(99L);
        when(memberResolver.resolveStoreMember(ROOM, OWNER)).thenReturn(member);
        when(voteRepository.aggregateByItems(anyCollection()))
                .thenReturn(Map.of(ITEM, new VoteCounts(1, 0)));

        service.vote(Interactor.store(OWNER), ROOM, ITEM, new VoteRequest(2));
        verify(voteRepository).upsert(ITEM, 99L, VoteValue.DISLIKE);
    }

    @Test
    @DisplayName("其他登录用户投票 → 404101 防探测（owner 强隔离）")
    void strangerVoteNotFound() {
        when(showroomRepository.findByIdAndOwner(ROOM, 666L)).thenReturn(null);
        assertThatThrownBy(() -> service.vote(Interactor.store(666L), ROOM, ITEM, new VoteRequest(1)))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("item 跨房（不属本 showroom）→ 404102（CV-SHR-006 双键点查，bs-725）")
    void crossRoomItemNotFound() {
        stubRoomAndItem();
        when(itemRepository.findByIdAndShowroom(999L, ROOM)).thenReturn(null);
        assertThatThrownBy(() -> service.vote(Interactor.guest(GUEST_MEMBER), ROOM, 999L,
                new VoteRequest(1)))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("guest 留言：member_id=GuestContext、nickname 联 member 派生、created_at 服务端生成")
    void guestComment() {
        stubRoomAndItem();
        ShowroomMember member = new ShowroomMember();
        member.setId(GUEST_MEMBER);
        member.setNickname("Emma");
        member.setAssignStatus(AssignStatus.UNASSIGNED);
        when(memberRepository.findById(GUEST_MEMBER)).thenReturn(member);

        ShowroomCommentDto dto = service.comment(Interactor.guest(GUEST_MEMBER), ROOM, ITEM,
                new CommentCreate("  Love this shade!  "));

        verify(commentRepository).insert(any());
        assertThat(dto.memberId()).isEqualTo(GUEST_MEMBER);
        assertThat(dto.nickname()).isEqualTo("Emma");
        assertThat(dto.content()).isEqualTo("Love this shade!");
        assertThat(dto.createdAt()).isNotNull();
    }
}
