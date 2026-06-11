package com.dreamy.catalog.dto;

/**
 * 商品媒体 DTO（请求/响应共用；kind 请求侧为字符串，服务端校验 V-CAT-031）。
 * L2 TRACE: openapi ProductImage。
 */
public record ProductImageDto(
        Long id,
        String url,
        String kind,
        String colorName,
        Integer sort
) {
}
