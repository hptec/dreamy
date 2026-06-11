package com.dreamy.marketing.controller;

import com.dreamy.marketing.domain.banner.service.StoreBannerService;
import com.dreamy.marketing.domain.blog.service.StoreBlogService;
import com.dreamy.marketing.domain.enums.BannerPosition;
import com.dreamy.marketing.domain.guide.service.GuideService;
import com.dreamy.marketing.domain.lookbook.service.StoreLookbookService;
import com.dreamy.marketing.domain.wedding.service.StoreWeddingService;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreBanner;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreBlogPostCard;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreBlogPostDetail;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreGuide;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreLookbook;
import com.dreamy.marketing.dto.StoreMarketingDtos.StoreRealWedding;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.i18n.MarketingMessageResolver;
import com.dreamy.marketing.support.FieldErrors;
import com.dreamy.marketing.support.MarketingParams;
import com.dreamy.identity.i18n.RequestLocaleContext;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消费端内容控制器（E-MKT-01~08，全部匿名公开——白名单 `/api/store/content/**`，api-detail §0.1）。
 * 响应带 `Cache-Control: s-maxage=300`（CDN 层，CACHE-MKT §8）；locale query 优先于 Accept-Language。
 */
@RestController
public class StoreContentController {

    private static final String CACHE_300 = "s-maxage=300";

    private final StoreBannerService bannerService;
    private final StoreBlogService blogService;
    private final StoreWeddingService weddingService;
    private final StoreLookbookService lookbookService;
    private final GuideService guideService;

    public StoreContentController(StoreBannerService bannerService, StoreBlogService blogService,
                                  StoreWeddingService weddingService, StoreLookbookService lookbookService,
                                  GuideService guideService) {
        this.bannerService = bannerService;
        this.blogService = blogService;
        this.weddingService = weddingService;
        this.lookbookService = lookbookService;
        this.guideService = guideService;
    }

    /** E-MKT-01 listStoreBanners（V-MKT-001/002） */
    @GetMapping("/api/store/content/banners")
    public ResponseEntity<R<Map<String, List<StoreBanner>>>> listBanners(
            @RequestParam(required = false) String position,
            @RequestParam(required = false) String locale) {
        FieldErrors errors = new FieldErrors();
        BannerPosition parsedPosition = null;
        if (position != null && !position.isBlank()) {
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
        FieldErrors errors = new FieldErrors();
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
        FieldErrors errors = new FieldErrors();
        String parsedLocale = MarketingParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(MarketingMessageResolver.toLocale(parsedLocale));
        StoreBlogPostDetail detail = blogService.getBySlug(slug, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(detail));
    }

    /** E-MKT-04 listStoreWeddings（复用 V-MKT-002/004） */
    @GetMapping("/api/store/content/weddings")
    public ResponseEntity<R<Paginated<StoreRealWedding>>> listWeddings(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        FieldErrors errors = new FieldErrors();
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
        FieldErrors errors = new FieldErrors();
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
        FieldErrors errors = new FieldErrors();
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
        FieldErrors errors = new FieldErrors();
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
        FieldErrors errors = new FieldErrors();
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
