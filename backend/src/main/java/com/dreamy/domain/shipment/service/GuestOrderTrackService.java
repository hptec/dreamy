package com.dreamy.domain.shipment.service;

import com.dreamy.domain.order.entity.Order;
import com.dreamy.domain.order.repository.OrderMapper;
import com.dreamy.domain.order.service.OrderEventRecorder;
import com.dreamy.dto.TradingDtos.OrderTrackRequest;
import com.dreamy.dto.TradingDtos.OrderTrackView;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.GuestTrackRateLimiter;
import com.dreamy.infra.grpc.CustomerInfoPort;
import com.dreamy.support.NameMasker;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingParams;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * 游客查单（order-flow-complete §3.1 POST /api/store/orders/track {order_no,email}）：
 * IP 频控 10 次/小时 → 429601；订单号不存在或邮箱不匹配一律 404601（防探测）；返回脱敏 OrderTrackView。
 */
@Service
public class GuestOrderTrackService {

    private final OrderMapper orderMapper;
    private final CustomerInfoPort customerInfoPort;
    private final ShipmentQueryService shipmentQueryService;
    private final OrderEventRecorder orderEventRecorder;
    private final GuestTrackRateLimiter rateLimiter;

    public GuestOrderTrackService(OrderMapper orderMapper, CustomerInfoPort customerInfoPort,
                                  ShipmentQueryService shipmentQueryService, OrderEventRecorder orderEventRecorder,
                                  GuestTrackRateLimiter rateLimiter) {
        this.orderMapper = orderMapper;
        this.customerInfoPort = customerInfoPort;
        this.shipmentQueryService = shipmentQueryService;
        this.orderEventRecorder = orderEventRecorder;
        this.rateLimiter = rateLimiter;
    }

    public OrderTrackView track(OrderTrackRequest request, String ip) {
        if (!rateLimiter.tryAcquire(ip)) {
            throw new TradingException(TradingErrorCode.TRACK_RATE_LIMITED,
                    Map.of("limit_per_hour", GuestTrackRateLimiter.LIMIT_PER_HOUR));
        }
        TradingFieldErrors errors = new TradingFieldErrors();
        String orderNo = TradingParams.requireText(request == null ? null : request.orderNo(), 20, "order_no", errors);
        String email = TradingParams.requireText(request == null ? null : request.email(), 128, "email", errors);
        errors.throwIfAny();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo.toUpperCase(Locale.ROOT)));
        if (order == null) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        // 邮箱归属校验经 CustomerInfoPort(IdentityGate gRPC);用户不存在或邮箱不匹配一律 404601(防探测)
        String ownerEmail = customerInfoPort.byId(order.getCustomerId())
                .map(info -> info.email())
                .orElse(null);
        if (ownerEmail == null || !ownerEmail.equalsIgnoreCase(email)) {
            throw new TradingException(TradingErrorCode.ORDER_NOT_FOUND);
        }
        Map<String, Object> snapshot = order.getAddressSnapshot();
        String receiver = snapshot == null ? null : asString(snapshot.get("receiver"));
        String countryCode = snapshot == null ? null : asString(snapshot.get("country_code"));
        return new OrderTrackView(order.getOrderNo(), order.getStatus().getKey(),
                order.getProductionStage() == null ? null : order.getProductionStage().getKey(), order.getCurrency(),
                order.getTotalAmount(), order.getCreatedAt(), order.getPaidAt(), order.getShippedAt(),
                order.getDeliveredAt(), order.getCompletedAt(), order.getEstimatedDeliveryFrom(),
                order.getEstimatedDeliveryTo(), NameMasker.mask(receiver), countryCode,
                shipmentQueryService.listByOrder(order.getId()),
                orderEventRecorder.listCustomerVisible(order.getId()));
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
