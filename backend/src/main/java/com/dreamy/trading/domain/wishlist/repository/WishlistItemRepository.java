package com.dreamy.trading.domain.wishlist.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.trading.domain.wishlist.entity.WishlistItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 收藏仓储（RM-TRD-060~063；uk_wishlist_customer_product 幂等，决策 18）。
 */
@Repository
public class WishlistItemRepository {

    private final WishlistItemMapper mapper;

    public WishlistItemRepository(WishlistItemMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-060 listByCustomerId（id DESC） */
    public List<WishlistItem> listByCustomerId(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<WishlistItem>()
                .eq(WishlistItem::getCustomerId, customerId)
                .orderByDesc(WishlistItem::getId));
    }

    /** RM-TRD-061 insertIgnore：affected=0 → 幂等 200（addWishlistItem.STEP-TRD-02） */
    public int insertIgnore(Long customerId, Long productId) {
        WishlistItem item = new WishlistItem();
        item.setCustomerId(customerId);
        item.setProductId(productId);
        try {
            return mapper.insert(item);
        } catch (DuplicateKeyException ex) {
            return 0;
        }
    }

    /** RM-TRD-062 findByCustomerAndProduct */
    public WishlistItem findByCustomerAndProduct(Long customerId, Long productId) {
        return mapper.selectOne(new LambdaQueryWrapper<WishlistItem>()
                .eq(WishlistItem::getCustomerId, customerId)
                .eq(WishlistItem::getProductId, productId));
    }

    /** RM-TRD-063 deleteByCustomerAndProduct（affected=0 → 404604） */
    public int deleteByCustomerAndProduct(Long customerId, Long productId) {
        return mapper.delete(new LambdaQueryWrapper<WishlistItem>()
                .eq(WishlistItem::getCustomerId, customerId)
                .eq(WishlistItem::getProductId, productId));
    }
}
