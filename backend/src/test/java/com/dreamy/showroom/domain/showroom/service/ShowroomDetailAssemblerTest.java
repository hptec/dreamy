package com.dreamy.showroom.domain.showroom.service;

import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.enums.VoteValue;
import com.dreamy.showroom.domain.member.entity.ShowroomComment;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.showroom.domain.member.repository.ShowroomVoteRepository.VoteCounts;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.dto.ShowroomDtos.ShowroomDetailDto;
import com.dreamy.showroom.port.CatalogSnapshotPort;
import com.dreamy.showroom.port.CatalogSnapshotPort.ProductCardBrief;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ShowroomDetail 装配与视图裁剪单元测试。
 * L2 TRACE: TC-SHR-033 [P0]（owner 含 invite_token + members[].email/linked_customer_id；
 * guest 三者全不出现——字段为 null 经 NON_NULL 不序列化）/ TC-SHR-032 单测面（票数聚合/my_vote/
 * 留言 nickname 派生/dye_lot_notice）/ 脏引用容忍降级（商品缺失 item 跳过不 5xx）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomDetailAssemblerTest {

    private static final long ROOM = 8L;

    @Mock
    ShowroomItemRepository itemRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    ShowroomVoteRepository voteRepository;
    @Mock
    ShowroomCommentRepository commentRepository;
    @Mock
    CatalogSnapshotPort catalogPort;
    @Mock
    DyeLotService dyeLotService;

    ShowroomDetailAssembler assembler;

    Showroom room;

    @BeforeEach
    void setUp() {
        assembler = new ShowroomDetailAssembler(itemRepository, memberRepository, voteRepository,
                commentRepository, catalogPort, dyeLotService);
        room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(1L);
        room.setName("Sarah's Bridal Party");
        room.setInviteToken("tok-1");
        room.setInviteVersion(1);

        ShowroomItem item = new ShowroomItem();
        item.setId(21L);
        item.setShowroomId(ROOM);
        item.setProductId(11L);
        item.setColor("Sage");
        item.setLastOrderedAt(LocalDateTime.now().minusHours(1));
        lenient().when(itemRepository.listByShowroom(ROOM)).thenReturn(List.of(item));

        ShowroomMember member = new ShowroomMember();
        member.setId(77L);
        member.setShowroomId(ROOM);
        member.setNickname("Emma");
        member.setEmail("emma@example.com");
        member.setLinkedCustomerId(555L);
        member.setAssignStatus(AssignStatus.ASSIGNED);
        member.setAssignedItemId(21L);
        lenient().when(memberRepository.listByShowroom(ROOM)).thenReturn(List.of(member));

        lenient().when(catalogPort.getProductCards(anyCollection(), anyString()))
                .thenReturn(Map.of(11L, new ProductCardBrief(11L, "meadow-bridesmaid", "Meadow",
                        new BigDecimal("158"), "/img.jpg", true, 7, true)));
        lenient().when(voteRepository.aggregateByItems(anyCollection()))
                .thenReturn(Map.of(21L, new VoteCounts(4, 0)));
        lenient().when(voteRepository.listByMemberAndItems(anyLong(), anyCollection()))
                .thenReturn(Map.of(21L, VoteValue.LIKE));
        ShowroomComment comment = new ShowroomComment();
        comment.setId(31L);
        comment.setShowroomItemId(21L);
        comment.setMemberId(77L);
        comment.setContent("Love it");
        comment.setCreatedAt(LocalDateTime.now());
        lenient().when(commentRepository.listByItems(anyCollection())).thenReturn(List.of(comment));
        lenient().when(dyeLotService.isWithinWindow(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
    }

    @Test
    @DisplayName("owner 视图：invite_token + members[].email/linked_customer_id 输出；聚合/my_vote/留言齐全")
    void ownerView() {
        ShowroomDetailDto dto = assembler.assemble(room, true, 77L, "en");

        assertThat(dto.inviteToken()).isEqualTo("tok-1");
        assertThat(dto.isOwner()).isTrue();
        assertThat(dto.myMemberId()).isEqualTo(77L);
        assertThat(dto.members().get(0).email()).isEqualTo("emma@example.com");
        assertThat(dto.members().get(0).linkedCustomerId()).isEqualTo(555L);

        var item = dto.items().get(0);
        assertThat(item.likeCount()).isEqualTo(4);
        assertThat(item.myVote()).isEqualTo("like");
        assertThat(item.comments()).hasSize(1);
        assertThat(item.comments().get(0).nickname()).isEqualTo("Emma");
        assertThat(item.dyeLotNotice()).isTrue();
        assertThat(item.product().slug()).isEqualTo("meadow-bridesmaid");
    }

    @Test
    @DisplayName("guest 视图：invite_token/members[].email/linked_customer_id 全不输出（TC-SHR-033）")
    void guestViewTrimmed() {
        ShowroomDetailDto dto = assembler.assemble(room, false, 77L, "en");

        assertThat(dto.inviteToken()).isNull();
        assertThat(dto.isOwner()).isFalse();
        assertThat(dto.members().get(0).email()).isNull();
        assertThat(dto.members().get(0).linkedCustomerId()).isNull();
        // 非敏感字段保留
        assertThat(dto.members().get(0).nickname()).isEqualTo("Emma");
        assertThat(dto.members().get(0).assignStatus()).isEqualTo("assigned");
    }

    @Test
    @DisplayName("脏引用容忍降级：端口缺失的 product_id → item 不输出（记告警不 5xx），计数随之收敛")
    void missingProductSkipsItem() {
        when(catalogPort.getProductCards(anyCollection(), anyString())).thenReturn(Map.of());
        ShowroomDetailDto dto = assembler.assemble(room, true, null, "en");
        assertThat(dto.items()).isEmpty();
        assertThat(dto.itemCount()).isZero();
    }

    @Test
    @DisplayName("无 my member（owner 未互动）：my_member_id 省略、my_vote 全部省略")
    void noMyMember() {
        ShowroomDetailDto dto = assembler.assemble(room, true, null, "en");
        assertThat(dto.myMemberId()).isNull();
        assertThat(dto.items().get(0).myVote()).isNull();
    }
}
