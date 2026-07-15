package com.dreamy.controller;

import com.dreamy.domain.category.service.StoreCategoryService;
import com.dreamy.domain.collection.service.StoreCollectionService;
import com.dreamy.dto.StoreCategoryNode;
import com.dreamy.dto.StoreCollectionGroup;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.support.CatalogFieldErrors;
import com.dreamy.support.StoreParams;
import com.dreamy.i18n.RequestLocaleContext;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消费端分类树与集合导航控制器（E-CAT-06/07，匿名公开——白名单 /api/store/categories、/api/store/collections）。
 */
@RestController
public class StoreCategoryController {

    private static final String CACHE_600 = "no-store";

    private final StoreCategoryService storeCategoryService;
    private final StoreCollectionService storeCollectionService;

    public StoreCategoryController(StoreCategoryService storeCategoryService, StoreCollectionService storeCollectionService) {
        this.storeCategoryService = storeCategoryService;
        this.storeCollectionService = storeCollectionService;
    }

    /** E-CAT-06 listStoreCategories */
    @GetMapping("/api/store/categories")
    public ResponseEntity<R<Map<String, List<StoreCategoryNode>>>> listCategories(
            @RequestParam(required = false) String locale) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        errors.throwIfAny();
        RequestLocaleContext.set(CatalogMessageResolver.toLocale(parsedLocale));
        List<StoreCategoryNode> items = storeCategoryService.listTree(parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_600).body(R.ok(Map.of("items", items)));
    }

    /** E-CAT-07 listStoreCollections */
    @GetMapping("/api/store/collections")
    public ResponseEntity<R<Map<String, List<StoreCollectionGroup>>>> listCollections(
            @RequestParam(required = false) String locale,
            @RequestParam(name = "group_id", required = false) Long groupId) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        Long parsedGroupId = StoreParams.parsePositiveId(groupId, "group_id", errors);
        errors.throwIfAny();
        RequestLocaleContext.set(CatalogMessageResolver.toLocale(parsedLocale));
        List<StoreCollectionGroup> items = storeCollectionService.listGroups(parsedGroupId, parsedLocale);
        return ResponseEntity.ok().header("Cache-Control", CACHE_600).body(R.ok(Map.of("items", items)));
    }
}
