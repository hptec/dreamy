package com.dreamy.domain.showroom.service;

import com.dreamy.domain.member.repository.ShowroomCommentRepository;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.member.repository.ShowroomVoteRepository;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import com.dreamy.dto.ShowroomDtos.InviteTokenDto;
import com.dreamy.dto.ShowroomDtos.ShowroomDetailDto;
import com.dreamy.dto.ShowroomDtos.ShowroomUpsert;
import com.dreamy.error.ShowroomErrorCode;
import com.dreamy.error.ShowroomException;
import com.dreamy.testsupport.ImmediateShowroomTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-SHR-01/02/04/05/06 协作空间 CRUD 与邀请重置单元测试。
 * L2 TRACE: TC-SHR-009 单测面（invite_token UUID + invite_version=1）/ TC-SHR-013 单测面
 * （reset 调 RM-SHR-006 新 UUID）/ TC-SHR-018 单测面（删除级联 5 表顺序）/
 * TC-SHR-026 单测面（列表派生计数）/ TC-SHR-036/039 单测面（跨用户 404101 防探测）。
 */
@ExtendWith(MockitoExtension.class)
class StoreShowroomServiceTest {

    private static final long OWNER = 1L;
    private static final long ROOM = 8L;

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
    ShowroomDetailAssembler assembler;

    StoreShowroomService service;

    @BeforeEach
    void setUp() {
        service = new StoreShowroomService(showroomRepository, itemRepository, memberRepository,
                voteRepository, commentRepository, assembler, new ImmediateShowroomTxRunner());
    }

    @Test
    @DisplayName("创建：invite_token 为合法 UUID、invite_version=1、owner_id=subject（TC-SHR-009）")
    void createGeneratesInviteToken() {
        doAnswer(invocation -> {
            Showroom s = invocation.getArgument(0);
            s.setId(ROOM);
            return null;
        }).when(showroomRepository).insert(any(Showroom.class));
        when(assembler.assemble(any(), anyBoolean(), isNull(), anyString()))
                .thenReturn(new ShowroomDetailDto(ROOM, OWNER, "Sarah's Bridal Party", null, 0, 0,
                        "tok", true, null, List.of(), List.of()));

        service.create(OWNER, new ShowroomUpsert("Sarah's Bridal Party", "2026-09-19"));

        ArgumentCaptor<Showroom> captor = ArgumentCaptor.forClass(Showroom.class);
        verify(showroomRepository).insert(captor.capture());
        Showroom inserted = captor.getValue();
        assertThat(inserted.getOwnerId()).isEqualTo(OWNER);
        assertThat(inserted.getInviteVersion()).isEqualTo(1);
        assertThat(UUID.fromString(inserted.getInviteToken())).isNotNull();
        assertThat(inserted.getWeddingDate()).isNotNull();
    }

    @Test
    @DisplayName("列表：仅 owner 自己的 + RM-SHR-009 派生计数（TC-SHR-026）")
    void listWithDerivedCounts() {
        Showroom room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        room.setName("R");
        when(showroomRepository.listByOwner(OWNER)).thenReturn(List.of(room));
        when(showroomRepository.countSummary(List.of(ROOM)))
                .thenReturn(Map.of(ROOM, new ShowroomRepository.SummaryCounts(3, 5)));

        var list = service.list(OWNER);
        assertThat(list.items()).hasSize(1);
        assertThat(list.items().get(0).itemCount()).isEqualTo(3);
        assertThat(list.items().get(0).memberCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("跨用户 GET/PUT/DELETE/reset → 404101 防探测（CV-SHR-007，bs-640）")
    void crossUserNotFound() {
        when(showroomRepository.findByIdAndOwner(ROOM, 666L)).thenReturn(null);
        assertThatThrownBy(() -> service.getForOwner(666L, ROOM, "en"))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_NOT_FOUND));
        assertThatThrownBy(() -> service.update(666L, ROOM, new ShowroomUpsert("X", null)))
                .isInstanceOf(ShowroomException.class);
        assertThatThrownBy(() -> service.delete(666L, ROOM))
                .isInstanceOf(ShowroomException.class);
        assertThatThrownBy(() -> service.resetInvite(666L, ROOM))
                .isInstanceOf(ShowroomException.class);
    }

    @Test
    @DisplayName("删除级联（TX-SHR-003）：comment→vote→item→member→showroom 顺序、整体在事务内")
    void deleteCascadeOrder() {
        Showroom room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        when(showroomRepository.findByIdAndOwner(ROOM, OWNER)).thenReturn(room);
        var item = new com.dreamy.domain.showroom.entity.ShowroomItem();
        item.setId(21L);
        when(itemRepository.listByShowroom(ROOM)).thenReturn(List.of(item));

        service.delete(OWNER, ROOM);

        InOrder order = Mockito.inOrder(commentRepository, voteRepository, itemRepository,
                memberRepository, showroomRepository);
        order.verify(commentRepository).deleteByItems(List.of(21L));
        order.verify(voteRepository).deleteByItems(List.of(21L));
        order.verify(itemRepository).deleteByShowroom(ROOM);
        order.verify(memberRepository).deleteByShowroom(ROOM);
        order.verify(showroomRepository).deleteById(ROOM);
    }

    @Test
    @DisplayName("重置邀请：RM-SHR-006 新 UUID 单语句原子，响应返回新 token（仅 owner 可见）")
    void resetInviteRotatesToken() {
        Showroom room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        room.setInviteToken("old-token");
        when(showroomRepository.findByIdAndOwner(ROOM, OWNER)).thenReturn(room);

        InviteTokenDto dto = service.resetInvite(OWNER, ROOM);
        assertThat(dto.inviteToken()).isNotEqualTo("old-token");
        assertThat(UUID.fromString(dto.inviteToken())).isNotNull();
        verify(showroomRepository).resetInvite(eq(ROOM), eq(dto.inviteToken()));
    }

    @Test
    @DisplayName("name 缺失 → 422101 fields.name=blank（V-SHR-001）")
    void createValidation() {
        assertThatThrownBy(() -> service.create(OWNER, new ShowroomUpsert("  ", null)))
                .isInstanceOfSatisfying(ShowroomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(ex.getDetails()).containsKey("fields");
                });
    }
}
