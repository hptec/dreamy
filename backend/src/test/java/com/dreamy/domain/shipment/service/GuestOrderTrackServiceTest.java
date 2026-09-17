package com.dreamy.domain.shipment.service;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderMapper;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.infra.grpc.CustomerInfoPort;
import java.util.Optional;
import com.dreamy.dto.TradingDtos.OrderTrackRequest;
import com.dreamy.dto.TradingDtos.OrderTrackView;
import com.dreamy.enums.OrderStatus;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.GuestTrackRateLimiter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 游客查单单测（§6.1：频控 429601、订单不存在/邮箱不匹配 404601、脱敏 receiver、shipments/events 装配）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestOrderTrackServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock CustomerInfoPort customerInfoPort;
    @Mock ShipmentQueryService shipmentQueryService;
    @Mock OrderEventRecorder orderEventRecorder;
    @Mock GuestTrackRateLimiter rateLimiter;

    GuestOrderTrackService service;
    Order order;

    @BeforeAll
    static void initMybatisPlusCache() {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new org.apache.ibatis.session.Configuration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @BeforeEach
    void setUp() {
        service = new GuestOrderTrackService(orderMapper, customerInfoPort, shipmentQueryService, orderEventRecorder, rateLimiter);
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        order = new Order();
        order.setId(1L);
        order.setOrderNo("DRM-20260907-0001");
        order.setCustomerId(7L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setCurrency("GBP");
        order.setTotalAmount(new BigDecimal("300.00"));
        order.setAddressSnapshot(Map.of("receiver", "Emma Watson", "country_code", "GB", "line", "secret"));
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(customerInfoPort.byId(7L)).thenReturn(Optional.of(new CustomerInfoPort.CustomerInfo(
                7L, "emma@example.com", true, "Emma", null, 1, 1, null, null, null, false)));
        when(shipmentQueryService.listByOrder(1L)).thenReturn(List.of());
        when(orderEventRecorder.listCustomerVisible(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("命中：邮箱大小写不敏感；receiver 脱敏；country_code 透出；不暴露地址明细")
    void trackSuccess() {
        OrderTrackView view = service.track(new OrderTrackRequest("drm-20260907-0001", "EMMA@example.com"), "1.1.1.1");
        assertThat(view.orderNo()).isEqualTo("DRM-20260907-0001");
        assertThat(view.status()).isEqualTo(3);
        assertThat(view.receiverMasked()).isNotEqualTo("Emma Watson").doesNotContain("Watson");
        assertThat(view.countryCode()).isEqualTo("GB");
        assertThat(view.totalAmount()).isEqualByComparingTo("300.00");
        verify(shipmentQueryService).listByOrder(1L);
        verify(orderEventRecorder).listCustomerVisible(1L);
    }

    @Test
    @DisplayName("邮箱不匹配 / 订单不存在 → 404601（防探测同码）")
    void notFoundOnMismatch() {
        assertThatThrownBy(() -> service.track(new OrderTrackRequest("DRM-20260907-0001", "other@example.com"), "ip"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.track(new OrderTrackRequest("DRM-X", "emma@example.com"), "ip"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("频控：第 11 次 → 429601，不查库；字段缺失 → 422601")
    void rateLimitedAndValidation() {
        when(rateLimiter.tryAcquire("9.9.9.9")).thenReturn(false);
        assertThatThrownBy(() -> service.track(new OrderTrackRequest("DRM-20260907-0001", "emma@example.com"), "9.9.9.9"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.TRACK_RATE_LIMITED));
        verify(orderMapper, never()).selectOne(any());
        assertThatThrownBy(() -> service.track(new OrderTrackRequest(null, "emma@example.com"), "1.1.1.1"))
                .isInstanceOfSatisfying(TradingException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(TradingErrorCode.FIELD_VALIDATION_FAILED));
        verify(customerInfoPort, never()).byId(org.mockito.ArgumentMatchers.anyLong());
    }
}
