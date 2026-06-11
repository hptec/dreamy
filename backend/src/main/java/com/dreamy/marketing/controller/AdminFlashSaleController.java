package com.dreamy.marketing.controller;

import com.dreamy.identity.aspect.RequirePermission;
import com.dreamy.marketing.domain.flashsale.service.AdminFlashSaleService;
import com.dreamy.marketing.dto.AdminMarketingDtos.FlashSaleDto;
import com.dreamy.marketing.dto.AdminMarketingDtos.FlashSaleUpsert;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台闪购控制器（E-MKT-17~20；RBAC `/promotions`；不缓存）。
 */
@RestController
public class AdminFlashSaleController {

    private static final String PERMISSION = "/promotions";

    private final AdminFlashSaleService adminFlashSaleService;

    public AdminFlashSaleController(AdminFlashSaleService adminFlashSaleService) {
        this.adminFlashSaleService = adminFlashSaleService;
    }

    /** E-MKT-17 listAdminFlashSales */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/promotions/flash-sales")
    public ResponseEntity<R<Map<String, List<FlashSaleDto>>>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(R.ok(Map.of("items", adminFlashSaleService.list(status))));
    }

    /** E-MKT-18 createAdminFlashSale（TX-MKT-004） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/promotions/flash-sales")
    public ResponseEntity<R<FlashSaleDto>> create(@RequestBody FlashSaleUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminFlashSaleService.create(req)));
    }

    /** E-MKT-19 updateAdminFlashSale（TX-MKT-005；ended 409703） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/promotions/flash-sales/{id}")
    public ResponseEntity<R<FlashSaleDto>> update(@PathVariable String id, @RequestBody FlashSaleUpsert req) {
        return ResponseEntity.ok(R.ok(adminFlashSaleService.update(parseId(id), req)));
    }

    /** E-MKT-20 deleteAdminFlashSale（TX-MKT-006；仅 draft 可删） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/promotions/flash-sales/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminFlashSaleService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** V-MKT-037：id 非法视同不存在 → 404703 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new MarketingException(MarketingErrorCode.FLASH_SALE_NOT_FOUND);
    }
}
