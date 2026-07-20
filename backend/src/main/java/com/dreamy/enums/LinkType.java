package com.dreamy.enums;

import huihao.enums.annotation.Enumable;
import huihao.enums.typeable.Describable;
import huihao.enums.typeable.IntEnum;
import lombok.Getter;

/**
 * 导航项链接类型（navigation_items.link_type）。
 * custom 手写 URL；page 系统固定页（page_key）；其余为内部资源引用（ref_id）。
 * showroom 不作引用类型（用户私有空间），仅作 page_key 系统页。
 */
@Enumable
public enum LinkType implements IntEnum, Describable {
    CUSTOM(1, "自定义 URL"),
    PAGE(2, "系统页"),
    CATEGORY(3, "商品分类"),
    COLLECTION(4, "合集"),
    PRODUCT(5, "商品"),
    BLOG_POST(6, "博客文章"),
    REAL_WEDDING(7, "真实婚礼"),
    LOOKBOOK(8, "画册"),
    GUIDE(9, "指南");

    @Getter
    private final Integer key;

    @Getter
    private final String desc;

    LinkType(Integer key, String desc) {
        this.key = key;
        this.desc = desc;
    }

    public static LinkType of(Integer value) {
        for (LinkType t : values()) {
            if (t.key.equals(value)) {
                return t;
            }
        }
        return null;
    }

    /** 是否需要 ref_id 引用内部资源 */
    public boolean requiresRef() {
        return this == CATEGORY || this == COLLECTION || this == PRODUCT
                || this == BLOG_POST || this == REAL_WEDDING || this == LOOKBOOK || this == GUIDE;
    }
}
