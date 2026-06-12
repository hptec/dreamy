package com.dreamy.common.security;

import com.dreamy.security.MethodAwarePathMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * method-aware 公开路径白名单匹配单元测试（error-strategy L2 要求 2；
 * review-api-detail 0.1 条目形式 + catalog/marketing/trading/showroom 各域登记 pattern 全量回归）。
 */
class MethodAwarePathMatcherTest {

    /** 与 application.yml dreamy.security.store-public-paths 同步的全量白名单 */
    private final MethodAwarePathMatcher matcher = new MethodAwarePathMatcher(List.of(
            // identity 既有 5 条
            "/api/store/auth/otp/send",
            "/api/store/auth/otp/verify",
            "/api/store/auth/oidc/**",
            "/api/store/auth/refresh",
            "/api/store/auth/config",
            // catalog 3 条
            "/api/store/products/**",
            "/api/store/categories",
            "/api/store/tags",
            // marketing 4 条
            "/api/store/content/**",
            "/api/store/promotions/flash-sales",
            "/api/store/newsletter",
            "/api/store/contact",
            // review 2 条 method-aware
            "GET:/api/store/reviews",
            "GET:/api/store/questions",
            // trading 2 条
            "/api/store/exchange-rates",
            "POST:/api/store/payments/stripe/webhook",
            // showroom 1 条
            "POST:/api/store/showrooms/guest-session"));

    @Test
    @DisplayName("identity 既有 5 条公开路径配置化后保留（任意 method）")
    void identityLegacyPublicPathsPreserved() {
        assertThat(matcher.matches("POST", "/api/store/auth/otp/send")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/auth/otp/verify")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/auth/oidc/google")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/auth/oidc/apple")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/auth/refresh")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/auth/config")).isTrue();
        // 非公开 auth 子路径不放行
        assertThat(matcher.matches("POST", "/api/store/auth/logout")).isFalse();
    }

    @Test
    @DisplayName("catalog 3 条 pattern 覆盖 7 端点（含公开 POST 尺码推荐）")
    void catalogPatterns() {
        assertThat(matcher.matches("GET", "/api/store/products")).isTrue(); // E-CAT-01 列表（/** 含零段）
        assertThat(matcher.matches("GET", "/api/store/products/wedding-dress-1")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/products/search")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/products/size-recommendation")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/categories")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/tags")).isTrue();
    }

    @Test
    @DisplayName("marketing：flash-sales 精确路径，coupons/validate 不被误放行")
    void marketingFlashSalesExactNotCouponValidate() {
        assertThat(matcher.matches("GET", "/api/store/promotions/flash-sales")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/promotions/coupons/validate")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/content/banners")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/newsletter")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/contact")).isTrue();
    }

    @Test
    @DisplayName("review method-aware：GET 公开 / 同路径 POST 仍强制鉴权")
    void reviewMethodAware() {
        assertThat(matcher.matches("GET", "/api/store/reviews")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/reviews")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/questions")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/questions")).isFalse();
        // uploads/presign 不入白名单
        assertThat(matcher.matches("POST", "/api/store/uploads/presign")).isFalse();
    }

    @Test
    @DisplayName("trading：exchange-rates 匿名 + stripe webhook 仅 POST 豁免 JWT")
    void tradingEntries() {
        assertThat(matcher.matches("GET", "/api/store/exchange-rates")).isTrue();
        assertThat(matcher.matches("POST", "/api/store/payments/stripe/webhook")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/payments/stripe/webhook")).isFalse();
    }

    @Test
    @DisplayName("showroom：仅 guest-session POST 公开，列表/详情不过度放行（TC-REV-054 口径）")
    void showroomEntryNotOverbroad() {
        assertThat(matcher.matches("POST", "/api/store/showrooms/guest-session")).isTrue();
        assertThat(matcher.matches("GET", "/api/store/showrooms/guest-session")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/showrooms")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/showrooms/1")).isFalse();
        assertThat(matcher.matches("POST", "/api/store/showrooms")).isFalse();
    }

    @Test
    @DisplayName("method 前缀大小写不敏感；空/空白条目忽略")
    void methodCaseInsensitiveAndBlankIgnored() {
        MethodAwarePathMatcher m = new MethodAwarePathMatcher(List.of("get:/api/store/reviews", "", "  "));
        assertThat(m.matches("GET", "/api/store/reviews")).isTrue();
        assertThat(m.matches("POST", "/api/store/reviews")).isFalse();
    }

    @Test
    @DisplayName("个人资源路径（cart/orders/wishlist）一律不在白名单")
    void personalResourcesNeverPublic() {
        assertThat(matcher.matches("GET", "/api/store/cart")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/orders")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/wishlist")).isFalse();
        assertThat(matcher.matches("GET", "/api/store/account/profile")).isFalse();
    }
}
