package com.dreamy.domain.wishlist.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.wishlist.entity.WishlistItem;
import org.apache.ibatis.annotations.Mapper;

/** WishlistItemMapper。表 wishlist_item。 */
@Mapper
public interface WishlistItemMapper extends BaseMapper<WishlistItem> {
}
