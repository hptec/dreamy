package com.dreamy.domain.member.service;

import com.dreamy.enums.AssignStatus;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import com.dreamy.dto.ShowroomDtos.AssignRequest;
import com.dreamy.dto.ShowroomDtos.ShowroomMemberDto;
import com.dreamy.error.ShowroomErrorCode;
import com.dreamy.error.ShowroomException;
import com.dreamy.mq.ShowroomEventPublisher;
import com.dreamy.mq.ShowroomEventPublisher.MailEventPayload;
import com.dreamy.port.ShowroomCatalogSnapshotPort;
import com.dreamy.port.ShowroomCatalogSnapshotPort.ProductCardBrief;
import com.dreamy.testsupport.ImmediateAfterCommitRunner;
import com.dreamy.testsupport.ImmediateShowroomTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-SHR-12/13 指派/提醒状态机与邮件触发单元测试。
 * L2 TRACE: TC-SHR-008 [P0]（invite 触发条件四象限：首填发/变更发/未提供不发/同值不发）/
 * TC-SHR-022 单测面（remind guard 409103 details reason 二分：not_assigned / email_missing；
 * reminded 自环重发）/ TC-SHR-027/031 单测面（CAS affected=0 → 409103 ordered；404102/404103 guard）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomMemberServiceTest {

    private static final long ROOM = 8L;
    private static final long MEMBER = 31L;
    private static final long ITEM = 21L;
    private static final long OWNER = 1L;

    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomItemRepository itemRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    ShowroomCatalogSnapshotPort catalogPort;
    @Mock
    ShowroomEventPublisher eventPublisher;

    ShowroomMemberService service;

    Showroom room;
    ShowroomItem item;

    @BeforeEach
    void setUp() {
        service = new ShowroomMemberService(showroomRepository, itemRepository, memberRepository,
                catalogPort, eventPublisher, new ImmediateAfterCommitRunner(),
                new ImmediateShowroomTxRunner());
        room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(OWNER);
        room.setName("Sarah's Bridal Party");
        room.setInviteToken("tok-1");
        room.setInviteVersion(1);
        item = new ShowroomItem();
        item.setId(ITEM);
        item.setShowroomId(ROOM);
        item.setProductId(11L);
        item.setColor("Sage");
        lenient().when(showroomRepository.findByIdAndOwner(ROOM, OWNER)).thenReturn(room);
        lenient().when(itemRepository.findByIdAndShowroom(ITEM, ROOM)).thenReturn(item);
        lenient().when(catalogPort.getProductCards(anyCollection(), anyString()))
                .thenReturn(Map.of(11L, new ProductCardBrief(11L, "meadow-bridesmaid",
                        "Meadow Sage Bridesmaid Dress", new BigDecimal("158"), null, true, 7, true)));
    }

    private ShowroomMember member(AssignStatus status, String email) {
        ShowroomMember m = new ShowroomMember();
        m.setId(MEMBER);
        m.setShowroomId(ROOM);
        m.setNickname("Emma");
        m.setEmail(email);
        m.setAssignStatus(status);
        m.setAssignedItemId(status == AssignStatus.UNASSIGNED ? null : ITEM);
        return m;
    }

    // ==================== E-SHR-12 assign ====================

    @Test
    @DisplayName("首填 email 指派成功 → CAS 推进 + 事务提交后发 showroom.invite（TC-SHR-008 首填发）")
    void assignFirstEmailPublishesInvite() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM)).thenReturn(member(AssignStatus.UNASSIGNED, null));
        when(memberRepository.casAssign(MEMBER, ITEM, "emma@example.com")).thenReturn(1);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.ASSIGNED, "emma@example.com"));

        ShowroomMemberDto dto = service.assign(OWNER, ROOM, MEMBER,
                new AssignRequest(ITEM, "emma@example.com"));

        assertThat(dto.assignStatus()).isEqualTo("assigned");
        // owner 视图含 email
        assertThat(dto.email()).isEqualTo("emma@example.com");
        ArgumentCaptor<MailEventPayload> captor = ArgumentCaptor.forClass(MailEventPayload.class);
        verify(eventPublisher).publishInvite(captor.capture());
        assertThat(captor.getValue().productName()).isEqualTo("Meadow Sage Bridesmaid Dress");
        assertThat(captor.getValue().inviteToken()).isEqualTo("tok-1");
    }

    @Test
    @DisplayName("email 未提供（COALESCE 保留）→ 不发 invite；同值重提 → 不发（防骚扰，TC-SHR-008）")
    void assignWithoutEmailChangeDoesNotPublish() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM))
                .thenReturn(member(AssignStatus.ASSIGNED, "emma@example.com"));
        when(memberRepository.casAssign(MEMBER, ITEM, null)).thenReturn(1);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.ASSIGNED, "emma@example.com"));
        service.assign(OWNER, ROOM, MEMBER, new AssignRequest(ITEM, null));
        verify(eventPublisher, never()).publishInvite(any());

        // 同值重提
        when(memberRepository.casAssign(MEMBER, ITEM, "emma@example.com")).thenReturn(1);
        service.assign(OWNER, ROOM, MEMBER, new AssignRequest(ITEM, "emma@example.com"));
        verify(eventPublisher, never()).publishInvite(any());
    }

    @Test
    @DisplayName("ordered 终态再指派：CAS affected=0 → 409103 details.assign_status=ordered（bs-841）")
    void assignOrderedRejected() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM)).thenReturn(member(AssignStatus.ORDERED, null));
        when(memberRepository.casAssign(MEMBER, ITEM, null)).thenReturn(0);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.ORDERED, null));

        assertThatThrownBy(() -> service.assign(OWNER, ROOM, MEMBER, new AssignRequest(ITEM, null)))
                .isInstanceOfSatisfying(ShowroomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.MEMBER_STATE_INVALID);
                    assertThat(ex.getDetails()).containsEntry("assign_status", "ordered");
                });
    }

    @Test
    @DisplayName("assigned_item_id 不属本房 → 404102（V-SHR-021，state-machine guard bs-832）")
    void assignForeignItemRejected() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM)).thenReturn(member(AssignStatus.UNASSIGNED, null));
        when(itemRepository.findByIdAndShowroom(999L, ROOM)).thenReturn(null);
        assertThatThrownBy(() -> service.assign(OWNER, ROOM, MEMBER, new AssignRequest(999L, null)))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_ITEM_NOT_FOUND));
    }

    @Test
    @DisplayName("member 跨房 → 404103；showroom 跨用户 → 404101（防探测）")
    void notFoundGuards() {
        when(memberRepository.findByIdAndShowroom(999L, ROOM)).thenReturn(null);
        assertThatThrownBy(() -> service.assign(OWNER, ROOM, 999L, new AssignRequest(ITEM, null)))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_MEMBER_NOT_FOUND));

        when(showroomRepository.findByIdAndOwner(ROOM, 666L)).thenReturn(null);
        assertThatThrownBy(() -> service.assign(666L, ROOM, MEMBER, new AssignRequest(ITEM, null)))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.SHOWROOM_NOT_FOUND));
    }

    // ==================== E-SHR-13 remind ====================

    @Test
    @DisplayName("assigned 有 email → 200 reminded + showroom.remind 发布（TC-SHR-022）")
    void remindHappyPath() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM))
                .thenReturn(member(AssignStatus.ASSIGNED, "emma@example.com"));
        when(memberRepository.casRemind(MEMBER)).thenReturn(1);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.REMINDED, "emma@example.com"));

        ShowroomMemberDto dto = service.remind(OWNER, ROOM, MEMBER);
        assertThat(dto.assignStatus()).isEqualTo("reminded");
        verify(eventPublisher).publishRemind(any());
    }

    @Test
    @DisplayName("unassigned remind → 409103 reason=not_assigned（bs-838）")
    void remindNotAssigned() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM)).thenReturn(member(AssignStatus.UNASSIGNED, null));
        when(memberRepository.casRemind(MEMBER)).thenReturn(0);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.UNASSIGNED, null));

        assertThatThrownBy(() -> service.remind(OWNER, ROOM, MEMBER))
                .isInstanceOfSatisfying(ShowroomException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.MEMBER_STATE_INVALID);
                    assertThat(ex.getDetails()).containsEntry("reason", "not_assigned")
                            .containsEntry("assign_status", "unassigned");
                });
        verify(eventPublisher, never()).publishRemind(any());
    }

    @Test
    @DisplayName("assigned 无 email → 409103 reason=email_missing（bs-835）")
    void remindEmailMissing() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM)).thenReturn(member(AssignStatus.ASSIGNED, null));
        when(memberRepository.casRemind(MEMBER)).thenReturn(0);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.ASSIGNED, null));

        assertThatThrownBy(() -> service.remind(OWNER, ROOM, MEMBER))
                .isInstanceOfSatisfying(ShowroomException.class, ex ->
                        assertThat(ex.getDetails()).containsEntry("reason", "email_missing")
                                .containsEntry("assign_status", "assigned"));
    }

    @Test
    @DisplayName("reminded 重发自环：状态保持 reminded + 事件再发（契约 v1.1.0 口径定稿）")
    void remindedResendLoop() {
        when(memberRepository.findByIdAndShowroom(MEMBER, ROOM))
                .thenReturn(member(AssignStatus.REMINDED, "emma@example.com"));
        when(memberRepository.casRemind(MEMBER)).thenReturn(1);
        when(memberRepository.findById(MEMBER)).thenReturn(member(AssignStatus.REMINDED, "emma@example.com"));

        ShowroomMemberDto dto = service.remind(OWNER, ROOM, MEMBER);
        assertThat(dto.assignStatus()).isEqualTo("reminded");
        verify(eventPublisher).publishRemind(any());
    }
}
