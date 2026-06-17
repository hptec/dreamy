package com.dreamy.domain.wedding.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.wedding.entity.RealWedding;
import com.dreamy.domain.wedding.entity.RealWeddingProduct;
import com.dreamy.domain.wedding.entity.RealWeddingTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 婚礼案例仓储（RM-MKT-040~054）。
 * L2 TRACE: marketing-data-detail §2 RealWeddingRepository / Translation / Product 三仓储合并封装。
 */
@Repository
public class RealWeddingRepository {

    private final RealWeddingMapper weddingMapper;
    private final RealWeddingTranslationMapper translationMapper;
    private final RealWeddingProductMapper productMapper;

    public RealWeddingRepository(RealWeddingMapper weddingMapper, RealWeddingTranslationMapper translationMapper,
                                 RealWeddingProductMapper productMapper) {
        this.weddingMapper = weddingMapper;
        this.translationMapper = translationMapper;
        this.productMapper = productMapper;
    }

    /** RM-MKT-040 pageStorePublished —— status='published' ORDER BY wedding_date DESC, id DESC（IDX-MKT-008） */
    public Page<RealWedding> pageStorePublished(int page, int pageSize) {
        return weddingMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<RealWedding>()
                .isNull(RealWedding::getDeletedAt)
                .eq(RealWedding::getStatus, PublishStatus.PUBLISHED)
                .orderByDesc(RealWedding::getWeddingDate)
                .orderByDesc(RealWedding::getId));
    }

    /** RM-MKT-041 findByIdPublished */
    public RealWedding findByIdPublished(Long id) {
        return weddingMapper.selectOne(new LambdaQueryWrapper<RealWedding>()
                .isNull(RealWedding::getDeletedAt)
                .eq(RealWedding::getId, id)
                .eq(RealWedding::getStatus, PublishStatus.PUBLISHED));
    }

    /** RM-MKT-042 pageAdmin —— ORDER BY id DESC（E-MKT-32） */
    public Page<RealWedding> pageAdmin(PublishStatus status, int page, int pageSize) {
        LambdaQueryWrapper<RealWedding> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(RealWedding::getStatus, status);
        }
        qw.orderByDesc(RealWedding::getId);
        return weddingMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-MKT-043 findById */
    public RealWedding findById(Long id) {
        RealWedding e = id == null ? null : weddingMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-MKT-044 insert */
    public void insert(RealWedding wedding) {
        weddingMapper.insert(wedding);
    }

    /** RM-MKT-045 update */
    public void update(RealWedding wedding) {
        weddingMapper.updateById(wedding);
    }

    /** RM-MKT-046 deleteById */
    public void deleteById(Long id) {
        weddingMapper.deleteById(id);
    }

    /** RM-MKT-047 updateStatus */
    public void updateStatus(Long id, PublishStatus status) {
        weddingMapper.update(null, new LambdaUpdateWrapper<RealWedding>()
                .eq(RealWedding::getId, id)
                .set(RealWedding::getStatus, status));
    }

    /** RM-MKT-048 listTranslationsByWeddingIds —— 批查防 N+1（NP-MKT-001） */
    public List<RealWeddingTranslation> listTranslationsByWeddingIds(Collection<Long> weddingIds) {
        if (weddingIds == null || weddingIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<RealWeddingTranslation>()
                .in(RealWeddingTranslation::getRealWeddingId, weddingIds));
    }

    /** RM-MKT-049 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long weddingId, List<RealWeddingTranslation> rows) {
        deleteTranslationsByWeddingId(weddingId);
        if (rows != null) {
            for (RealWeddingTranslation row : rows) {
                row.setRealWeddingId(weddingId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-050 deleteTransByWeddingId */
    public void deleteTranslationsByWeddingId(Long weddingId) {
        translationMapper.delete(new LambdaQueryWrapper<RealWeddingTranslation>()
                .eq(RealWeddingTranslation::getRealWeddingId, weddingId));
    }

    /** RM-MKT-051 listProductIdsByWeddingId */
    public List<Long> listProductIdsByWeddingId(Long weddingId) {
        return productMapper.selectList(new LambdaQueryWrapper<RealWeddingProduct>()
                        .eq(RealWeddingProduct::getRealWeddingId, weddingId)
                        .orderByAsc(RealWeddingProduct::getId))
                .stream().map(RealWeddingProduct::getProductId).toList();
    }

    /** RM-MKT-052 listProductIdsByWeddingIds —— admin 列表件数批查防 N+1 */
    public Map<Long, List<Long>> listProductIdsByWeddingIds(Collection<Long> weddingIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (weddingIds == null || weddingIds.isEmpty()) {
            return result;
        }
        for (RealWeddingProduct row : productMapper.selectList(new LambdaQueryWrapper<RealWeddingProduct>()
                .in(RealWeddingProduct::getRealWeddingId, weddingIds)
                .orderByAsc(RealWeddingProduct::getId))) {
            result.computeIfAbsent(row.getRealWeddingId(), k -> new java.util.ArrayList<>()).add(row.getProductId());
        }
        return result;
    }

    /** RM-MKT-053 replaceProducts —— DELETE+批量 INSERT（整单覆盖，调用方已去重） */
    public void replaceProducts(Long weddingId, List<Long> productIds) {
        deleteProductsByWeddingId(weddingId);
        if (productIds != null) {
            for (Long productId : productIds) {
                RealWeddingProduct row = new RealWeddingProduct();
                row.setRealWeddingId(weddingId);
                row.setProductId(productId);
                productMapper.insert(row);
            }
        }
    }

    /** RM-MKT-054 deleteProductsByWeddingId */
    public void deleteProductsByWeddingId(Long weddingId) {
        productMapper.delete(new LambdaQueryWrapper<RealWeddingProduct>()
                .eq(RealWeddingProduct::getRealWeddingId, weddingId));
    }
}
