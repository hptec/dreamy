package com.dreamy.controller;

import com.dreamy.domain.shipment.service.GuestOrderTrackService;
import com.dreamy.dto.TradingDtos.OrderTrackRequest;
import com.dreamy.dto.TradingDtos.OrderTrackView;
import huihao.web.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游客查单控制器（order-flow-complete §3.1 POST /api/store/orders/track；公开白名单；IP 频控 10 次/小时 → 429601；
 * 邮箱不匹配 404601）。
 */
@RestController
public class StoreOrderTrackController {

    private final GuestOrderTrackService trackService;

    public StoreOrderTrackController(GuestOrderTrackService trackService) {
        this.trackService = trackService;
    }

    @PostMapping("/api/store/orders/track")
    public ResponseEntity<R<OrderTrackView>> track(@RequestBody OrderTrackRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(R.ok(trackService.track(request, clientIp(http))));
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return http.getRemoteAddr();
    }
}
