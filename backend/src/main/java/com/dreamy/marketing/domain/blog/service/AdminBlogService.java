package com.dreamy.marketing.domain.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.marketing.domain.blog.entity.BlogPost;
import com.dreamy.marketing.domain.blog.entity.BlogPostTranslation;
import com.dreamy.marketing.domain.blog.repository.BlogPostRepository;
import com.dreamy.marketing.domain.enums.ContentStatus;
import com.dreamy.marketing.dto.AdminMarketingDtos.BlogPostDto;
import com.dreamy.marketing.dto.AdminMarketingDtos.BlogPostUpsert;
import com.dreamy.marketing.dto.MarketingTranslationDtos.BlogPostTranslationDto;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.infra.MarketingAfterCommitRunner;
import com.dreamy.marketing.infra.MarketingAuditRecorder;
import com.dreamy.marketing.infra.MarketingCacheService;
import com.dreamy.marketing.infra.MarketingCacheService.Family;
import com.dreamy.marketing.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.marketing.support.ContentStateGuards;
import com.dreamy.marketing.support.FieldErrors;
import com.dreamy.marketing.support.MarketingParams;
import com.dreamy.marketing.support.PaginatedSupport;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 后台博客服务（E-MKT-26~31；TX-MKT-011~014；TASK-033 blog_post_lifecycle）。
 * 已发布保存即触发失效链（s-758）：`marketing:blogs:*` + `marketing:blog:{slug}:*`（新旧 slug）→ MQ blog_changed。
 * L2 TRACE: V-MKT-048~057 / CV-MKT-012 / RM-MKT-020~032 / CACHE-MKT-002/003。
 */
