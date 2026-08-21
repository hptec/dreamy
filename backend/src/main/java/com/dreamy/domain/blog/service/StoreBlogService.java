package com.dreamy.domain.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.entity.BlogPostTranslation;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.dto.StoreMarketingDtos.StoreBlogPostCard;
import com.dreamy.dto.StoreMarketingDtos.StoreBlogPostDetail;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.support.MarketingPaginatedSupport;
import com.dreamy.support.Translations;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 消费端博客服务（E-MKT-02 listStoreBlogs / E-MKT-03 getStoreBlog；FLOW-P01/P03，s-758）。
 * L2 TRACE: STEP-MKT 各步 / CACHE-MKT-002/003 / MAP-MKT-003/004 / DEC-MKT-6 / TC-MKT-007/019/020。
 */
@Service
public class StoreBlogService {

    /** V-MKT-005 slug ^[a-z0-9-]+$ 且 ≤128（不匹配 → 404701 同口径防探测） */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{1,128}$");
    private static final Pattern MARKUP_PATTERN = Pattern.compile("<[^>]*>");

    private final BlogPostRepository blogPostRepository;
    private final MarketingCacheService cache;
    private final BlogViewsCounter viewsCounter;

    public StoreBlogService(BlogPostRepository blogPostRepository, MarketingCacheService cache,
                            BlogViewsCounter viewsCounter) {
        this.blogPostRepository = blogPostRepository;
        this.cache = cache;
        this.viewsCounter = viewsCounter;
    }

    /** E-MKT-02：published 列表（category 筛选 + 分页 + locale 回退 + JetCache 300s） */
    @SuppressWarnings("unchecked")
    public Paginated<StoreBlogPostCard> page(String category, int page, int pageSize, String locale) {
        // STEP-MKT-01 查 JetCache marketing:blogs:{category|all}:{page}:{page_size}:{locale}
        String cacheKey = (category == null ? "all" : category) + ":" + page + ":" + pageSize + ":" + locale;
        MarketingCacheService.Lookup lookup = cache.lookup(Family.BLOGS, cacheKey);
        Object cached = lookup.value();
        if (cached instanceof Paginated<?> hit) {
            return (Paginated<StoreBlogPostCard>) hit;
        }
        // STEP-MKT-02 分页查询（IDX-MKT-005）
        Page<BlogPost> result = blogPostRepository.pageStorePublished(category, page, pageSize);
        // STEP-MKT-03 卡片派生（excerpt/title 回退，决策 13）
        Map<Long, BlogPostTranslation> translations = translationsFor(
                result.getRecords().stream().map(BlogPost::getId).toList(), locale);
        Paginated<StoreBlogPostCard> paginated = MarketingPaginatedSupport.of(result,
                post -> toCard(post, translations.get(post.getId())));
        // STEP-MKT-04 写缓存
        cache.put(lookup, paginated);
        return paginated;
    }

    /** E-MKT-03：slug 详情（null 缓存穿透保护 + views 实时叠加 Redis 增量）。
     *  2026-08-21：缓存存 DB 快照 views，返回前叠加 Redis 未 flush 增量，保证消费端实时可见；
     *  计数职责移交独立端点 POST /blogs/{slug}/view（sessionStorage UV 口径），本方法不再 increment。 */
    public StoreBlogPostDetail getBySlug(String slug, String locale) {
        // V-MKT-005 slug pattern（非法与不存在同口径 404701 防探测）
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-01 查 JetCache marketing:blog:{slug}:{locale}（null 值 60s）
        String cacheKey = slug + ":" + locale;
        MarketingCacheService.Lookup lookup = cache.lookup(Family.BLOG, cacheKey);
        Object cached = lookup.value();
        if (cache.isNullMarker(cached)) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        if (cached instanceof StoreBlogPostDetail hit) {
            return withLiveViews(hit);
        }
        // STEP-MKT-02 点查（uk_blog_slug）；不存在/未发布 → null 缓存 60s → 404701
        BlogPost post = blogPostRepository.findBySlugPublished(slug);
        if (post == null) {
            cache.putNullMarker(lookup);
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-03 translation 覆盖（EN seo_title=title、seo_description=excerpt 派生，主表不建 seo 列）
        BlogPostTranslation t = translationsFor(List.of(post.getId()), locale).get(post.getId());
        StoreBlogPostDetail detail = toDetail(post, t);
        // STEP-MKT-04 写缓存（DB 快照 views）
        cache.put(lookup, detail);
        return withLiveViews(detail);
    }

    /** 2026-08-21：E-MKT-03B 阅读计数独立端点（sessionStorage UV 去重由前端保证，本端点信任调用）。
     *  slug 不存在/未发布 → 静默成功（防探测同 E-MKT-03 口径不暴露 404 差异）。 */
    public void recordView(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            return;
        }
        BlogPost post = blogPostRepository.findBySlugPublished(slug);
        if (post == null) {
            return;
        }
        viewsCounter.increment(post.getId());
    }

