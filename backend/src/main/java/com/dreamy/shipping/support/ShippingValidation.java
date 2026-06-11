package com.dreamy.shipping.support;

import com.dreamy.shipping.domain.enums.CarrierStatus;
import com.dreamy.shipping.dto.ShippingDtos.CarrierUpsert;
import com.dreamy.shipping.dto.ShippingDtos.ShippingRateUpsert;
import com.dreamy.shipping.domain.rate.service.ZoneNormalizer;
import com.dreamy.shipping.error.ShippingException;

import java.math.BigDecimal;

/**
 * shipping 域入参校验（V-SHP-002~011；首错即抛 422901 details={field}）。
 * L2 TRACE: shipping-api-detail E-SHP-02/03/05/07/08 入参验证段 + CV-SHP-001~004/007。
 */
public final class ShippingValidation {

    /** V-SHP-010 上限：≤99999999.99（DECIMAL(10,2)） */
    private static final BigDecimal FEE_MAX = new BigDecimal("99999999.99");

    private ShippingValidation() {
    }

    /** V-SHP-002 通用路径参数：id 必须为 int64 正整数；非法 → 422901 {field:"id"} */
    public static Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw ShippingException.fieldValidation("id");
    }

    /** 校验后的承运方载荷（trim 落库值；空白可选字段归 null——bs-260/261 容忍） */
    public record ValidCarrier(String name, String zones, String leadTime, CarrierStatus status) {
    }

    /** V-SHP-003~006（E-SHP-02）/ V-SHP-007（E-SHP-03 整单覆盖全字段重校验） */
    public static ValidCarrier validateCarrier(CarrierUpsert req) {
        if (req == null) {
            throw ShippingException.fieldValidation("_body");
        }
        // V-SHP-003 name 必填，trim 后非空且 <=64
        String name = req.name() == null ? null : req.name().trim();
        if (name == null || name.isEmpty() || name.length() > 64) {
            throw ShippingException.fieldValidation("name");
        }
        // V-SHP-004 status 必填 ∈ {enabled, disabled}
        CarrierStatus status = CarrierStatus.of(req.status());
        if (status == null) {
            throw ShippingException.fieldValidation("status");
        }
        // V-SHP-005 zones 可空；提供时 trim 后 <=255
        String zones = trimToNull(req.zones());
        if (zones != null && zones.length() > 255) {
            throw ShippingException.fieldValidation("zones");
        }
        // V-SHP-006 lead_time 可空；提供时 trim 后 <=64
        String leadTime = trimToNull(req.leadTime());
        if (leadTime != null && leadTime.length() > 64) {
            throw ShippingException.fieldValidation("lead_time");
        }
        return new ValidCarrier(name, zones, leadTime, status);
    }

    /** V-SHP-008 status 必填 ∈ {enabled, disabled}（E-SHP-05） */
    public static CarrierStatus validateStatus(String status) {
        CarrierStatus parsed = CarrierStatus.of(status);
        if (parsed == null) {
            throw ShippingException.fieldValidation("status");
        }
        return parsed;
    }

    /** 校验后的规则行载荷（zone 已按 DEC-SHP-1 规范化） */
    public record ValidRate(String zoneNorm, BigDecimal feeUnder, BigDecimal feeOver, BigDecimal threshold) {
    }

    /** V-SHP-009/010（E-SHP-07）/ V-SHP-011（E-SHP-08 同校验） */
    public static ValidRate validateRate(ShippingRateUpsert req) {
        if (req == null) {
            throw ShippingException.fieldValidation("_body");
        }
        // V-SHP-009 zone 必填，规范化后非空且 <=128
        String zoneNorm = ZoneNormalizer.normalize(req.zone());
        if (zoneNorm == null || zoneNorm.isEmpty() || zoneNorm.length() > 128) {
            throw ShippingException.fieldValidation("zone");
        }
        // V-SHP-010 三费用字段可空；提供时 >=0、<=99999999.99、小数位 <=2（CV-SHP-004）
        validateFee(req.feeUnder(), "fee_under");
        validateFee(req.feeOver(), "fee_over");
        validateFee(req.threshold(), "threshold");
        return new ValidRate(zoneNorm, req.feeUnder(), req.feeOver(), req.threshold());
    }

    private static void validateFee(BigDecimal value, String field) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(FEE_MAX) > 0
                || value.stripTrailingZeros().scale() > 2) {
            throw ShippingException.fieldValidation(field);
        }
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
