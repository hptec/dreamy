package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.shippingrate.service.ShippingRateAdminService;
import com.dreamy.dto.ShippingDtos.ShippingRateDto;
import com.dreamy.dto.ShippingDtos.ShippingRateUpsert;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台运费规则控制器（E-SHP-06~09；V-SHP-001 鉴权前置：AdminJwtFilter(40100) + RBAC `/shipping`(40300)；不缓存）。
 * order-flow-complete E：shipping_rate 已被 shipping_option 取代（报价改读 /api/admin/shipping/options）。
 * 本控制器保留 GET 只读兼容（旧表数据，供回滚/对照）；写端点统一返回 410901 引导迁移。
 */
@RestController
public class AdminShippingRateController {

    private static final String PERMISSION = "/shipping";

    private final ShippingRateAdminService rateAdminService;

    public AdminShippingRateController(ShippingRateAdminService rateAdminService) {
        this.rateAdminService = rateAdminService;
    }

    /** E-SHP-06 listAdminShippingRates */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/shipping/rates")
    public ResponseEntity<R<Map<String, List<ShippingRateDto>>>> list() {
        return ResponseEntity.ok(R.ok(Map.of("items", rateAdminService.list())));
    }

    /** E-SHP-07（已废弃）→ 410901 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/shipping/rates")
    public ResponseEntity<R<ShippingRateDto>> create(@RequestBody ShippingRateUpsert req) {
        throw new com.dreamy.error.ShippingException(com.dreamy.error.ShippingErrorCode.SHIPPING_RATE_DEPRECATED);
    }

    /** E-SHP-08（已废弃）→ 410901 */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/shipping/rates/{id}")
    public ResponseEntity<R<ShippingRateDto>> update(@PathVariable String id, @RequestBody ShippingRateUpsert req) {
        throw new com.dreamy.error.ShippingException(com.dreamy.error.ShippingErrorCode.SHIPPING_RATE_DEPRECATED);
    }

    /** E-SHP-09（已废弃）→ 410901 */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/shipping/rates/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        throw new com.dreamy.error.ShippingException(com.dreamy.error.ShippingErrorCode.SHIPPING_RATE_DEPRECATED);
    }
}
