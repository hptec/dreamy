package com.dreamy.controller;

import com.dreamy.domain.banner.service.StoreBannerService;
import com.dreamy.domain.blog.service.BlogPreviewService;
import com.dreamy.domain.blog.service.StoreBlogService;
import com.dreamy.enums.BannerPosition;
import com.dreamy.domain.guide.service.GuideService;
import com.dreamy.domain.lookbook.service.StoreLookbookService;
import com.dreamy.domain.wedding.service.StoreWeddingService;
import com.dreamy.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.dto.StoreMarketingDtos.StoreBlogPostCard;
import com.dreamy.dto.StoreMarketingDtos.StoreBlogPostDetail;
import com.dreamy.dto.StoreMarketingDtos.StoreGuide;
import com.dreamy.dto.StoreMarketingDtos.StoreLookbook;
import com.dreamy.dto.StoreMarketingDtos.StoreRealWedding;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.i18n.MarketingMessageResolver;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import com.dreamy.i18n.RequestLocaleContext;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消费端内容控制器（E-MKT-01~08，全部匿名公开——白名单 `/api/store/content/**`，api-detail §0.1）。
 * force-dynamic 商城直连后端，响应 no-store；共享新鲜度由 JetCache + durable invalidation task 保证。
 */
@RestController
public class StoreContentController {

    private static final String CACHE_300 = "no-store";

    private final StoreBannerService bannerService;
    private final StoreBlogService blogService;
    private final StoreWeddingService weddingService;
    private final StoreLookbookService lookbookService;
    private final GuideService guideService;
    private final BlogPreviewService blogPreviewService;

    public StoreContentController(StoreBannerService bannerService, StoreBlogService blogService,
                                  StoreWeddingService weddingService, StoreLookbookService lookbookService,
                                  GuideService guideService, BlogPreviewService blogPreviewService) {
        this.bannerService = bannerService;
        this.blogService = blogService;
        this.weddingService = weddingService;
        this.lookbookService = lookbookService;
        this.guideService = guideService;
        this.blogPreviewService = blogPreviewService;
    }

    /** E-MKT-01 listStoreBanners（V-MKT-001/002） */
    @GetMapping("/api/store/content/banners")
    public ResponseEntity<R<Map<String, List<StoreBanner>>>> listBanners(
            @RequestParam(required = false) Integer position,
            @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        BannerPosition parsedPosition = null;
        if (position != null) {
            parsedPosition = BannerPosition.of(position);
            if (parsedPosition == null) {
                errors.reject("position", "invalid_enum");
            }
        }
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        List<StoreBanner> items = bannerService.list(parsedPosition, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", items)));
    }

    /** E-MKT-02 listStoreBlogs（V-MKT-002/003/004） */
    @GetMapping("/api/store/content/blogs")
    public ResponseEntity<R<Paginated<StoreBlogPostCard>>> listBlogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        String parsedCategory = MarketingParams.checkMaxLength(category, 64, "category", errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        Paginated<StoreBlogPostCard> result = blogService.page(parsedCategory, parsedPage, parsedPageSize,
                parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(result));
    }

    /** E-MKT-03 getStoreBlog（V-MKT-005；slug 非法与不存在同口径 404701） */
    @GetMapping("/api/store/content/blogs/{slug}")
    public ResponseEntity<R<StoreBlogPostDetail>> getBlog(@PathVariable String slug,
                                                          @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        StoreBlogPostDetail detail = blogService.getBySlug(slug, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(detail));
    }

    /** 2026-08-21 新增：E-MKT-03B 阅读计数（sessionStorage UV 由前端去重，本端点信任调用）。
     *  无缓存无鉴权无响应体；slug 不存在/未发布静默 204（防探测同 E-MKT-03 口径）。 */
    @PostMapping("/api/store/content/blogs/{slug}/view")
    public ResponseEntity<Void> recordBlogView(@PathVariable String slug) {
        blogService.recordView(slug);
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }

    /** 2026-08-20 新增：草稿预览（凭 token 直读，不校验 status，不走缓存）。
     *  X-Robots-Tag noindex/nofollow 防搜索引擎索引预览链接。 */
    @GetMapping("/api/store/content/blogs/preview/{token}")
    public ResponseEntity<R<StoreBlogPostDetail>> previewBlog(@PathVariable String token,
                                                              @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        Long postId = blogPreviewService.resolvePostId(token);
        StoreBlogPostDetail detail = blogService.getByIdForPreview(postId, parsedLocale);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(R.ok(detail));
    }

    /** 2026-08-20 新增：sitemap 数据（仅 published，供 portal-store app/sitemap.ts 拉取）。
     *  路径用 /sitemap-blogs 避免与 /blogs/{slug} 冲突。 */
    @GetMapping("/api/store/content/sitemap-blogs")
    public ResponseEntity<R<Map<String, Object>>> sitemapBlogs() {
        List<StoreBlogService.SitemapEntry> entries = blogService.listPublishedForSitemap();
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", entries)));
    }

    /** E-MKT-04 listStoreWeddings（复用 V-MKT-002/004） */
    @GetMapping("/api/store/content/weddings")
    public ResponseEntity<R<Paginated<StoreRealWedding>>> listWeddings(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        Paginated<StoreRealWedding> result = weddingService.page(parsedPage, parsedPageSize, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(result));
    }

    /** E-MKT-05 getStoreWedding（V-MKT-006 非法 id → 404701 同口径） */
    @GetMapping("/api/store/content/weddings/{id}")
    public ResponseEntity<R<StoreRealWedding>> getWedding(@PathVariable String id,
                                                          @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        StoreRealWedding dto = weddingService.get(parseContentId(id), parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(dto));
    }

    /** E-MKT-06 listStoreLookbooks（V-MKT-002） */
    @GetMapping("/api/store/content/lookbooks")
    public ResponseEntity<R<Map<String, List<StoreLookbook>>>> listLookbooks(
            @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        List<StoreLookbook> items = lookbookService.list(parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", items)));
    }

    /** E-MKT-07 getStoreLookbook（V-MKT-006 口径） */
    @GetMapping("/api/store/content/lookbooks/{id}")
    public ResponseEntity<R<StoreLookbook>> getLookbook(@PathVariable String id,
                                                        @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        StoreLookbook dto = lookbookService.get(parseContentId(id), parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(dto));
    }

    /** E-MKT-08 listStoreGuides（V-MKT-002） */
    @GetMapping("/api/store/content/guides")
    public ResponseEntity<R<Map<String, List<StoreGuide>>>> listGuides(
            @RequestParam(required = false) String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        List<StoreGuide> items = guideService.listStore(parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", items)));
    }

    /** V-MKT-006：id 非数字/非正 → 404701（防探测同口径） */
    private Long parseContentId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
    }
}
