package com.dreamy.dto;

import java.math.BigDecimal;

/**
 * shipping 域 DTO 集（MAP-SHP-001/002；JSON 全局 SNAKE_CASE：leadTime→lead_time、feeUnder→fee_under）。
 * 审计列 created_at/updated_at 不出 DTO（契约 Schema 无此字段）；DECIMAL NULL → JSON null 原样透出（DEC-SHP-3）。
 */
public final class ShippingDtos {

    private ShippingDtos() {
    }

    /** MAP-SHP-001 Carrier → CarrierDto（契约 Carrier Schema） */
    public record CarrierDto(Long id, String name, String zones, String leadTime, Integer status) {
    }

    /** 契约 CarrierUpsert（E-SHP-02/03 请求体；status 字符串入参由 V-SHP-004 校验枚举） */
    public record CarrierUpsert(String name, String zones, String leadTime, Integer status) {
    }

    /** MAP-SHP-002 ShippingRate → ShippingRateDto（契约 ShippingRate Schema） */
    public record ShippingRateDto(Long id, String zone, BigDecimal feeUnder, BigDecimal feeOver,
                                  BigDecimal threshold) {
    }

    /** 契约 ShippingRateUpsert（E-SHP-07/08 请求体） */
    public record ShippingRateUpsert(String zone, BigDecimal feeUnder, BigDecimal feeOver, BigDecimal threshold) {
    }

    /** E-SHP-05 请求体 { status } */
    public record StatusPatch(Integer status) {
    }
}
