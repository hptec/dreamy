package com.dreamy.catalog.controller;

import com.dreamy.catalog.domain.category.service.StoreCategoryService;
import com.dreamy.catalog.domain.tag.service.StoreTagService;
import com.dreamy.catalog.dto.StoreCategoryNode;
import com.dreamy.catalog.dto.StoreTagDimensionGroup;
import com.dreamy.catalog.i18n.CatalogMessageResolver;
import com.dreamy.catalog.support.FieldErrors;
import com.dreamy.catalog.support.StoreParams;
import com.dreamy.identity.i18n.RequestLocaleContext;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消费端分类树与标签导航控制器（E-CAT-06/07，匿名公开——白名单 /api/store/categories、/api/store/tags）。
 */
@RestController
public class StoreCategoryController {

    private static final String CACHE_600 = "s-maxage=600";

    private final StoreCategoryService storeCategoryService;
    private final StoreTagService storeTagService;

    public StoreCategoryController(StoreCategoryService storeCategoryService, StoreTagService storeTagService) {
        this.storeCategoryService = storeCategoryService;
        this.storeTagService = storeTagService;
    }

    /** E-CAT-06 listStoreCategories */
    @GetMapping("/api/store/categories")
    public ResponseEntity<R<Map<String, List<StoreCategoryNode>>>> listCategories(
            @RequestParam(required = false) String locale) {
        FieldErrors errors = new FieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(CatalogMessageResolver.toLocale(parsedLocale));
        List<StoreCategoryNode> items = storeCategoryService.listTree(parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_600).body(R.ok(Map.of("items", items)));
    }

    /** E-CAT-07 listStoreTags */
    @GetMapping("/api/store/tags")
    public ResponseEntity<R<Map<String, List<StoreTagDimensionGroup>>>> listTags(
            @RequestParam(required = false) String locale,
            @RequestParam(name = "dimension_id", required = false) Long dimensionId) {
        FieldErrors errors = new FieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        Long parsedDimensionId = StoreParams.parsePositiveId(dimensionId, "dimension_id", errors);
        errors.throwIfAny();
        RequestLocaleContext.set(CatalogMessageResolver.toLocale(parsedLocale));
        List<StoreTagDimensionGroup> items = storeTagService.listGroups(parsedDimensionId, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_600).body(R.ok(Map.of("items", items)));
    }
}
