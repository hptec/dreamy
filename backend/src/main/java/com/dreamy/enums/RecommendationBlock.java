package com.dreamy.enums;

import lombok.Getter;

/**
 * 推荐位 block（决策 29 五规则）。
 * L2 TRACE: V-CAT-008 / E-CAT-03 STEP-CAT-02。
 */
public enum RecommendationBlock {
    NEW_ARRIVALS("new_arrivals"),
    BEST_SELLERS("best_sellers"),
    SHOP_BY_COLOR("shop_by_color"),
    YOU_MAY_ALSO_LIKE("you_may_also_like"),
    COMPLETE_THE_LOOK("complete_the_look");

    @Getter
    private final String key;

    RecommendationBlock(String key) {
        this.key = key;
    }

    public static RecommendationBlock of(String value) {
        for (RecommendationBlock b : values()) {
            if (b.key.equals(value)) {
                return b;
            }
        }
        return null;
    }

    /** you_may_also_like / complete_the_look 需要 product_id（V-CAT-009） */
    public boolean requiresProductId() {
        return this == YOU_MAY_ALSO_LIKE || this == COMPLETE_THE_LOOK;
    }

    /** shop_by_color 需要 collection_id（V-CAT-010） */
    public boolean requiresCollectionId() {
        return this == SHOP_BY_COLOR;
    }
}
