package com.dreamy.domain.product.entity.vo;

import java.math.BigDecimal;

/**
 * 面料成分行（内联存储于 product.fabric_compositions JSON 列）。
 * layer: 1=Shell 2=Lining 3=Overlay 4=Trim（前端分组标签用）；
 * material: 材质名称字符串（如 "Polyester"，不再用枚举/字典外键）；
 * percentage: 占比 0..100。
 */
public record FabricComposition(
        Integer layer,
        String material,
        BigDecimal percentage
) {
}
