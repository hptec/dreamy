package com.dreamy.dto;

import com.dreamy.dto.ReviewDtos.StoreReviewDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paginated 子类序列化形状回归（MAP-REV-006/007 定稿验证：六分页字段 + 聚合字段平铺同层无嵌套，
 * snake_case 命中契约 StoreReviewListResponse/AdminReviewListResponse required 结构）。
 * L2 TRACE: TC-REV-030 [P0]。
 */
class PaginatedFlatteningTest {

    /** 与 application.yml spring.jackson.property-naming-strategy=SNAKE_CASE + JSR310 等价配置 */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    @DisplayName("TC-REV-030 [P0]: StoreReviewListResponse——六分页字段与 rating_avg/rating_count/rating_breakdown 同层平铺")
    void storeReviewListFlattened() throws Exception {
        StoreReviewListDTO dto = new StoreReviewListDTO();
        dto.setData(List.of(new StoreReviewDto(1L, 11L, "Madison R.", 5, "lovely", 2, true,
                LocalDateTime.of(2026, 6, 9, 21, 14), List.of(), null, null, null)));
        dto.setTotalElements(1L);
        dto.setPageNumber(1);
        dto.setPageSize(20);
        dto.setNumberOfElements(1);
        dto.setTotalPages(1);
        dto.setRatingAvg(new BigDecimal("4.33"));
        dto.setRatingCount(3);
        dto.setRatingBreakdown(Map.of("1", 0, "2", 0, "3", 0, "4", 2, "5", 1));

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
        // 契约 required 字段全部同层
        for (String field : new String[]{"data", "total_elements", "page_number", "page_size",
                "number_of_elements", "total_pages", "rating_avg", "rating_count", "rating_breakdown"}) {
            assertThat(node.has(field)).as("top-level field %s", field).isTrue();
        }
        // 无嵌套包装（备选「组合包装 {page:{...}}」已被否）
        assertThat(node.has("page")).isFalse();
        assertThat(node.get("rating_avg").decimalValue()).isEqualByComparingTo("4.33");
        // 行级字段 snake_case
        JsonNode row = node.get("data").get(0);
        assertThat(row.has("customer_name")).isTrue();
        assertThat(row.has("submitted_at")).isTrue();
        assertThat(row.has("product_id")).isTrue();
    }

    @Test
    @DisplayName("TC-REV-030 [P0]: AdminReviewListResponse——pending_count 与六分页字段同层平铺")
    void adminReviewListFlattened() throws Exception {
        AdminReviewListDTO dto = new AdminReviewListDTO();
        dto.setData(List.of());
        dto.setTotalElements(0L);
        dto.setPageNumber(1);
        dto.setPageSize(20);
        dto.setNumberOfElements(0);
        dto.setTotalPages(0);
        dto.setPendingCount(9L);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));
        assertThat(node.get("pending_count").asLong()).isEqualTo(9L);
        assertThat(node.has("total_elements")).isTrue();
        assertThat(node.has("page_number")).isTrue();
    }
}
