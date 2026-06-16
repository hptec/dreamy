package com.dreamy.domain.product.entity.vo;

/**
 * 护理标签项（内联存储于 product.care JSON 列）。
 * symbol: 行业通用护理 Unicode 符号（如 "🫧"）；
 * label: 展示文本（如 "Hand wash cold"）。
 * 不再使用字典表 + IntEnum，直接内联字符串。
 */
public record CareItem(
        String symbol,
        String label
) {
}
