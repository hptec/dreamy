package com.dreamy.domain.cart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;

/** CartItemMapper。表 cart_item。 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
