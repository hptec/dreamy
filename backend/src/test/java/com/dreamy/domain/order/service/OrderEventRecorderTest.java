package com.dreamy.domain.order.service;

import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.order.entity.OrderEvent;
import com.dreamy.domain.order.repository.OrderEventRepository;
import com.dreamy.dto.TradingDtos.OrderEventDto;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import com.dreamy.enums.OrderStatus;
import com.dreamy.enums.ProductionStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 订单时间线记录器单测：截断 / payload 复制 / 状态与阶段便捷方法 / 消费端隐藏 actor_name / 后台批量解析姓名。 */
@ExtendWith(MockitoExtension.class)
class OrderEventRecorderTest {

    @Mock
    OrderEventRepository orderEventRepository;
    @Mock
    AdminUserMapper adminUserMapper;

    @InjectMocks
    OrderEventRecorder recorder;

    @Test
    @DisplayName("record：title>128/detail>512 截断；payload 复制为新 Map；空 payload → null")
    void recordTruncatesAndCopies() {
        Map<String, Object> payload = new java.util.HashMap<>(Map.of("k", 1));
        OrderEvent event = recorder.record(1L, OrderEventType.NOTE, OrderActorType.ADMIN, 3L,
                "x".repeat(200), "y".repeat(600), payload, false);
        assertThat(event.getTitle()).hasSize(128);
        assertThat(event.getDetail()).hasSize(512);
        assertThat(event.getPayload()).isNotSameAs(payload).containsEntry("k", 1);
        assertThat(event.getCustomerVisible()).isFalse();
        verify(orderEventRepository).insert(event);
        OrderEvent empty = recorder.record(1L, OrderEventType.NOTE, OrderActorType.SYSTEM, null, "t", null, Map.of(), true);
        assertThat(empty.getPayload()).isNull();
    }

    @Test
    @DisplayName("statusChanged / productionStageChanged：payload from/to 整数码；null from 允许（创建 PENDING）")
    void convenienceMethods() {
        OrderEvent created = recorder.statusChanged(1L, null, OrderStatus.PENDING, OrderActorType.CUSTOMER, 7L, "placed", true);
        assertThat(created.getType()).isEqualTo(OrderEventType.STATUS_CHANGED);
        assertThat(created.getPayload()).containsEntry("from", null).containsEntry("to", 1);
        assertThat(created.getTitle()).isEqualTo("Order pending");

        OrderEvent stage = recorder.productionStageChanged(1L, ProductionStage.PENDING_REVIEW,
                ProductionStage.IN_PRODUCTION, OrderActorType.ADMIN, 3L, null);
        assertThat(stage.getType()).isEqualTo(OrderEventType.PRODUCTION);
        assertThat(stage.getPayload()).containsEntry("from_stage", 1).containsEntry("to_stage", 2);
        assertThat(stage.getCustomerVisible()).isTrue();

        OrderEvent finished = recorder.productionStageChanged(1L, ProductionStage.READY_TO_SHIP, null,
                OrderActorType.ADMIN, 3L, "shipped");
        assertThat(finished.getTitle()).isEqualTo("Production finished");
    }

    @Test
    @DisplayName("listCustomerVisible：只读可见行，actor_name 一律 null（不暴露后台操作者），不查 admin_user")
    void customerViewHidesActorName() {
        OrderEvent e = new OrderEvent();
        e.setId(1L);
        e.setOrderId(9L);
        e.setType(OrderEventType.NOTE);
        e.setActorType(OrderActorType.ADMIN);
        e.setActorId(3L);
        e.setTitle("Note");
        e.setCustomerVisible(true);
        when(orderEventRepository.listCustomerVisibleByOrderId(9L)).thenReturn(List.of(e));
        List<OrderEventDto> dtos = recorder.listCustomerVisible(9L);
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).actorName()).isNull();
        assertThat(dtos.get(0).actorType()).isEqualTo(3);
        verify(adminUserMapper, never()).selectByIds(any());
    }

    @Test
    @DisplayName("listAdmin：全量 + ADMIN 触发者姓名批量解析（一次 selectByIds）")
    void adminViewResolvesNames() {
        OrderEvent a = new OrderEvent();
        a.setId(1L);
        a.setType(OrderEventType.NOTE);
        a.setActorType(OrderActorType.ADMIN);
        a.setActorId(3L);
        a.setTitle("Internal note");
        a.setCustomerVisible(false);
        OrderEvent b = new OrderEvent();
        b.setId(2L);
        b.setType(OrderEventType.STATUS_CHANGED);
        b.setActorType(OrderActorType.SYSTEM);
        b.setTitle("Order paid");
        b.setCustomerVisible(true);
        when(orderEventRepository.listByOrderId(9L)).thenReturn(List.of(a, b));
        AdminUser admin = new AdminUser();
        admin.setId(3L);
        admin.setName("Ops Lee");
        when(adminUserMapper.selectByIds(any())).thenReturn(List.of(admin));

        List<OrderEventDto> dtos = recorder.listAdmin(9L);

        assertThat(dtos).extracting(OrderEventDto::actorName).containsExactly("Ops Lee", null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<Long>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(adminUserMapper).selectByIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(3L);
    }
}
