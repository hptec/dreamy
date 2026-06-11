package com.dreamy.catalog.dto;

/**
 * SKU DTO（请求/响应共用；已有 SKU 编辑回传 id+version 防并发 409508——V-CAT-038）。
 * L2 TRACE: openapi Sku。
 */
public record SkuDto(
        Long id,
        String skuCode,
        String color,
        String size,
        Integer stock,
        Long version
) {
}
