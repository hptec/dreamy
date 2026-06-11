package com.dreamy.trading.domain.cart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.trading.domain.cart.entity.CartMergeRecord;
import org.apache.ibatis.annotations.Mapper;

/** CartMergeRecordMapper。表 cart_merge_record。 */
@Mapper
public interface CartMergeRecordMapper extends BaseMapper<CartMergeRecord> {
}
