package com.dreamy.mq;

import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import com.dreamy.enums.AssignStatus;
import com.dreamy.domain.member.entity.ShowroomMember;
import com.dreamy.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.domain.showroom.entity.ShowroomItem;
import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EVT-SHR-003 q.showroom 消费 order.paid 单元测试。
 * L2 TRACE: TC-SHR-020 [P0]（命中订单行推进 ordered / 不含被指派款式不推进 bs-836/837 /
 * 未绑定成员不可达 / 同 event_id 幂等空操作）/ TC-SHR-021 单测面（dye lot 参与域回写）/
 * TC-SHR-054 单测面（消费异常 → 幂等键释放允许重放）/ TC-SHR-030（place_order 消费侧推进）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomOrderPaidConsumerTest {

    private static final long CUSTOMER = 555L;

    @Mock
    EventIdempotencyGuard idempotencyGuard;
    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomItemRepository itemRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    TransactionTemplate transactionTemplate;

    ShowroomOrderPaidConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ShowroomOrderPaidConsumer(idempotencyGuard, showroomRepository, itemRepository,
                memberRepository, transactionTemplate);
        lenient().when(idempotencyGuard.tryAcquire(any(), any())).thenReturn(true);
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> work = invocation.getArgument(0);
            work.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private DomainEvent orderPaid(Object... lineProductIds) {
        List<Map<String, Object>> lines = java.util.Arrays.stream(lineProductIds)
                .map(pid -> Map.<String, Object>of("product_id", pid, "qty", 1))
                .toList();
        return new DomainEvent("evt-1", "order.paid", "2026-06-10T12:00:00Z",
                Map.of("customer_id", CUSTOMER, "order_no", "DR-1", "lines", lines));
    }

    private ShowroomMember member(long id, Long assignedItemId, AssignStatus status) {
        ShowroomMember m = new ShowroomMember();
        m.setId(id);
        m.setShowroomId(8L);
        m.setNickname("Emma");
        m.setAssignedItemId(assignedItemId);
        m.setAssignStatus(status);
        m.setLinkedCustomerId(CUSTOMER);
        return m;
    }

    private ShowroomItem item(long id, long productId) {
        ShowroomItem i = new ShowroomItem();
        i.setId(id);
        i.setShowroomId(8L);
        i.setProductId(productId);
        return i;
    }

    @Test
    @DisplayName("绑定成员 + assigned_item 命中订单行 → casOrder 推进；dye lot 参与域回写")
    void progressionAndDyeLotWriteback() {
        when(memberRepository.listByLinkedCustomer(CUSTOMER))
                .thenReturn(List.of(member(31L, 21L, AssignStatus.ASSIGNED)));
        when(itemRepository.listByIds(anyCollection())).thenReturn(List.of(item(21L, 11L)));
        when(showroomRepository.listIdsByCustomerParticipation(CUSTOMER)).thenReturn(List.of(8L, 9L));

        consumer.onEvent(orderPaid(11L, 12L));

        verify(memberRepository).casOrder(31L);
        verify(itemRepository).touchLastOrdered(eqList(8L, 9L), anyCollection(), any());
    }

    @Test
    @DisplayName("订单行不含被指派款式 → 不推进（bs-836/837 guard）；回写仍按参与域执行")
    void noProgressionWhenProductNotInLines() {
        when(memberRepository.listByLinkedCustomer(CUSTOMER))
                .thenReturn(List.of(member(31L, 21L, AssignStatus.ASSIGNED)));
        when(itemRepository.listByIds(anyCollection())).thenReturn(List.of(item(21L, 99L)));
        when(showroomRepository.listIdsByCustomerParticipation(CUSTOMER)).thenReturn(List.of(8L));

        consumer.onEvent(orderPaid(11L));

        verify(memberRepository, never()).casOrder(anyLong());
        verify(itemRepository).touchLastOrdered(anyCollection(), anyCollection(), any());
    }

    @Test
    @DisplayName("未绑定成员（linked_customer_id NULL）不可达不推进——定位前提天然承载")
    void unboundMemberUnreachable() {
        when(memberRepository.listByLinkedCustomer(CUSTOMER)).thenReturn(List.of());
        when(showroomRepository.listIdsByCustomerParticipation(CUSTOMER)).thenReturn(List.of());

        consumer.onEvent(orderPaid(11L));
        verify(memberRepository, never()).casOrder(anyLong());
    }

    @Test
    @DisplayName("同 event_id 重投 → SETNX 拦截空操作（幂等闸）")
    void duplicateEventSkipped() {
        when(idempotencyGuard.tryAcquire(ShowroomOrderPaidConsumer.QUEUE, "evt-1")).thenReturn(false);
        consumer.onEvent(orderPaid(11L));
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    @DisplayName("消费异常 → 幂等键释放允许重放（TC-SHR-054 口径）+ 异常上抛走重试阶梯")
    void failureReleasesIdempotencyKey() {
        when(memberRepository.listByLinkedCustomer(CUSTOMER)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> consumer.onEvent(orderPaid(11L)))
                .isInstanceOf(RuntimeException.class);
        verify(idempotencyGuard).release(ShowroomOrderPaidConsumer.QUEUE, "evt-1");
    }

    @Test
    @DisplayName("队列与绑定声明：q.showroom ← order.paid（拓扑登记一致）")
    void queueTopology() {
        assertThat(consumer.queue()).isEqualTo("q.showroom");
        assertThat(consumer.bindingKeys()).containsExactly("order.paid");
    }

    @SuppressWarnings("unchecked")
    private static java.util.Collection<Long> eqList(Long... values) {
        return org.mockito.ArgumentMatchers.eq(List.of(values));
    }
}
