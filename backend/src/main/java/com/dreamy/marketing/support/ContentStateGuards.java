package com.dreamy.marketing.support;

import com.dreamy.marketing.domain.enums.ContentStatus;

/**
 * 三态内容状态机迁移 guard（纯函数，banner_lifecycle / blog_post_lifecycle 共用迁移拓扑）。
 * 合法集：draft→published（publish）/ published→archived（take_offline|unpublish）/
 * archived→published（republish）；其余迁移非法 → 409703。
 * L2 TRACE: E-MKT-23/25 STEP（banner）、E-MKT-29/31 STEP（blog）/ TC-MKT-009/010 / bs-733~743。
 */
public final class ContentStateGuards {

    private ContentStateGuards() {
    }

    /** banner_lifecycle / blog_post_lifecycle 迁移合法性（同态由调用方幂等短路，不经本 guard） */
    public static boolean transitionAllowed(ContentStatus from, ContentStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case DRAFT -> to == ContentStatus.PUBLISHED;
            case PUBLISHED -> to == ContentStatus.ARCHIVED;
            case ARCHIVED -> to == ContentStatus.PUBLISHED;
        };
    }
}