@Service
public class AdminBlogService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{1,128}$");
    private static final List<String> STATUS_FILTER = List.of("all", "draft", "published", "archived");

    private final BlogPostRepository blogPostRepository;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final MarketingAfterCommitRunner afterCommit;
    private final MarketingContentInvalidatedPublisher publisher;

    public AdminBlogService(BlogPostRepository blogPostRepository, MarketingCacheService cache,
                            MarketingAuditRecorder audit, MarketingAfterCommitRunner afterCommit,
                            MarketingContentInvalidatedPublisher publisher) {
        this.blogPostRepository = blogPostRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.publisher = publisher;
    }

    /** E-MKT-26：分页列表（status/search 筛选） */
    public Paginated<BlogPostDto> page(Integer page, Integer pageSize, String status, String search) {
        FieldErrors errors = new FieldErrors();
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        // V-MKT-048 status ∈ {all, draft, published, archived} 缺省 all
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!STATUS_FILTER.contains(statusFilter)) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-049 search ≤80（title LIKE）
        String parsedSearch = MarketingParams.checkMaxLength(search, 80, "search", errors);
        errors.throwIfAny();

        ContentStatus statusEnum = "all".equals(statusFilter) ? null : ContentStatus.of(statusFilter);
        Page<BlogPost> result = blogPostRepository.pageAdmin(statusEnum, parsedSearch, parsedPage, parsedPageSize);
        Map<Long, List<BlogPostTranslationDto>> translations = translationsByPost(
                result.getRecords().stream().map(BlogPost::getId).toList());
        return PaginatedSupport.of(result, p -> toDto(p, translations.getOrDefault(p.getId(), List.of())));
    }

    /** E-MKT-28：编辑详情（translations 三语 tab 全量原样，admin 不回退合并） */
    public BlogPostDto get(Long id) {
        BlogPost post = blogPostRepository.findById(id);
        if (post == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Map<Long, List<BlogPostTranslationDto>> translations = translationsByPost(List.of(id));
        return toDto(post, translations.getOrDefault(id, List.of()));
    }

    /** E-MKT-27：创建（TX-MKT-011；blog_post_lifecycle 初态 draft/published） */
    @Transactional
    public BlogPostDto create(BlogPostUpsert req) {
        Normalized n = validateUpsert(req, true);
        // STEP-MKT-01 slug 非空查重（uk_blog_slug 兜底）→ 409702
        if (n.slug() != null && blogPostRepository.existsBySlugExcept(n.slug(), null)) {
            throw new MarketingException(MarketingErrorCode.SLUG_EXISTS);
        }
        // STEP-MKT-02 INSERT（published 记 published_at=now；views=0 初始化）+ translation 批插
        BlogPost post = new BlogPost();
        applyUpsert(post, n, req);
        post.setViews(0);
        if (n.status() == ContentStatus.PUBLISHED) {
            post.setPublishedAt(LocalDateTime.now());
        }
        blogPostRepository.insert(post);
        blogPostRepository.replaceTranslations(post.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-03 审计
        audit.record("创建文章", n.title(), null);
        // STEP-MKT-04 提交后（published）失效 + MQ
        if (n.status() == ContentStatus.PUBLISHED) {
            invalidateAfterCommit(n.slug(), null);
        }
        return toDto(blogPostRepository.findById(post.getId()), nonNull(req.translations()));
    }

    /** E-MKT-29：编辑（TX-MKT-012；已发布保存即触发失效链 s-758） */
    @Transactional
    public BlogPostDto update(Long id, BlogPostUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        BlogPost existing = blogPostRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Normalized n = validateUpsert(req, false);
        // STEP-MKT-02 status 变更迁移 guard（blog_post_lifecycle）→ 409703
        boolean statusChanged = existing.getStatus() != n.status();
        if (statusChanged && !ContentStateGuards.transitionAllowed(existing.getStatus(), n.status())) {
            throw MarketingException.stateInvalid("illegal_transition");
        }
        // draft→published 发布不变量：slug 必填（CV-MKT-012）
        if (n.status() == ContentStatus.PUBLISHED && n.slug() == null) {
            throw MarketingException.fieldValidation("slug", "required_for_publish");
        }
        // STEP-MKT-03 slug 变更查重（排除自身）→ 409702
        if (n.slug() != null && !n.slug().equals(existing.getSlug())
                && blogPostRepository.existsBySlugExcept(n.slug(), id)) {
            throw new MarketingException(MarketingErrorCode.SLUG_EXISTS);
        }
        String oldSlug = existing.getSlug();
        ContentStatus oldStatus = existing.getStatus();
        // STEP-MKT-04 UPDATE（SET 不含 views——V-MKT-056；published_at 仅 draft→published 写入，编辑不刷新）
        applyUpsert(existing, n, req);
        if (statusChanged && oldStatus == ContentStatus.DRAFT && n.status() == ContentStatus.PUBLISHED) {
            existing.setPublishedAt(LocalDateTime.now());
        }
        blogPostRepository.update(existing);
        blogPostRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-05 审计
        audit.record("编辑文章", n.title(), null);
        // STEP-MKT-06 提交后（DB 或目标 status=published，或 published→archived 下线）失效（新旧 slug 都失效）+ MQ
        if (oldStatus == ContentStatus.PUBLISHED || n.status() == ContentStatus.PUBLISHED) {
            invalidateAfterCommit(n.slug(), oldSlug);
        }
        return toDto(blogPostRepository.findById(id), nonNull(req.translations()));
    }

    /** E-MKT-30：删除（TX-MKT-013；blog_post_lifecycle 全态可删） */
    @Transactional
    public void delete(Long id) {
        BlogPost existing = blogPostRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-02 物理删除双表 + 审计
        blogPostRepository.deleteById(id);
        blogPostRepository.deleteTranslationsByPostId(id);
        audit.record("删除文章", existing.getTitle(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ（文章页 revalidate 后 404701，列表移除）
        if (existing.getStatus() == ContentStatus.PUBLISHED) {
            invalidateAfterCommit(existing.getSlug(), null);
        }
    }

    /** E-MKT-31：发布状态变更（TX-MKT-014；blog_post_lifecycle publish/unpublish/republish） */
    @Transactional
    public BlogPostDto patchStatus(Long id, String statusRaw) {
        // V-MKT-057 status 必填枚举
        ContentStatus target = ContentStatus.of(statusRaw);
        if (target == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        BlogPost existing = blogPostRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Map<Long, List<BlogPostTranslationDto>> translations = translationsByPost(List.of(id));
        // STEP-MKT-02 幂等：同态直返（不写审计不发事件）
        if (existing.getStatus() == target) {
            return toDto(existing, translations.getOrDefault(id, List.of()));
        }
        // STEP-MKT-03 迁移 guard（bs-739~743）：draft→published 须 slug 非空（422704）；非法迁移 409703
        if (!ContentStateGuards.transitionAllowed(existing.getStatus(), target)) {
            throw MarketingException.stateInvalid("illegal_transition");
        }
        LocalDateTime publishedAt = null;
        if (target == ContentStatus.PUBLISHED && existing.getStatus() == ContentStatus.DRAFT) {
            if (existing.getSlug() == null || existing.getSlug().isBlank()) {
                throw MarketingException.fieldValidation("slug", "required_for_publish");
            }
            publishedAt = LocalDateTime.now();
        }
        // STEP-MKT-04 UPDATE + 审计（archived→published republish 不刷新 published_at）
        blogPostRepository.updateStatus(id, target, publishedAt);
        audit.record("文章发布状态变更", existing.getTitle(),
                "{\"from\":\"" + existing.getStatus().getKey() + "\",\"to\":\"" + target.getKey() + "\"}");
        // STEP-MKT-05 提交后失效 + MQ + revalidate /blog、/blog/{slug} ×3 + purge
        invalidateAfterCommit(existing.getSlug(), null);
        existing.setStatus(target);
        if (publishedAt != null) {
            existing.setPublishedAt(publishedAt);
        }
        return toDto(existing, translations.getOrDefault(id, List.of()));
    }

    private void invalidateAfterCommit(String slug, String oldSlug) {
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.BLOGS);
            cache.invalidateBlogSlug(slug);
            if (oldSlug != null && !oldSlug.equals(slug)) {
                cache.invalidateBlogSlug(oldSlug);
            }
            publisher.publishBlog(slug, oldSlug);
        });
    }

    private record Normalized(String title, String slug, ContentStatus status) {
    }

    /** V-MKT-050~054 */
    private Normalized validateUpsert(BlogPostUpsert req, boolean create) {
        FieldErrors errors = new FieldErrors();
        // V-MKT-050 title 必填 trim 非空 ≤200（publish guard title!=null 前移为必填）
        String title = MarketingParams.trimToNull(req.title());
        if (title == null) {
            errors.reject("title", "required");
        } else if (title.length() > 200) {
            errors.reject("title", "too_long");
        }
        // V-MKT-051 cover ≤512 / category ≤64 / author ≤64 可选
        MarketingParams.checkMaxLength(req.cover(), 512, "cover", errors);
        MarketingParams.checkMaxLength(req.category(), 64, "category", errors);
        MarketingParams.checkMaxLength(req.author(), 64, "author", errors);
        // V-MKT-053 status 必填；创建态仅 draft/published
        ContentStatus status = ContentStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        } else if (create && status == ContentStatus.ARCHIVED) {
            errors.reject("status", "invalid_initial");
        }
        // V-MKT-052 slug 可选 pattern ^[a-z0-9-]+$ ≤128；status=published 时必填
        String slug = MarketingParams.trimToNull(req.slug());
        if (slug != null && !SLUG_PATTERN.matcher(slug).matches()) {
            errors.reject("slug", "pattern_invalid");
            slug = null;
        }
        if (status == ContentStatus.PUBLISHED && slug == null) {
            errors.reject("slug", "required_for_publish");
        }
        // V-MKT-054 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(title, slug, status);
    }

    /** V-MKT-054 translations locale ∈ {es,fr} 不重复；title ≤200 / excerpt ≤500 / seo_title ≤128 / seo_description ≤255 */
    private void validateTranslations(List<BlogPostTranslationDto> translations, FieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (BlogPostTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.title() != null && t.title().length() > 200) {
                errors.reject("translations", "title_too_long");
            }
            if (t.excerpt() != null && t.excerpt().length() > 500) {
                errors.reject("translations", "excerpt_too_long");
            }
            if (t.seoTitle() != null && t.seoTitle().length() > 128) {
                errors.reject("translations", "seo_title_too_long");
            }
            if (t.seoDescription() != null && t.seoDescription().length() > 255) {
                errors.reject("translations", "seo_description_too_long");
            }
        }
    }

    private void applyUpsert(BlogPost post, Normalized n, BlogPostUpsert req) {
        post.setTitle(n.title());
        post.setCover(MarketingParams.trimToNull(req.cover()));
        post.setCategory(MarketingParams.trimToNull(req.category()));
        post.setAuthor(MarketingParams.trimToNull(req.author()));
        post.setContent(req.content());
        post.setSlug(n.slug());
        post.setStatus(n.status());
    }

    private List<BlogPostTranslation> toTranslationRows(List<BlogPostTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<BlogPostTranslation> rows = new ArrayList<>(dtos.size());
        for (BlogPostTranslationDto dto : dtos) {
            BlogPostTranslation row = new BlogPostTranslation();
            row.setLocale(dto.locale());
            row.setTitle(dto.title());
            row.setExcerpt(dto.excerpt());
            row.setBody(dto.body());
            row.setSeoTitle(dto.seoTitle());
            row.setSeoDescription(dto.seoDescription());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<BlogPostTranslationDto>> translationsByPost(List<Long> ids) {
        Map<Long, List<BlogPostTranslationDto>> map = new HashMap<>();
        for (BlogPostTranslation row : blogPostRepository.listTranslationsByPostIds(ids)) {
            map.computeIfAbsent(row.getBlogPostId(), k -> new ArrayList<>())
                    .add(new BlogPostTranslationDto(row.getLocale(), row.getTitle(), row.getExcerpt(),
                            row.getBody(), row.getSeoTitle(), row.getSeoDescription()));
        }
        return map;
    }

    private List<BlogPostTranslationDto> nonNull(List<BlogPostTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private BlogPostDto toDto(BlogPost p, List<BlogPostTranslationDto> translations) {
        return new BlogPostDto(p.getId(), p.getTitle(), p.getCover(), p.getCategory(), p.getAuthor(), p.getContent(),
                p.getSlug(), p.getStatus().getKey(), p.getPublishedAt(), p.getViews(), translations);
    }
}
