package com.dreamy.domain.lookbook.service;

import com.dreamy.port.CatalogQueryPort;
import com.dreamy.port.CatalogQueryPort.ProductRef;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Resolves the optional Lookbook cover fallback from linked published product images. */
final class LookbookCoverResolver {

    private LookbookCoverResolver() {
    }

    static String firstImage(List<Long> productIds, List<ProductRef> products) {
        if (productIds == null || productIds.isEmpty() || products == null || products.isEmpty()) {
            return null;
        }
        Map<Long, String> imageByProduct = new HashMap<>();
        for (ProductRef product : products) {
            if (product != null && product.id() != null && hasText(product.imageUrl())) {
                imageByProduct.putIfAbsent(product.id(), product.imageUrl());
            }
        }
        for (Long productId : productIds) {
            String image = imageByProduct.get(productId);
            if (hasText(image)) {
                return image;
            }
        }
        return null;
    }

    static Map<Long, String> resolve(Map<Long, List<Long>> productIdsByLookbook,
                                     CatalogQueryPort catalogQueryPort, String locale) {
        Map<Long, String> result = new HashMap<>();
        if (productIdsByLookbook == null || productIdsByLookbook.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> productIds = new LinkedHashSet<>();
        for (List<Long> ids : productIdsByLookbook.values()) {
            if (ids != null) {
                productIds.addAll(ids);
            }
        }
        if (productIds.isEmpty()) {
            return result;
        }
        List<ProductRef> products = catalogQueryPort.listProductRefs(productIds, locale);
        for (Map.Entry<Long, List<Long>> entry : productIdsByLookbook.entrySet()) {
            String image = firstImage(entry.getValue(), products);
            if (hasText(image)) {
                result.put(entry.getKey(), image);
            }
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
