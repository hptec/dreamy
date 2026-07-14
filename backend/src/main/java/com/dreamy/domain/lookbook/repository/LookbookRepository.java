package com.dreamy.domain.lookbook.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.lookbook.entity.Lookbook;
import com.dreamy.domain.lookbook.entity.LookbookProduct;
import com.dreamy.domain.lookbook.entity.LookbookTranslation;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lookbook 仓储（RM-MKT-060~074）。
 * L2 TRACE: marketing-data-detail §2 LookbookRepository / Translation / Product 三仓储合并封装。
 */
@Repository
public class LookbookRepository {

    private final LookbookMapper lookbookMapper;
    private final LookbookTranslationMapper translationMapper;
    private final LookbookProductMapper productMapper;

    public LookbookRepository(LookbookMapper lookbookMapper, LookbookTranslationMapper translationMapper,
                              LookbookProductMapper productMapper) {
        this.lookbookMapper = lookbookMapper;
        this.translationMapper = translationMapper;
        this.productMapper = productMapper;
    }

    /** RM-MKT-060 listStorePublished —— ORDER BY id DESC（E-MKT-06） */
    public List<Lookbook> listStorePublished() {
        return lookbookMapper.selectList(new LambdaQueryWrapper<Lookbook>()
                .eq(Lookbook::getStatus, PublishStatus.PUBLISHED)
                .orderByDesc(Lookbook::getId));
    }

    /** RM-MKT-061 findByIdPublished */
    public Lookbook findByIdPublished(Long id) {
        return lookbookMapper.selectOne(new LambdaQueryWrapper<Lookbook>()
                .eq(Lookbook::getId, id)
                .eq(Lookbook::getStatus, PublishStatus.PUBLISHED));
    }

    /** RM-MKT-062 listAdmin */
    public List<Lookbook> listAdmin(PublishStatus status) {
        LambdaQueryWrapper<Lookbook> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Lookbook::getStatus, status);
        }
        return lookbookMapper.selectList(qw.orderByDesc(Lookbook::getId));
    }

    /** RM-MKT-063 findById */
    public Lookbook findById(Long id) {
        return id == null ? null : lookbookMapper.selectById(id);
    }

    /** RM-MKT-064 insert */
    public void insert(Lookbook lookbook) {
        lookbookMapper.insert(lookbook);
    }

    /** RM-MKT-065 update */
    public void update(Lookbook lookbook) {
        lookbookMapper.updateById(lookbook);
    }

    /** RM-MKT-066 deleteById */
    public void deleteById(Long id) {
        lookbookMapper.deleteById(id);
    }

    /** RM-MKT-067 updateStatus */
    public void updateStatus(Long id, PublishStatus status) {
        lookbookMapper.update(null, new LambdaUpdateWrapper<Lookbook>()
                .eq(Lookbook::getId, id)
                .set(Lookbook::getStatus, status));
    }

    /** RM-MKT-068 listTranslationsByLookbookIds —— 批查防 N+1（NP-MKT-001） */
    public List<LookbookTranslation> listTranslationsByLookbookIds(Collection<Long> lookbookIds) {
        if (lookbookIds == null || lookbookIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<LookbookTranslation>()
                .in(LookbookTranslation::getLookbookId, lookbookIds));
    }

    /** RM-MKT-069 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long lookbookId, List<LookbookTranslation> rows) {
        deleteTranslationsByLookbookId(lookbookId);
        if (rows != null) {
            for (LookbookTranslation row : rows) {
                row.setLookbookId(lookbookId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-070 deleteTransByLookbookId */
    public void deleteTranslationsByLookbookId(Long lookbookId) {
        translationMapper.delete(new LambdaQueryWrapper<LookbookTranslation>()
                .eq(LookbookTranslation::getLookbookId, lookbookId));
    }

    /** RM-MKT-071 listProductIdsByLookbookId */
    public List<Long> listProductIdsByLookbookId(Long lookbookId) {
        return productMapper.selectList(new LambdaQueryWrapper<LookbookProduct>()
                        .eq(LookbookProduct::getLookbookId, lookbookId)
                        .orderByAsc(LookbookProduct::getId))
                .stream().map(LookbookProduct::getProductId).toList();
    }

    /** RM-MKT-072 listProductIdsByLookbookIds —— admin 列表件数批查防 N+1 */
    public Map<Long, List<Long>> listProductIdsByLookbookIds(Collection<Long> lookbookIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (lookbookIds == null || lookbookIds.isEmpty()) {
            return result;
        }
        for (LookbookProduct row : productMapper.selectList(new LambdaQueryWrapper<LookbookProduct>()
                .in(LookbookProduct::getLookbookId, lookbookIds)
                .orderByAsc(LookbookProduct::getId))) {
            result.computeIfAbsent(row.getLookbookId(), k -> new ArrayList<>()).add(row.getProductId());
        }
        return result;
    }

    /** RM-MKT-073 replaceProducts —— DELETE+批量 INSERT（整单覆盖，调用方已去重） */
    public void replaceProducts(Long lookbookId, List<Long> productIds) {
        deleteProductsByLookbookId(lookbookId);
        if (productIds != null) {
            for (Long productId : productIds) {
                LookbookProduct row = new LookbookProduct();
                row.setLookbookId(lookbookId);
                row.setProductId(productId);
                productMapper.insert(row);
            }
        }
    }

    /** RM-MKT-074 deleteProductsByLookbookId */
    public void deleteProductsByLookbookId(Long lookbookId) {
        productMapper.delete(new LambdaQueryWrapper<LookbookProduct>()
                .eq(LookbookProduct::getLookbookId, lookbookId));
    }
}
