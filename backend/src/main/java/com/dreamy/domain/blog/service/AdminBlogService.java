package com.dreamy.domain.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.blog.entity.BlogPost;
import com.dreamy.domain.blog.entity.BlogPostTranslation;
import com.dreamy.domain.blog.repository.BlogPostRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.enums.ContentStatus;
import com.dreamy.dto.AdminMarketingDtos.BlogPostDto;
import com.dreamy.dto.AdminMarketingDtos.BlogPostUpsert;
import com.dreamy.dto.MarketingTranslationDtos.BlogPostTranslationDto;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.support.ContentStateGuards;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import com.dreamy.support.MarketingPaginatedSupport;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    /** 2026-08-20：slug 保留字黑名单（命中抛 422 RESERVED_SLUG），避免与系统/路由冲突 */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "admin", "api", "blog", "preview", "new", "edit", "_next", "sitemap.xml", "robots.txt", "favicon.ico");

    /** 2026-08-20：正文长度上限（防止 MEDIUMTEXT 滥用 + 控制 RSC 渲染开销） */
    private static final int CONTENT_MAX_LENGTH = 200_000;

    private final BlogPostRepository blogPostRepository;
    private final MarketingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;
    private final BlogPreviewService previewService;
    private final BlogViewsCounter viewsCounter;

    public AdminBlogService(BlogPostRepository blogPostRepository, MarketingAuditRecorder audit,
                            CacheInvalidationTaskService cacheTasks, BlogPreviewService previewService,
                            BlogViewsCounter viewsCounter) {
        this.blogPostRepository = blogPostRepository;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
        this.previewService = previewService;
        this.viewsCounter = viewsCounter;
    }

    /** 2026-08-20 新增：生成预览 token（4h TTL），写入审计 */
    public BlogPreviewService.PreviewToken createPreviewToken(Long id) {
        BlogPost existing = blogPostRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        BlogPreviewService.PreviewToken token = previewService.issue(id);
        audit.record("生成预览 token", existing.getTitle(), null);
        return token;
    }

    /** E-MKT-26：分页列表（status/search 筛选） */
    public Paginated<BlogPostDto> page(Integer page, Integer pageSize, Integer status, String search) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        // V-MKT-048 status ∈ {all, draft, published, archived} 缺省 all
        Integer statusFilter = status;
        if (statusFilter != null && ContentStatus.of(statusFilter) == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-049 search ≤80（title LIKE）
        String parsedSearch = MarketingParams.checkMaxLength(search, 80, "search", errors);
        errors.throwIfAny();

        ContentStatus statusEnum = statusFilter == null ? null : ContentStatus.of(statusFilter);
        Page<BlogPost> result = blogPostRepository.pageAdmin(statusEnum, parsedSearch, parsedPage, parsedPageSize);
        Map<Long, List<BlogPostTranslationDto>> translations = translationsByPost(
                result.getRecords().stream().map(BlogPost::getId).toList());
        // 2026-08-21：实时 views = DB + Redis 未 flush 增量（单次 MGET）
        Map<Long, Long> liveDeltas = viewsCounter.getDeltas(
                result.getRecords().stream().map(BlogPost::getId).toList());
        return MarketingPaginatedSupport.of(result,
                p -> toDto(p, translations.getOrDefault(p.getId(), List.of()),
                        liveDeltas.getOrDefault(p.getId(), 0L)));
    }

    /** E-MKT-28：编辑详情（translations 三语 tab 全量原样，admin 不回退合并） */
    public BlogPostDto get(Long id) {
        BlogPost post = blogPostRepository.findById(id);
        if (post == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Map<Long, List<BlogPostTranslationDto>> translations = translationsByPost(List.of(id));
        // 2026-08-21：实时 views = DB + Redis 未 flush 增量
        return toDto(post, translations.getOrDefault(id, List.of()), viewsCounter.getDelta(id));
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
        // 2026-08-21: 请求显式带 publishedAt → 用请求值（覆盖自动语义）；否则按 status 走默认
        BlogPost post = new BlogPost();
        applyUpsert(post, n, req);
        post.setViews(0);
        if (req.publishedAt() != null) {
            post.setPublishedAt(req.publishedAt());
        } else if (n.status() == ContentStatus.PUBLISHED) {
            post.setPublishedAt(LocalDateTime.now());
        }
        blogPostRepository.insert(post);
        blogPostRepository.replaceTranslations(post.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-03 审计
        audit.record("创建文章", n.title(), null);
        // STEP-MKT-04 提交后（published）失效 + MQ
        if (n.status() == ContentStatus.PUBLISHED) {
            enqueue("blog.create", post, null);
        }
        return toDto(blogPostRepository.findById(post.getId()), nonNull(req.translations()));
    }

    /** E-MKT-29：编辑（TX-MKT-012；已发布保存即触发失效链 s-758）
     *  2026-08-20: 加乐观锁——req.version 必须与 DB 一致，否则抛 409 VERSION_CONFLICT */
    @Transactional
    public BlogPostDto update(Long id, BlogPostUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        BlogPost existing = blogPostRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // 2026-08-20: 乐观锁版本校验（前端在表单带回 version；version=null 视为旧客户端,允许继续以兼容）
        if (req.version() != null && !req.version().equals(existing.getVersion())) {
            throw new MarketingException(MarketingErrorCode.VERSION_CONFLICT);
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
        // STEP-MKT-04 UPDATE（SET 不含 views——V-MKT-056）
        // 2026-08-21: published_at 优先级——请求显式带 → 用请求值（运营可改首次发布时间）；
        //                     请求 null + draft→published → 自动 now()；
        //                     请求 null + 其他场景 → 保留 DB 现值
        applyUpsert(existing, n, req);
        if (req.publishedAt() != null) {
            existing.setPublishedAt(req.publishedAt());
        } else if (statusChanged && oldStatus == ContentStatus.DRAFT && n.status() == ContentStatus.PUBLISHED) {
            existing.setPublishedAt(LocalDateTime.now());
        }
        int updated = blogPostRepository.update(existing);
        if (updated == 0) {
            // 并发下 version 被他人 +1 导致 update 失败
            throw new MarketingException(MarketingErrorCode.VERSION_CONFLICT);
        }
        blogPostRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-05 审计
        audit.record("编辑文章", n.title(), null);
        // STEP-MKT-06 提交后（DB 或目标 status=published，或 published→archived 下线）失效（新旧 slug 都失效）+ MQ
        if (oldStatus == ContentStatus.PUBLISHED || n.status() == ContentStatus.PUBLISHED) {
            enqueue("blog.update", existing, oldSlug);
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
        // STEP-MKT-02 物理删除双表 + 审计（先清译文，再删主表）
        blogPostRepository.deleteTranslationsByPostId(id);
        blogPostRepository.deleteById(id);
        audit.record("删除文章", existing.getTitle(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ（文章页 revalidate 后 404701，列表移除）
        if (existing.getStatus() == ContentStatus.PUBLISHED) {
            enqueue("blog.delete", existing, null);
        }
    }

    /** E-MKT-31：发布状态变更（TX-MKT-014；blog_post_lifecycle publish/unpublish/republish）
     *  2026-08-20: 改状态前置条件 UPDATE（不走 @Version，避免与 update() 互相阻塞）；
     *  并发下同态操作后置者收 409 STATE_CHANGED */
    @Transactional
    public BlogPostDto patchStatus(Long id, Integer statusRaw) {
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
        // 2026-08-20: 状态前置条件 UPDATE，受影响行=0 表示并发下状态已被他人变更
        ContentStatus fromStatus = existing.getStatus();
        int updated = blogPostRepository.updateStatusWithPrecondition(id, fromStatus, target, publishedAt);
        if (updated == 0) {
            throw new MarketingException(MarketingErrorCode.STATE_CHANGED);
        }
        // STEP-MKT-04 审计（archived→published republish 不刷新 published_at）
        audit.record("文章发布状态变更", existing.getTitle(),
                "{\"from\":\"" + fromStatus.getKey() + "\",\"to\":\"" + target.getKey() + "\"}");
        // STEP-MKT-05 提交后失效 + MQ + revalidate /blog、/blog/{slug} ×3 + purge
        enqueue("blog.status", existing, null);
        existing.setStatus(target);
        if (publishedAt != null) {
            existing.setPublishedAt(publishedAt);
        }
        return toDto(existing, translations.getOrDefault(id, List.of()));
    }

    private void enqueue(String triggerPoint, BlogPost post, String oldSlug) {
        java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
        if (post.getSlug() != null) details.put("slug", post.getSlug());
        if (oldSlug != null && !oldSlug.equals(post.getSlug())) details.put("old_slug", oldSlug);
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "blog", post.getId(), post.getTitle(), CacheInvalidationPlans.BLOG,
                null, details, null);
    }

    private record Normalized(String title, String slug, ContentStatus status) {
    }

    /** V-MKT-050~054 + 2026-08-20 新增: slug 归一化小写 + 保留字黑名单 + content 长度上限 + EN 三列长度校验 */
    private Normalized validateUpsert(BlogPostUpsert req, boolean create) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
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
        // 2026-08-20: EN 主表 SEO 三列长度
        MarketingParams.checkMaxLength(req.excerpt(), 500, "excerpt", errors);
        MarketingParams.checkMaxLength(req.seoTitle(), 128, "seo_title", errors);
        MarketingParams.checkMaxLength(req.seoDescription(), 255, "seo_description", errors);
        // 2026-08-20: content 长度上限（MEDIUMTEXT 防御）
        if (req.content() != null && req.content().length() > CONTENT_MAX_LENGTH) {
            throw new MarketingException(MarketingErrorCode.CONTENT_TOO_LARGE);
        }
        // V-MKT-053 status 必填；创建态仅 draft/published
        ContentStatus status = ContentStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        } else if (create && status == ContentStatus.ARCHIVED) {
            errors.reject("status", "invalid_initial");
        }
        // V-MKT-052 slug 可选 pattern ^[a-z0-9-]+$ ≤128；status=published 时必填
        // 2026-08-20: 先归一化小写再校验；保留字黑名单
        String slug = MarketingParams.trimToNull(req.slug());
        if (slug != null) {
            slug = slug.toLowerCase(Locale.ROOT).trim();
            if (RESERVED_SLUGS.contains(slug)) {
                throw new MarketingException(MarketingErrorCode.RESERVED_SLUG);
            }
            if (!SLUG_PATTERN.matcher(slug).matches()) {
                errors.reject("slug", "pattern_invalid");
                slug = null;
            }
        }
        if (status == ContentStatus.PUBLISHED && slug == null) {
            errors.reject("slug", "required_for_publish");
        }
        // 2026-08-21: publishedAt 不允许是未来时间（防止预发布错觉；留 null 走自动语义）
        if (req.publishedAt() != null && req.publishedAt().isAfter(LocalDateTime.now())) {
            errors.reject("published_at", "future_not_allowed");
        }
        // V-MKT-054 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(title, slug, status);
    }

    /** V-MKT-054 translations locale ∈ {es,fr} 不重复；title ≤200 / excerpt ≤500 / seo_title ≤128 / seo_description ≤255 */
    private void validateTranslations(List<BlogPostTranslationDto> translations, MarketingFieldErrors errors) {
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
        post.setExcerpt(MarketingParams.trimToNull(req.excerpt()));
        post.setSeoTitle(MarketingParams.trimToNull(req.seoTitle()));
        post.setSeoDescription(MarketingParams.trimToNull(req.seoDescription()));
        post.setSlug(n.slug());
        post.setStatus(n.status());
        // 2026-08-20: 字数统计 + 阅读时长（保存时同步计算，10 万字 < 100ms）
        computeWordCountAndReadingTime(post);
    }

    /** 2026-08-20: 中英混排字数统计 —— CJK 按字、Latin 按词（空白分隔）；阅读时长 = cjk/300 + latin/200（分钟，向上取 0.1） */
    private void computeWordCountAndReadingTime(BlogPost post) {
        String content = post.getContent();
        if (content == null || content.isBlank()) {
            post.setWordCount(0);
            post.setReadingMinutes(BigDecimal.ZERO);
            return;
        }
        int cjk = content.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
        String latinText = content.replaceAll("[\\u4e00-\\u9fa5]", " ").trim();
        int latin = latinText.isEmpty() ? 0 : latinText.split("\\s+").length;
        post.setWordCount(cjk + latin);
        double minutes = (cjk / 300.0) + (latin / 200.0);
        post.setReadingMinutes(BigDecimal.valueOf(minutes).setScale(1, RoundingMode.CEILING));
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
        return toDto(p, translations, 0L);
    }

    /** 2026-08-21：实时 views 版（delta 来自 Redis 未 flush 增量） */
    private BlogPostDto toDto(BlogPost p, List<BlogPostTranslationDto> translations, long viewsDelta) {
        int baseViews = p.getViews() == null ? 0 : p.getViews();
        int liveViews = (int) Math.min(Integer.MAX_VALUE, baseViews + viewsDelta);
        return new BlogPostDto(p.getId(), p.getTitle(), p.getCover(), p.getCategory(), p.getAuthor(), p.getContent(),
                p.getSlug(), p.getStatus().getKey(), p.getPublishedAt(), liveViews,
                p.getExcerpt(), p.getSeoTitle(), p.getSeoDescription(),
                p.getWordCount(), p.getReadingMinutes(), p.getVersion(),
                translations);
    }
}
