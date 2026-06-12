package com.dreamy.catalog.dto;

import java.util.List;

/**
 * 消费端动态属性 DTO 集（PDP attributes / PLP filters 维度）。
 * value=EN 规范值（筛选参数/EAV 落库口径），label=locale 译文（attribute_def_translation.options 同序映射）。
 */
public final class StoreAttributeDtos {

    private StoreAttributeDtos() {
    }

    /** 单个可选值/已选值（value 规范值 + label 译文） */
    public record OptionDto(String value, String label) {
    }

    /** PDP 商品属性行（按生效属性集顺序，hidden 已排除） */
    public record StoreAttributeDto(String key, String label, String type, List<OptionDto> values) {
    }

    /** PLP 筛选维度（非 hidden 的 select/multiselect） */
    public record StoreFilterDimDto(String key, String label, String type, List<OptionDto> options) {
    }
}
