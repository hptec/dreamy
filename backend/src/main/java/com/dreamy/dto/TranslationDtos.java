package com.dreamy.dto;

import java.util.List;

/**
 * 翻译行 DTO 集（locale ∈ {es,fr}，CV-CAT-009；admin 三语 tab 原样不回退合并——MAP-CAT-004/006）。
 */
public final class TranslationDtos {

    private TranslationDtos() {
    }

    /** openapi ProductTranslation（决策12 增量 designerNote 三语独立） */
    public record ProductTranslationDto(
            String locale,
            String name,
            String description,
            String designerNote,
            List<String> sellingPoints,
            String seoTitle,
            String seoDescription
    ) {
    }

    /** openapi CategoryTranslation */
    public record CategoryTranslationDto(String locale, String name) {
    }

    /** openapi AttributeDefTranslation（options 与主表等长——V-CAT-058） */
    public record AttributeDefTranslationDto(String locale, String label, List<String> options) {
    }

    /** openapi TagTranslation */
    public record TagTranslationDto(String locale, String label) {
    }

    /** openapi TagDimensionTranslation */
    public record TagDimensionTranslationDto(String locale, String name) {
    }
}
