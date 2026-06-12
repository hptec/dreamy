package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.coupon.service.AdminCouponService;
import com.dreamy.dto.AdminMarketingDtos.CouponDto;
import com.dreamy.dto.AdminMarketingDtos.CouponUpsert;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import huihao.page.Paginated;
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

/**
 * 后台优惠券控制器（E-MKT-13~16；RBAC `/promotions`；不缓存）。
 */
@RestController
public class AdminCouponController {

    private static final String PERMISSION = "/promotions";

    private final AdminCouponService adminCouponService;

    public AdminCouponController(AdminCouponService adminCouponService) {
        this.adminCouponService = adminCouponService;
    }

    /** E-MKT-13 listAdminCoupons */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/promotions/coupons")
    public ResponseEntity<R<Paginated<CouponDto>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(adminCouponService.page(page, pageSize, status, search)));
    }

    /** E-MKT-14 createAdminCoupon（TX-MKT-001） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/promotions/coupons")
    public ResponseEntity<R<CouponDto>> create(@RequestBody CouponUpsert req) {
        return ResponseEntity.status(201).body(R.ok(adminCouponService.create(req)));
    }

    /** E-MKT-15 updateAdminCoupon（TX-MKT-002） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/promotions/coupons/{id}")
    public ResponseEntity<R<CouponDto>> update(@PathVariable String id, @RequestBody CouponUpsert req) {
        return ResponseEntity.ok(R.ok(adminCouponService.update(parseId(id), req)));
    }

    /** E-MKT-16 deleteAdminCoupon（TX-MKT-003；409703 guard） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/promotions/coupons/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminCouponService.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** V-MKT-028：id 非法视同不存在 → 404702 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new MarketingException(MarketingErrorCode.COUPON_NOT_FOUND);
    }
}