    /** 2026-08-21：实时 views = DB 快照 + Redis 未 flush 增量 */
    private StoreBlogPostDetail withLiveViews(StoreBlogPostDetail detail) {
        long delta = viewsCounter.getDelta(detail.id());
        if (delta == 0) {
            return detail;
        }
        int liveViews = (int) Math.min(Integer.MAX_VALUE, (long) detail.views() + delta);
        return new StoreBlogPostDetail(detail.id(), detail.title(), detail.slug(), detail.cover(),
                detail.category(), detail.author(), detail.excerpt(), detail.publishedAt(), liveViews,
                detail.content(), detail.seoTitle(), detail.seoDescription());
    }

    /** 2026-08-20 新增：预览模式按 id 直读（不校验 status，不走缓存——草稿预览必须看到最新数据） */
    public StoreBlogPostDetail getByIdForPreview(Long postId, String locale) {
        BlogPost post = blogPostRepository.findById(postId);
        if (post == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        BlogPostTranslation t = translationsFor(List.of(post.getId()), locale).get(post.getId());
        return toDetail(post, t);
    }

    /** 2026-08-20 新增：sitemap 数据（仅 published，返回 slug + updated_at 用于 lastModified） */
    public List<SitemapEntry> listPublishedForSitemap() {
        // 复用 pageStorePublished 拉全量（博客体量小,无需流式）
        Page<BlogPost> page = blogPostRepository.pageStorePublished(null, 1, 10_000);
        return page.getRecords().stream()
                .filter(p -> p.getSlug() != null && !p.getSlug().isBlank())
                .map(p -> new SitemapEntry(p.getSlug(), p.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /** 2026-08-20: sitemap 条目（slug + lastModified） */
    public record SitemapEntry(String slug, java.time.LocalDateTime updatedAt) {
    }

    /**
     * EN excerpt 派生：content strip 标记后截断 200 字符（MAP-MKT-003 / TC-MKT-007）。
     * 2026-08-20 改造: 主表已有 excerpt 列,本方法仅作为 fallback 兜底,优先级链:
     *   译文.excerpt → 主表.excerpt → 译文.body strip 截 200 → 主表.content strip 截 200
     */
    public static String deriveExcerpt(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String stripped = MARKUP_PATTERN.matcher(content).replaceAll("").replaceAll("\\s+", " ").trim();
        return stripped.length() <= 200 ? stripped : stripped.substring(0, 200);
    }

    /** 2026-08-20: excerpt fallback 链（译文.excerpt → 主表.excerpt → 译文.body strip → 主表.content strip） */
    private String resolveExcerpt(BlogPost post, BlogPostTranslation t) {
        if (t != null && t.getExcerpt() != null && !t.getExcerpt().isBlank()) {
            return t.getExcerpt();
        }
        if (post.getExcerpt() != null && !post.getExcerpt().isBlank()) {
            return post.getExcerpt();
        }
        if (t != null && t.getBody() != null && !t.getBody().isBlank()) {
            return deriveExcerpt(t.getBody());
        }
        return deriveExcerpt(post.getContent());
    }

    /** 2026-08-20: seoTitle fallback 链（译文.seoTitle → 主表.seoTitle → 译文.title → 主表.title） */
    private String resolveSeoTitle(BlogPost post, BlogPostTranslation t, String displayTitle) {
        if (t != null && t.getSeoTitle() != null && !t.getSeoTitle().isBlank()) {
            return t.getSeoTitle();
        }
        if (post.getSeoTitle() != null && !post.getSeoTitle().isBlank()) {
            return post.getSeoTitle();
        }
        return displayTitle;
    }

    /** 2026-08-20: seoDescription fallback 链（译文.seoDescription → 主表.seoDescription → excerpt） */
    private String resolveSeoDescription(BlogPost post, BlogPostTranslation t, String displayExcerpt) {
        if (t != null && t.getSeoDescription() != null && !t.getSeoDescription().isBlank()) {
            return t.getSeoDescription();
        }
        if (post.getSeoDescription() != null && !post.getSeoDescription().isBlank()) {
            return post.getSeoDescription();
        }
        return displayExcerpt;
    }

    private StoreBlogPostCard toCard(BlogPost post, BlogPostTranslation t) {
        String excerpt = resolveExcerpt(post, t);
        return new StoreBlogPostCard(post.getId(),
                Translations.coalesce(t == null ? null : t.getTitle(), post.getTitle()),
                post.getSlug(), post.getCover(), post.getCategory(), post.getAuthor(),
                excerpt,
                post.getPublishedAt(), post.getViews());
    }

    private StoreBlogPostDetail toDetail(BlogPost post, BlogPostTranslation t) {
        String title = Translations.coalesce(t == null ? null : t.getTitle(), post.getTitle());
        String excerpt = resolveExcerpt(post, t);
        return new StoreBlogPostDetail(post.getId(), title, post.getSlug(), post.getCover(), post.getCategory(),
                post.getAuthor(), excerpt, post.getPublishedAt(), post.getViews(),
                Translations.coalesce(t == null ? null : t.getBody(), post.getContent()),
                resolveSeoTitle(post, t, title),
                resolveSeoDescription(post, t, excerpt));
    }

    private Map<Long, BlogPostTranslation> translationsFor(List<Long> ids, String locale) {
        Map<Long, BlogPostTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || ids.isEmpty()) {
            return map;
        }
        for (BlogPostTranslation row : blogPostRepository.listTranslationsByPostIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getBlogPostId(), row);
            }
        }
        return map;
    }
}
