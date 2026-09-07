package com.dreamy.enums;

import lombok.Getter;

/**
 * 系统固定页 page_key（navigation_items.page_key，LinkType=PAGE 时非空）。
 * key 即消费端路由路径（不含 locale 前缀）。
 */
@Getter
public enum NavPageKey {
    HOME("home", "/"),
    PRODUCTS("products", "/products"),
    WEDDING_DRESSES("wedding-dresses", "/wedding-dresses"),
    SPECIAL_OCCASION("special-occasion", "/special-occasion"),
    ACCESSORIES("accessories", "/accessories"),
    OUTDOOR_WEDDINGS("outdoor-weddings", "/outdoor-weddings"),
    REAL_WEDDINGS("real-weddings", "/real-weddings"),
    INSPIRATION("inspiration", "/inspiration"),
    BLOG("blog", "/blog"),
    WEDDING_GUIDES("wedding-guides", "/wedding-guides"),
    SHOWROOM("showroom", "/showroom"),
    ABOUT("about", "/about"),
    CONTACT("contact", "/contact"),
    FAQ("faq", "/faq"),
    SEARCH("search", "/search"),
    CART("cart", "/cart"),
    ACCOUNT_LOGIN("account-login", "/account/login"),
    ACCOUNT_ORDERS("account-orders", "/account/orders"),
    ACCOUNT_WISHLIST("account-wishlist", "/account/wishlist"),
    TRACK_ORDER("track-order", "/track-order");

    private final String key;
    private final String path;

    NavPageKey(String key, String path) {
        this.key = key;
        this.path = path;
    }

    public static NavPageKey of(String key) {
        if (key == null) {
            return null;
        }
        for (NavPageKey p : values()) {
            if (p.key.equals(key)) {
                return p;
            }
        }
        return null;
    }
}
