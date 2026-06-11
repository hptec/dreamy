package com.dreamy.marketing.support;

import com.dreamy.marketing.domain.enums.ContentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.dreamy.marketing.domain.enums.ContentStatus.ARCHIVED;
import static com.dreamy.marketing.domain.enums.ContentStatus.DRAFT;
import static com.dreamy.marketing.domain.enums.ContentStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 三态内容状态机迁移 guard 单元测试（banner_lifecycle / blog_post_lifecycle 共用拓扑）。
 * L2 TRACE: TC-MKT-009（banner 迁移 guard）/ TC-MKT-010（blog 迁移 guard，published_at/slug 语义归服务层）/
 * bs-733~743。
 */
class ContentStateGuardsTest {

    @Test
    @DisplayName("TC-MKT-009 [P0]: 合法集 {draft→published, published→archived, archived→published}")
    void allowedTransitions() {
        assertThat(ContentStateGuards.transitionAllowed(DRAFT, PUBLISHED)).isTrue();
        assertThat(ContentStateGuards.transitionAllowed(PUBLISHED, ARCHIVED)).isTrue();
        assertThat(ContentStateGuards.transitionAllowed(ARCHIVED, PUBLISHED)).isTrue();
    }

    @Test
    @DisplayName("TC-MKT-009/010 [P0]: published→draft / draft→archived / archived→draft 拒绝（409703 由服务层映射）")
    void rejectedTransitions() {
        assertThat(ContentStateGuards.transitionAllowed(PUBLISHED, DRAFT)).isFalse();
        assertThat(ContentStateGuards.transitionAllowed(DRAFT, ARCHIVED)).isFalse();
        assertThat(ContentStateGuards.transitionAllowed(ARCHIVED, DRAFT)).isFalse();
    }

    @Test
    @DisplayName("guard 边界：同态/null 不经 guard（同态由调用方幂等短路）")
    void sameStateAndNull() {
        for (ContentStatus s : ContentStatus.values()) {
            assertThat(ContentStateGuards.transitionAllowed(s, s)).isFalse();
        }
        assertThat(ContentStateGuards.transitionAllowed(null, PUBLISHED)).isFalse();
        assertThat(ContentStateGuards.transitionAllowed(DRAFT, null)).isFalse();
    }
}
