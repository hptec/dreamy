package com.dreamy.domain.product.service;

import com.dreamy.enums.ImageKind;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.entity.ProductTranslation;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductTranslationRepository;
import com.dreamy.dto.StoreProductCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 翻译回退合并 + 卡片派生字段单元测试（决策 13 / MAP-CAT-001）。
 * L2 TRACE: TC-CAT-013（翻译回退合并）/ NP-CAT-001。
 */
@ExtendWith(MockitoExtension.class)
class ProductCardAssemblerTest {

    @Mock
    ProductImageRepository imageRepository;
    @Mock
    ProductTranslationRepository translationRepository;
    @InjectMocks
    ProductCardAssembler assembler;

    private static Product product(long id) {
        Product p = new Product();
        p.setId(id);
        p.setSlug("aurelia-gown");
        p.setName("Aurelia Gown");
        p.setPrice(new BigDecimal("1280"));
        p.setRatingAvg(new BigDecimal("4.90"));
        p.setRatingCount(142);
        return p;
    }

    private static ProductImage image(long productId, ImageKind kind, String color, int sort, String url) {
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setKind(kind);
        img.setColorName(color);
        img.setSort(sort);
        img.setUrl(url);
        return img;
    }

    @Test
    @DisplayName("TC-CAT-013 [P0]: es 命中附表覆盖 name；fr 部分字段缺失逐字段回退 EN")
    void translationFallbackMerge() {
        // es：name 有译文
        ProductTranslation es = new ProductTranslation();
        es.setProductId(1L);
        es.setLocale("es");
        es.setName("Vestido Aurelia");
        when(translationRepository.listByProductIds(anyCollection(), eq("es"))).thenReturn(List.of(es));
        when(imageRepository.listByProductIds(anyCollection())).thenReturn(List.of());
        List<StoreProductCard> cards = assembler.assemble(List.of(product(1)), "es");
        assertThat(cards.get(0).name()).isEqualTo("Vestido Aurelia");
        // en：不查附表，原样 EN
        List<StoreProductCard> enCards = assembler.assemble(List.of(product(1)), "en");
        assertThat(enCards.get(0).name()).isEqualTo("Aurelia Gown");
        // fr：无译文行 → 全字段回退 EN
        when(translationRepository.listByProductIds(anyCollection(), eq("fr"))).thenReturn(List.of());
        List<StoreProductCard> frCards = assembler.assemble(List.of(product(1)), "fr");
        assertThat(frCards.get(0).name()).isEqualTo("Aurelia Gown");
    }

    @Test
    @DisplayName("MAP-CAT-001 [P0]: 主图=gallery sort=0；swatches=kind=swatch；rating 冗余列直读")
    void cardDerivedFields() {
        when(imageRepository.listByProductIds(anyCollection())).thenReturn(List.of(
                image(1L, ImageKind.GALLERY, null, 1, "/g1.jpg"),
                image(1L, ImageKind.GALLERY, null, 0, "/main.jpg"),
                image(1L, ImageKind.SWATCH, "Ivory", 0, "/sw.jpg"),
                image(1L, ImageKind.LIFESTYLE, null, 0, "/life.jpg")));
        List<StoreProductCard> cards = assembler.assemble(List.of(product(1)), "en");
        StoreProductCard card = cards.get(0);
        assertThat(card.imageUrl()).isEqualTo("/main.jpg");
        assertThat(card.swatches()).hasSize(1);
        assertThat(card.swatches().get(0).colorName()).isEqualTo("Ivory");
        assertThat(card.ratingAvg()).isEqualByComparingTo("4.9");
        assertThat(card.ratingCount()).isEqualTo(142);
    }
}
