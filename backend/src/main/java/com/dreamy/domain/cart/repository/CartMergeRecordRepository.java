package com.dreamy.domain.cart.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.cart.entity.CartMergeRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 合并幂等记录仓储（RM-TRD-008 insertIgnore + SCHED-TRD-003 清理）。
 * insertIgnore 以唯一索引冲突捕获实现（uk_merge_customer_token；InnoDB 冲突不毒化事务）。
 */
@Repository
public class CartMergeRecordRepository {

    private final CartMergeRecordMapper mapper;

    public CartMergeRecordRepository(CartMergeRecordMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-008 insertIgnore：affected=0 即已合并（TX-TRD-007 幂等闸） */
    public int insertIgnore(Long customerId, String anonToken) {
        CartMergeRecord record = new CartMergeRecord();
        record.setCustomerId(customerId);
        record.setAnonToken(anonToken);
        try {
            return mapper.insert(record);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    /** SCHED-TRD-003：删除 created_at < cutoff 记录 */
    public int deleteBefore(LocalDateTime cutoff) {
        return mapper.delete(new LambdaQueryWrapper<CartMergeRecord>()
                .lt(CartMergeRecord::getCreatedAt, cutoff));
    }
}
