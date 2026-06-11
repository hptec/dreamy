package com.dreamy.trading.domain.browse.service;

import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.trading.domain.browse.entity.BrowseHistory;
import com.dreamy.trading.domain.browse.repository.BrowseHistoryRepository;
import com.dreamy.trading.dto.TradingDtos.BrowseHistoryItemDto;
import com.dreamy.trading.error.TradingException;
import com.dreamy.trading.port.CatalogSnapshotPort;
import com.dreamy.trading.port.CatalogSnapshotPort.ProductBrief;
import com.dreamy.trading.support.FieldErrors;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 浏览历史服务（trading-api-detail §7，FLOW-P13，决策 23；derived_scope host TASK-051）。
 * upsert 幂等（uk_browse_customer_product）+ 每用户 50 条滚动清理（写时清理 RM-TRD-072）。
 */
@Service
public class BrowseHistoryService {

    static final int MAX_KEEP = 50;

    private final BrowseHistoryRepository browseHistoryRepository;
    private final CatalogSnapshotPort catalogSnapshotPort;

    public BrowseHistoryService(BrowseHistoryRepository browseHistoryRepository,
                                CatalogSnapshotPort catalogSnapshotPort) {
        this.browseHistoryRepository = browseHistoryRepository;
        this.catalogSnapshotPort = catalogSnapshotPort;
    }

    /** E-listBrowseHistory（V-TRD-041：limit 1..50 缺省 20） */
    public List<BrowseHistoryItemDto> list(Long customerId, Integer limit, String locale) {
        FieldErrors errors = new FieldErrors();
        int parsedLimit = 20;
        if (limit != null) {
            if (limit < 1 || limit > MAX_KEEP) {
                errors.reject("limit", "range_invalid");
            } else {
                parsedLimit = limit;
            }
        }
        errors.throwIfAny();
        List<BrowseHistory> rows = browseHistoryRepository.listRecent(customerId, parsedLimit);
        Map<Long, ProductBrief> products = catalogSnapshotPort.getProductBriefs(
                rows.stream().map(BrowseHistory::getProductId).toList(), locale);
        return rows.stream()
                .map(row -> new BrowseHistoryItemDto(row.getId(), row.getProductId(), row.getViewedAt(),
                        products.get(row.getProductId())))
                .toList();
    }

    /** E-recordBrowseHistory（V-TRD-042 + STEP-TRD-01/02：upsert + 滚动清理） */
    public void record(Long customerId, Long productId) {
        if (productId == null) {
            throw TradingException.fieldValidation("product_id", "required");
        }
        ProductBrief product = catalogSnapshotPort.getProductBrief(productId, "en");
        if (product == null || !product.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        browseHistoryRepository.upsertViewedAt(customerId, productId, LocalDateTime.now());
        browseHistoryRepository.trimToLatest(customerId, MAX_KEEP);
    }
}
