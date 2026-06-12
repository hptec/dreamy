package com.dreamy.port;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.product.consts.SkuDBConst;
import com.dreamy.domain.product.entity.Sku;
import com.dreamy.domain.product.repository.SkuMapper;
import org.springframework.stereotype.Component;

/**
 * SKU 库存端口适配（RM-TRD-110/111，决策 3/6：经 catalog SkuMapper 在本域事务内执行 CAS SQL，
 * 事务传播 REQUIRED 加入 TX-TRD-001/003/005；CP-016 setSql 手工版本递增，不挂 @Version）。
 * - deduct：`UPDATE sku SET stock=stock-:qty, version=version+1 WHERE id=? AND version=? AND stock>=:qty`
 *   affected=0 → 调用方重读重试 ×3（createOrder.STEP-TRD-05 ③），仍失败 → 409601 整体回滚。
 * - restock：`UPDATE sku SET stock=stock+:qty, version=version+1 WHERE id=?`（回补无条件累加）。
 */
@Component
public class SkuStockAdapter {

    private final SkuMapper skuMapper;

    public SkuStockAdapter(SkuMapper skuMapper) {
        this.skuMapper = skuMapper;
    }

    /** RM-TRD-110 乐观锁扣减（qty 为已校验正整数，setSql 拼接安全） */
    public int deduct(Long skuId, int qty, Long expectedVersion) {
        return skuMapper.update(null, new LambdaUpdateWrapper<Sku>()
                .eq(Sku::getId, skuId)
                .eq(Sku::getVersion, expectedVersion)
                .ge(Sku::getStock, qty)
                .setSql(SkuDBConst.STOCK + " = " + SkuDBConst.STOCK + " - " + qty)
                .setSql(SkuDBConst.VERSION + " = " + SkuDBConst.VERSION + " + 1"));
    }

    /** RM-TRD-111 回补（无条件累加） */
    public int restock(Long skuId, int qty) {
        return skuMapper.update(null, new LambdaUpdateWrapper<Sku>()
                .eq(Sku::getId, skuId)
                .setSql(SkuDBConst.STOCK + " = " + SkuDBConst.STOCK + " + " + qty)
                .setSql(SkuDBConst.VERSION + " = " + SkuDBConst.VERSION + " + 1"));
    }

    /** CAS 重读（version/stock 最新值） */
    public Sku reload(Long skuId) {
        return skuMapper.selectById(skuId);
    }
}
