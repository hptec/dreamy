package com.dreamy.controller;

import com.dreamy.domain.product.service.RecommendationService;
import com.dreamy.domain.product.service.SizeRecommendationService;
import com.dreamy.domain.product.service.StoreProductService;
import com.dreamy.dto.SizeRecommendation;
import com.dreamy.dto.StoreAttributeDtos.StoreFilterDimDto;
import com.dreamy.dto.StoreProductCard;
import com.dreamy.dto.StoreProductDetail;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.support.CatalogFieldErrors;
import com.dreamy.support.StoreParams;
import com.dreamy.i18n.RequestLocaleContext;
import huihao.page.Paginated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import huihao.web.R;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 消费端商品控制器（E-CAT-01~05，全部匿名公开——StoreJwtFilter 白名单 /api/store/products/**）。
 * i18n：locale query 优先于 Accept-Language（api-detail §0）；开发阶段统一输出 no-store。
 */
@RestController
public class StoreProductController {

    private static final String CACHE_300 = "no-store";

    private final StoreProductService storeProductService;
    private final RecommendationService recommendationService;
    private final SizeRecommendationService sizeRecommendationService;

    public StoreProductController(StoreProductService storeProductService,
                                  RecommendationService recommendationService,
                                  SizeRecommendationService sizeRecommendationService) {
        this.storeProductService = storeProductService;
        this.recommendationService = recommendationService;
        this.sizeRecommendationService = sizeRecommendationService;
    }

    /** E-CAT-01 listStoreProducts（attr 重复参数：?attr=silhouette:A-Line&attr=fabric:Tulle） */
    @GetMapping("/api/store/products")
    public ResponseEntity<R<Paginated<StoreProductCard>>> listProducts(
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(name = "collection_id", required = false) Long collectionId,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String size,
            @RequestParam(name = "price_min", required = false) BigDecimal priceMin,
            @RequestParam(name = "price_max", required = false) BigDecimal priceMax,
            @RequestParam(required = false) String sort,
            @RequestParam(name = "attr", required = false) List<String> attr) {
        StoreProductService.ListQuery query = storeProductService.parseListQuery(
                locale, page, pageSize, categoryId, collectionId, color, size, priceMin, priceMax, sort, attr);
        applyLocale(query.locale());
        Paginated<StoreProductCard> result = storeProductService.listProducts(query);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(result));
    }

    /** E-CAT-27 listStoreProductFilters：分类动态属性筛选维度（PLP 筛选组数据源） */
    @GetMapping("/api/store/products/filters")
    public ResponseEntity<R<Map<String, List<StoreFilterDimDto>>>> listFilters(
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false) String locale) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        Long parsedCategoryId = StoreParams.parsePositiveId(categoryId, "category_id", errors);
        errors.throwIfAny();
        applyLocale(parsedLocale);
        List<StoreFilterDimDto> items = storeProductService.listFilters(parsedCategoryId, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", items)));
    }

    /** E-CAT-02 searchStoreProducts（CDN 不缓存，决策 17） */
    @GetMapping("/api/store/products/search")
    public ResponseEntity<R<Paginated<StoreProductCard>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        int parsedPage = StoreParams.parsePage(page, errors);
        int parsedSize = StoreParams.parsePageSize(pageSize, errors);
        errors.throwIfAny();
        applyLocale(parsedLocale);
        return ResponseEntity.ok(R.ok(storeProductService.search(q, parsedLocale, parsedPage, parsedSize)));
    }

    /** E-CAT-03 listStoreRecommendations */
    @GetMapping("/api/store/products/recommendations")
    public ResponseEntity<R<Map<String, List<StoreProductCard>>>> recommendations(
            @RequestParam(required = false) String block,
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(name = "collection_id", required = false) Long collectionId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String locale) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        errors.throwIfAny();
        applyLocale(parsedLocale);
        List<StoreProductCard> items = recommendationService.recommend(block, productId, collectionId, limit, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(Map.of("items", items)));
    }

    /** E-CAT-04 getStoreProduct（PDP，ISR 商品页同源） */
    @GetMapping("/api/store/products/{slug}")
    public ResponseEntity<R<StoreProductDetail>> getProduct(
            @PathVariable String slug,
            @RequestParam(required = false) String locale) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        errors.throwIfAny();
        applyLocale(parsedLocale);
        StoreProductDetail detail = storeProductService.getProduct(slug, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_300).body(R.ok(detail));
    }

    /** E-CAT-05 recommendSize（公开 POST，写限流在 WAF——决策 11；纯函数不缓存） */
    @PostMapping("/api/store/products/{id}/size-recommendation")
    public ResponseEntity<R<SizeRecommendation.Response>> recommendSize(
            @PathVariable String id,
            @RequestParam(required = false) String locale,
            @RequestBody SizeRecommendation.Request request) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        errors.throwIfAny();
        applyLocale(parsedLocale);
        Long productId = parseId(id);
        if (productId == null) {
            // 非法 id 与不存在同口径（V-CAT-037 口径防探测）
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        return ResponseEntity.ok(R.ok(sizeRecommendationService.recommend(productId, parsedLocale, request)));
    }

    /** locale query 优先于 Accept-Language（错误文案/话术同语种） */
    private void applyLocale(String locale) {
        RequestLocaleContext.set(CatalogMessageResolver.toLocale(locale));
    }

    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
