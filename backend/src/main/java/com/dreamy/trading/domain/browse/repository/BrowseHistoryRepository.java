package com.dreamy.trading.domain.browse.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.trading.domain.browse.entity.BrowseHistory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 浏览历史仓储（RM-TRD-070~072；uk_browse_customer_product upsert 幂等，决策 23）。
 */
@Repository
public class BrowseHistoryRepository {

    private final BrowseHistoryMapper mapper;

    public BrowseHistoryRepository(BrowseHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-070 upsertViewedAt（INSERT 唯一冲突 → UPDATE viewed_at，js_guard 重复浏览） */
    public void upsertViewedAt(Long customerId, Long productId, LocalDateTime now) {
        BrowseHistory row = new BrowseHistory();
        row.setCustomerId(customerId);
        row.setProductId(productId);
        row.setViewedAt(now);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            mapper.update(null, new LambdaUpdateWrapper<BrowseHistory>()
                    .eq(BrowseHistory::getCustomerId, customerId)
                    .eq(BrowseHistory::getProductId, productId)
                    .set(BrowseHistory::getViewedAt, now));
        }
    }

    /** RM-TRD-071 listRecent（idx_browse_customer_viewed，viewed_at DESC） */
    public List<BrowseHistory> listRecent(Long customerId, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getCustomerId, customerId)
                .orderByDesc(BrowseHistory::getViewedAt)
                .orderByDesc(BrowseHistory::getId)
                .last("LIMIT " + limit));
    }

    /** RM-TRD-072 trimToLatest：删除排名 keep 之后最旧行（recordBrowseHistory.STEP-TRD-02 滚动清理） */
    public int trimToLatest(Long customerId, int keep) {
        List<BrowseHistory> keepRows = listRecent(customerId, keep);
        if (keepRows.size() < keep) {
            return 0;
        }
        List<Long> keepIds = keepRows.stream().map(BrowseHistory::getId).toList();
        return mapper.delete(new LambdaQueryWrapper<BrowseHistory>()
                .eq(BrowseHistory::getCustomerId, customerId)
                .notIn(BrowseHistory::getId, keepIds));
    }
}
