package com.dreamy.marketing.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * type→revalidate 路径映射表单元测试（EVT-MKT-002 / 决策 27 ×3 locale 展开）。
 * L2 TRACE: TC-MKT-026 单测面（路径映射 ×3 locale + old_slug 一并失效）。
 */
class InvalidatePathMapperTest {

    @Test
    @DisplayName("TC-MKT-026 [P0]: blog_changed → /blog + /blog/{slug}（old_slug 一并）×3 locale")
    void blogChangedPaths() {
        List<String> paths = InvalidatePathMapper.localizedPaths("blog_changed",
                Map.of("slug", "fabric-guide", "old_slug", "old-fabric"));
        assertThat(paths).containsExactlyInAnyOrder(
                "/blog", "/blog/fabric-guide", "/blog/old-fabric",
                "/es/blog", "/es/blog/fabric-guide", "/es/blog/old-fabric",
                "/fr/blog", "/fr/blog/fabric-guide", "/fr/blog/old-fabric");
    }

    @Test
    @DisplayName("TC-MKT-026 [P0]: banner/flash → 首页 ×3 locale（`/` 展开为 /es、/fr 非 /es/）")
    void homePaths() {
        assertThat(InvalidatePathMapper.localizedPaths("banner_changed", Map.of()))
                .containsExactly("/", "/es", "/fr");
        assertThat(InvalidatePathMapper.localizedPaths("flash_sale_changed", Map.of()))
                .containsExactly("/", "/es", "/fr");
    }

    @Test
    @DisplayName("EVT-MKT-002 [P0]: wedding_changed → /real-weddings + /real-weddings/{id} + / ×3 locale")
    void weddingPaths() {
        List<String> paths = InvalidatePathMapper.localizedPaths("wedding_changed", Map.of("id", 7L));
        assertThat(paths).hasSize(9)
                .contains("/real-weddings", "/real-weddings/7", "/",
                        "/es/real-weddings/7", "/fr/real-weddings", "/fr");
    }

    @Test
    @DisplayName("EVT-MKT-002 [P0]: lookbook/guide 单页；catalog product_updated 含商品页+四聚合页+首页")
    void otherTypePaths() {
        assertThat(InvalidatePathMapper.localizedPaths("lookbook_changed", Map.of("id", 1L)))
                .containsExactly("/inspiration", "/es/inspiration", "/fr/inspiration");
        assertThat(InvalidatePathMapper.localizedPaths("guide_changed", Map.of()))
                .containsExactly("/wedding-guides", "/es/wedding-guides", "/fr/wedding-guides");
        List<String> product = InvalidatePathMapper.localizedPaths("product_updated", Map.of("slug", "aurelia"));
        assertThat(product).contains("/product/aurelia", "/wedding-dresses", "/special-occasion",
                "/accessories", "/outdoor-weddings", "/", "/es/product/aurelia", "/fr/outdoor-weddings");
        assertThat(product).hasSize(18);
        assertThat(InvalidatePathMapper.localizedPaths("category_changed", Map.of())).hasSize(15);
    }

    @Test
    @DisplayName("EVT-MKT-002 前向兼容：未知 type → 空集（消费者空操作不死信）")
    void unknownType() {
        assertThat(InvalidatePathMapper.localizedPaths("something_new", Map.of())).isEmpty();
        assertThat(InvalidatePathMapper.localizedPaths(null, Map.of())).isEmpty();
    }
}
