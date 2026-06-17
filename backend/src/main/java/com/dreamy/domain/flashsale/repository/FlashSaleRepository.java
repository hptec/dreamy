package com.dreamy.domain.flashsale.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.enums.FlashSaleStatus;
import com.dreamy.domain.flashsale.entity.FlashSale;
import com.dreamy.domain.flashsale.entity.FlashSaleProduct;
import com.dreamy.domain.flashsale.entity.FlashSaleTranslation;
import com.dreamy.support.PromoWindow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 闪购仓储（RM-MKT-120~133）。
 * L2 TRACE: marketing-data-detail §2 FlashSaleRepository / Translation / Product 三仓储合并封装。
 */
@Repository
public class FlashSaleRepository {

    private final FlashSaleMapper flashSaleMapper;
    private final FlashSaleTranslationMapper translationMapper;
    private final FlashSaleProductMapper productMapper;

    public FlashSaleRepository(FlashSaleMapper flashSaleMapper, FlashSaleTranslationMapper translationMapper,
                               FlashSaleProductMapper productMapper) {
        this.flashSaleMapper = flashSaleMapper;
        this.translationMapper = translationMapper;
        this.productMapper = productMapper;
    }

    /** SCHED-MKT-01③ 翻转结果（任一非空触发失效链 + MQ flash_sale_changed） */
    public record FlipResult(List<Long> activated, List<Long> ended) {
        public boolean hasChanges() {
            return !activated.isEmpty() || !ended.isEmpty();
        }
    }

    /** RM-MKT-120 listStoreActive —— status='active' ORDER BY end_at ASC（IDX-MKT-003，DEC-MKT-3 以状态列为准） */
    public List<FlashSale> listStoreActive() {
        return flashSaleMapper.selectList(new LambdaQueryWrapper<FlashSale>()
                .isNull(FlashSale::getDeletedAt)
                .eq(FlashSale::getStatus, FlashSaleStatus.ACTIVE)
                .orderByAsc(FlashSale::getEndAt));
    }

    /** RM-MKT-121 listAdmin —— ORDER BY start_at DESC, id DESC（E-MKT-17） */
    public List<FlashSale> listAdmin(FlashSaleStatus status) {
        LambdaQueryWrapper<FlashSale> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(FlashSale::getStatus, status);
        }
        return flashSaleMapper.selectList(qw.orderByDesc(FlashSale::getStartAt).orderByDesc(FlashSale::getId));
    }

    /** RM-MKT-122 findById */
    public FlashSale findById(Long id) {
        FlashSale e = id == null ? null : flashSaleMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-MKT-123 insert */
    public void insert(FlashSale flashSale) {
        flashSaleMapper.insert(flashSale);
    }

    /** RM-MKT-124 update */
    public void update(FlashSale flashSale) {
        flashSaleMapper.updateById(flashSale);
    }

    /** RM-MKT-125 deleteById */
    public void deleteById(Long id) {
        flashSaleMapper.deleteById(id);
    }

    /**
     * RM-MKT-126 flipStatusByWindow —— SCHED-MKT-01③ 两段翻转：scheduled→active（start_at≤now）、
     * active→ended（end_at<now，s-761 自动下线；目标态判定收敛于 PromoWindow 纯函数）；
     * 逐行 status CAS（WHERE status=旧值）防与管理端编辑并发竞争。
     */
    public FlipResult flipStatusByWindow(LocalDateTime now) {
        List<FlashSale> candidates = flashSaleMapper.selectList(new LambdaQueryWrapper<FlashSale>()
                .isNull(FlashSale::getDeletedAt)
                .in(FlashSale::getStatus, FlashSaleStatus.SCHEDULED, FlashSaleStatus.ACTIVE));
        List<Long> activated = new ArrayList<>();
        List<Long> ended = new ArrayList<>();
        for (FlashSale sale : candidates) {
            FlashSaleStatus target = PromoWindow.flashTarget(sale.getStatus(), sale.getStartAt(),
                    sale.getEndAt(), now);
            if (target == null || target == sale.getStatus()) {
                continue;
            }
            int affected = flashSaleMapper.update(null, new LambdaUpdateWrapper<FlashSale>()
                    .eq(FlashSale::getId, sale.getId())
                    .eq(FlashSale::getStatus, sale.getStatus())
                    .set(FlashSale::getStatus, target));
            if (affected > 0) {
                (target == FlashSaleStatus.ACTIVE ? activated : ended).add(sale.getId());
            }
        }
        return new FlipResult(activated, ended);
    }

    /** RM-MKT-127 listTranslationsByFlashIds —— 批查防 N+1（NP-MKT-001） */
    public List<FlashSaleTranslation> listTranslationsByFlashIds(Collection<Long> flashIds) {
        if (flashIds == null || flashIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<FlashSaleTranslation>()
                .in(FlashSaleTranslation::getFlashSaleId, flashIds));
    }

    /** RM-MKT-128 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long flashId, List<FlashSaleTranslation> rows) {
        deleteTranslationsByFlashId(flashId);
        if (rows != null) {
            for (FlashSaleTranslation row : rows) {
                row.setFlashSaleId(flashId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-129 deleteTransByFlashId */
    public void deleteTranslationsByFlashId(Long flashId) {
        translationMapper.delete(new LambdaQueryWrapper<FlashSaleTranslation>()
                .eq(FlashSaleTranslation::getFlashSaleId, flashId));
    }

    /** RM-MKT-130 listProductIdsByFlashId */
    public List<Long> listProductIdsByFlashId(Long flashId) {
        return productMapper.selectList(new LambdaQueryWrapper<FlashSaleProduct>()
                        .eq(FlashSaleProduct::getFlashSaleId, flashId)
                        .orderByAsc(FlashSaleProduct::getId))
                .stream().map(FlashSaleProduct::getProductId).toList();
    }

    /** RM-MKT-131 listProductIdsByFlashIds —— 批查防 N+1 */
    public Map<Long, List<Long>> listProductIdsByFlashIds(Collection<Long> flashIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (flashIds == null || flashIds.isEmpty()) {
            return result;
        }
        for (FlashSaleProduct row : productMapper.selectList(new LambdaQueryWrapper<FlashSaleProduct>()
                .in(FlashSaleProduct::getFlashSaleId, flashIds)
                .orderByAsc(FlashSaleProduct::getId))) {
            result.computeIfAbsent(row.getFlashSaleId(), k -> new ArrayList<>()).add(row.getProductId());
        }
        return result;
    }

    /** RM-MKT-132 replaceProducts —— DELETE+批量 INSERT（整单覆盖，调用方已去重） */
    public void replaceProducts(Long flashId, List<Long> productIds) {
        deleteProductsByFlashId(flashId);
        if (productIds != null) {
            for (Long productId : productIds) {
                FlashSaleProduct row = new FlashSaleProduct();
                row.setFlashSaleId(flashId);
                row.setProductId(productId);
                productMapper.insert(row);
            }
        }
    }

    /** RM-MKT-133 deleteProductsByFlashId */
    public void deleteProductsByFlashId(Long flashId) {
        productMapper.delete(new LambdaQueryWrapper<FlashSaleProduct>()
                .eq(FlashSaleProduct::getFlashSaleId, flashId));
    }
}
