package com.dreamy.domain.collection.service;

import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionGroup;
import com.dreamy.domain.collection.repository.CollectionGroupRepository;
import com.dreamy.domain.collection.repository.CollectionRepository;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.repository.ProductCollectionRepository;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.dto.StoreCollectionGroup.StoreCollectionItem;
import com.dreamy.enums.ImageKind;
import com.dreamy.enums.ProductStatus;
import com.dreamy.infra.CatalogCacheService;
import com.dreamy.infra.CatalogCacheService.Family;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoreCollectionServiceTest {

    @Mock private CollectionGroupRepository groupRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private ProductCollectionRepository productCollectionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private CatalogCacheService cache;

    private StoreCollectionService service;

    @BeforeEach
    void setUp() {
        service = new StoreCollectionService(groupRepository, collectionRepository,
                productCollectionRepository, productRepository, productImageRepository, cache);
    }

    @Test
    void themeCollectionsUseFirstPublishedGalleryImage() {
        CollectionGroup themeGroup = group(10L, "Theme");
        Collection garden = collection(20L, 10L, "Garden");
        Collection beach = collection(21L, 10L, "Beach");
        when(groupRepository.listAll()).thenReturn(List.of(themeGroup));
        when(groupRepository.findById(10L)).thenReturn(themeGroup);
        when(collectionRepository.listEnabled(10L)).thenReturn(List.of(garden, beach));
        when(productCollectionRepository.countByCollections(true)).thenReturn(Map.of(20L, 2, 21L, 1));
        when(productCollectionRepository.listFirstNProductIdsByCollections(List.of(20L, 21L), 8))
                .thenReturn(Map.of(20L, List.of(100L, 101L), 21L, List.of(102L)));
        when(productRepository.listByIds(java.util.Set.of(100L, 101L, 102L))).thenReturn(List.of(
                product(100L, ProductStatus.DRAFT),
                product(101L, ProductStatus.PUBLISHED),
                product(102L, ProductStatus.PUBLISHED)));
        when(productImageRepository.listByProductIds(java.util.Set.of(101L, 102L))).thenReturn(List.of(
                image(101L, "/garden-scene.jpg", ImageKind.LIFESTYLE),
                image(101L, "/garden.jpg", ImageKind.GALLERY),
                image(102L, "/beach.jpg", ImageKind.GALLERY)));
        when(cache.lookup(Family.COLLECTIONS, "v2:10:en"))
                .thenReturn(new CatalogCacheService.Lookup(Family.COLLECTIONS, "v2:10:en", 0, null));

        List<StoreCollectionItem> themes = service.listThemeCollections("en");

        assertThat(themes).extracting(StoreCollectionItem::name)
                .containsExactly("Garden", "Beach");
        assertThat(themes).extracting(StoreCollectionItem::imageUrl)
                .containsExactly("/garden.jpg", "/beach.jpg");
    }

    private static CollectionGroup group(long id, String name) {
        CollectionGroup group = new CollectionGroup();
        group.setId(id);
        group.setName(name);
        return group;
    }

    private static Collection collection(long id, long groupId, String name) {
        Collection collection = new Collection();
        collection.setId(id);
        collection.setCollectionGroupId(groupId);
        collection.setName(name);
        return collection;
    }

    private static Product product(long id, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setStatus(status);
        return product;
    }

    private static ProductImage image(long productId, String url, ImageKind kind) {
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setUrl(url);
        image.setKind(kind);
        return image;
    }
}
