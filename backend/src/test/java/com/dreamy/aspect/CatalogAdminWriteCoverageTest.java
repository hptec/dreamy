package com.dreamy.aspect;

import com.dreamy.domain.attribute.service.AttributeDefService;
import com.dreamy.domain.attribute.service.AttributeSetService;
import com.dreamy.domain.category.service.AdminCategoryService;
import com.dreamy.domain.collection.service.CollectionAdminService;
import com.dreamy.domain.product.service.AdminProductBatchService;
import com.dreamy.domain.product.service.AdminProductService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogAdminWriteCoverageTest {

    private static final List<Class<?>> CATALOG_ADMIN_SERVICES = List.of(
            AdminProductService.class,
            AdminCategoryService.class,
            AttributeSetService.class,
            AttributeDefService.class,
            CollectionAdminService.class
    );

    @Test
    void everyTransactionalCatalogAdminWriteUsesGlobalLock() {
        for (Class<?> serviceType : CATALOG_ADMIN_SERVICES) {
            List<Method> transactionalMethods = Arrays.stream(serviceType.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Transactional.class))
                    .toList();

            assertThat(transactionalMethods)
                    .as("%s must expose transactional write methods", serviceType.getSimpleName())
                    .isNotEmpty()
                    .allMatch(method -> method.isAnnotationPresent(CatalogAdminWrite.class),
                            "all transactional Catalog admin methods must hold the global write lock");
        }
    }

    @Test
    void programmaticAndBatchProductWritesUseGlobalLock() {
        assertLocked(AdminProductService.class, "toggleStatus");
        assertLocked(AdminProductBatchService.class, "execute");
    }

    @Test
    void collectionMembershipWritesUseGlobalLock() {
        assertLocked(CollectionAdminService.class, "replaceCollectionProducts");
        assertLocked(CollectionAdminService.class, "removeCollectionProduct");
    }

    private static void assertLocked(Class<?> serviceType, String methodName) {
        List<Method> methods = Arrays.stream(serviceType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();

        assertThat(methods)
                .as("%s.%s", serviceType.getSimpleName(), methodName)
                .isNotEmpty()
                .allMatch(method -> method.isAnnotationPresent(CatalogAdminWrite.class));
    }
}
