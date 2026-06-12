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
        Object cached = cache.get(Family.BLOGS, cacheKey);
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
        cache.put(Family.BLOGS, cacheKey, paginated);
        return paginated;
    }

    /** E-MKT-03：slug 详情（null 缓存穿透保护 + views 异步累加） */
    public StoreBlogPostDetail getBySlug(String slug, String locale) {
        // V-MKT-005 slug pattern（非法与不存在同口径 404701 防探测）
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-01 查 JetCache marketing:blog:{slug}:{locale}（null 值 60s）
        String cacheKey = slug + ":" + locale;
        Object cached = cache.get(Family.BLOG, cacheKey);
        if (cache.isNullMarker(cached)) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        if (cached instanceof StoreBlogPostDetail hit) {
            // STEP-MKT-05（缓存命中亦计数？否——CDN/JetCache 命中不计数，DEC-MKT-6 近似语义）
            return hit;
        }
        // STEP-MKT-02 点查（uk_blog_slug）；不存在/未发布 → null 缓存 60s → 404701
        BlogPost post = blogPostRepository.findBySlugPublished(slug);
        if (post == null) {
            cache.putNullMarker(Family.BLOG, cacheKey);
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-03 translation 覆盖（EN seo_title=title、seo_description=excerpt 派生，主表不建 seo 列）
        BlogPostTranslation t = translationsFor(List.of(post.getId()), locale).get(post.getId());
        StoreBlogPostDetail detail = toDetail(post, t);
        // STEP-MKT-04 写缓存
        cache.put(Family.BLOG, cacheKey, detail);
        // STEP-MKT-05 views 异步累加（源站命中才计数，fire-and-forget——DEC-MKT-6）
        viewsCounter.increment(post.getId());
        return detail;
    }

    /**
     * EN excerpt 派生：content strip 标记后截断 200 字符（MAP-MKT-003 / TC-MKT-007）。
     */
    public static String deriveExcerpt(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String stripped = MARKUP_PATTERN.matcher(content).replaceAll("").replaceAll("\\s+", " ").trim();
        return stripped.length() <= 200 ? stripped : stripped.substring(0, 200);
    }

    private StoreBlogPostCard toCard(BlogPost post, BlogPostTranslation t) {
        String enExcerpt = deriveExcerpt(post.getContent());
        return new StoreBlogPostCard(post.getId(),
                Translations.coalesce(t == null ? null : t.getTitle(), post.getTitle()),
                post.getSlug(), post.getCover(), post.getCategory(), post.getAuthor(),
                Translations.coalesce(t == null ? null : t.getExcerpt(), enExcerpt),
                post.getPublishedAt(), post.getViews());
    }

    private StoreBlogPostDetail toDetail(BlogPost post, BlogPostTranslation t) {
        String enExcerpt = deriveExcerpt(post.getContent());
        String title = Translations.coalesce(t == null ? null : t.getTitle(), post.getTitle());
        String excerpt = Translations.coalesce(t == null ? null : t.getExcerpt(), enExcerpt);
        return new StoreBlogPostDetail(post.getId(), title, post.getSlug(), post.getCover(), post.getCategory(),
                post.getAuthor(), excerpt, post.getPublishedAt(), post.getViews(),
                Translations.coalesce(t == null ? null : t.getBody(), post.getContent()),
                Translations.coalesce(t == null ? null : t.getSeoTitle(), title),
                Translations.coalesce(t == null ? null : t.getSeoDescription(), excerpt));
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
