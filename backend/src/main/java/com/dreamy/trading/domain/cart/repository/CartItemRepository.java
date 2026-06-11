package com.dreamy.trading.domain.cart.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.trading.domain.cart.entity.CartItem;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 购物车仓储（RM-TRD-001~007）。
 * L2 TRACE: trading-data-detail §1 CartItemRepository / IDX-TRD-014/015。
 */
@Repository
public class CartItemRepository {

    private final CartItemMapper mapper;

    public CartItemRepository(CartItemMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-001 listByCustomerId（id DESC 展示序） */
    public List<CartItem> listByCustomerId(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getCustomerId, customerId)
                .orderByDesc(CartItem::getId));
    }

    /** RM-TRD-002 隔离点查（404603 防探测） */
    public CartItem findByIdAndCustomerId(Long id, Long customerId) {
        return mapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .eq(CartItem::getCustomerId, customerId));
    }

    /** RM-TRD-003 合并判定：同 SKU（现货）或同定制哈希（定制行，custom_size_hash 比较） */
    public CartItem findMergeTarget(Long customerId, Long skuId, String customSizeHash) {
        LambdaQueryWrapper<CartItem> qw = new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getCustomerId, customerId);
        if (skuId != null) {
            qw.eq(CartItem::getSkuId, skuId);
        } else {
            qw.isNull(CartItem::getSkuId).eq(CartItem::getCustomSizeHash, customSizeHash);
        }
        List<CartItem> matches = mapper.selectList(qw.orderByAsc(CartItem::getId));
        return matches.isEmpty() ? null : matches.get(0);
    }

    /** RM-TRD-004 insert */
    public void insert(CartItem item) {
        mapper.insert(item);
    }

    /** RM-TRD-005 updateQty */
    public void updateQty(Long id, int qty) {
        mapper.update(null, new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .set(CartItem::getQty, qty));
    }

    /** RM-TRD-006 deleteByIdAndCustomerId（affected=0 → 404603） */
    public int deleteByIdAndCustomerId(Long id, Long customerId) {
        return mapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .eq(CartItem::getCustomerId, customerId));
    }

    /** RM-TRD-007 清车（TX-TRD-001 第 5 步） */
    public void deleteAllByCustomerId(Long customerId) {
        mapper.delete(new LambdaQueryWrapper<CartItem>().eq(CartItem::getCustomerId, customerId));
    }
}
