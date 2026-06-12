package com.dreamy.mq;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * type → revalidate 路径映射表（EVT-MKT-002 / data-flow FLOW-P03 落点，纯函数）。
 * 每条路径 ×3 locale：EN 无前缀、`/es`、`/fr`（决策 27）；purge URL 同列表。
 * 覆盖本域 6 type + catalog 生产的 product_xxx / category_changed / tag_changed
 * + review 生产的 review_changed / question_changed（多域生产者共用 q.invalidate）。
 * trading exchange_rates_updated（EVT-TRD-005 自带 purge_paths、仅 CDN purge）由消费者特判，不经本表。
 * L2 TRACE: marketing-data-detail §9.2 路径映射表 / TC-MKT-026。
 */
public final class InvalidatePathMapper {

    private static final List<String> LOCALE_PREFIXES = List.of("", "/es", "/fr");

    private static final List<String> CATALOG_AGGREGATE_PAGES = List.of(
            "/wedding-dresses", "/special-occasion", "/accessories", "/outdoor-weddings");

    private InvalidatePathMapper() {
    }

    /** 基础路径集（locale 展开前）；未知 type → 空集（向前兼容：新增 type 不致死信） */
    public static List<String> basePaths(String type, Map<String, Object> payload) {
        if (type == null) {
            return List.of();
        }
        Object slugRaw = payload == null ? null : payload.get("slug");
        Object oldSlugRaw = payload == null ? null : payload.get("old_slug");
        Object idRaw = payload == null ? null : payload.get("id");
        String slug = slugRaw == null ? null : String.valueOf(slugRaw);
        String oldSlug = oldSlugRaw == null ? null : String.valueOf(oldSlugRaw);
        Set<String> paths = new LinkedHashSet<>();
        switch (type) {
            case "banner_changed", "flash_sale_changed" -> paths.add("/");
            case "blog_changed" -> {
                paths.add("/blog");
                if (slug != null) {
                    paths.add("/blog/" + slug);
                }
                if (oldSlug != null) {
                    paths.add("/blog/" + oldSlug);
                }
            }
            case "wedding_changed" -> {
                paths.add("/real-weddings");
                if (idRaw != null) {
                    paths.add("/real-weddings/" + idRaw);
                }
                paths.add("/");
            }
            case "lookbook_changed" -> paths.add("/inspiration");
            case "guide_changed" -> paths.add("/wedding-guides");
            case "product_created", "product_updated", "product_status_changed", "product_flags_changed" -> {
                if (slug != null) {
                    paths.add("/product/" + slug);
                }
                if (oldSlug != null) {
                    paths.add("/product/" + oldSlug);
                }
                paths.addAll(CATALOG_AGGREGATE_PAGES);
                paths.add("/");
            }
            case "category_changed", "tag_changed" -> {
                paths.addAll(CATALOG_AGGREGATE_PAGES);
                paths.add("/");
            }
            // review 域生产（EVT-REV-002：type=review_changed|question_changed → PDP 评价/Q&A 区刷新，
            // review-data-detail §8.1：revalidatePath('/product/{slug}') ×3 locale + purge）
            case "review_changed", "question_changed" -> {
                if (slug != null) {
                    paths.add("/product/" + slug);
                }
            }
            default -> {
                return List.of();
            }
        }
        return new ArrayList<>(paths);
    }

    /** locale 展开：EN 无前缀 + /es + /fr（决策 27；`/` 展开为 `/es`、`/fr` 而非 `/es/`） */
    public static List<String> localizedPaths(String type, Map<String, Object> payload) {
        List<String> base = basePaths(type, payload);
        List<String> result = new ArrayList<>(base.size() * LOCALE_PREFIXES.size());
        for (String prefix : LOCALE_PREFIXES) {
            for (String path : base) {
                if (prefix.isEmpty()) {
                    result.add(path);
                } else {
                    result.add("/".equals(path) ? prefix : prefix + path);
                }
            }
        }
        return result;
    }
}
