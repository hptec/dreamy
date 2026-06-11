package com.dreamy.showroom.domain.showroom.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 收藏款式仓储（RM-SHR-020~025）。
 * L2 TRACE: showroom-data-detail §2 ShowroomItemRepository / IDX-SHR-003/004。
 */
@Repository
public class ShowroomItemRepository {

    private final ShowroomItemMapper itemMapper;

    public ShowroomItemRepository(ShowroomItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /** RM-SHR-020 insert —— uk_si_room_product_color 冲突向上抛（调用方映射 409102，E-SHR-08） */
    public void insert(ShowroomItem item) {
        itemMapper.insert(item);
    }

    /** RM-SHR-021 listByShowroom —— E-SHR-03（uk 左前缀覆盖） */
    public List<ShowroomItem> listByShowroom(Long showroomId) {
        return itemMapper.selectList(new LambdaQueryWrapper<ShowroomItem>()
                .eq(ShowroomItem::getShowroomId, showroomId)
                .orderByAsc(ShowroomItem::getId));
    }

    /** RM-SHR-022 findByIdAndShowroom —— 归属校验（404102，E-SHR-09/10/11/12，CV-SHR-006 双键点查） */
    public ShowroomItem findByIdAndShowroom(Long itemId, Long showroomId) {
        if (itemId == null || showroomId == null) {
            return null;
        }
        return itemMapper.selectOne(new LambdaQueryWrapper<ShowroomItem>()
                .eq(ShowroomItem::getId, itemId)
                .eq(ShowroomItem::getShowroomId, showroomId));
    }

    /** RM-SHR-023 deleteById —— 级联（E-SHR-09） */
    public void deleteById(Long itemId) {
        itemMapper.deleteById(itemId);
    }

    /** RM-SHR-023 deleteByShowroom —— 级联（E-SHR-05 / TX-SHR-003） */
    public void deleteByShowroom(Long showroomId) {
        itemMapper.delete(new LambdaQueryWrapper<ShowroomItem>()
                .eq(ShowroomItem::getShowroomId, showroomId));
    }

    /** 批量取行（EVT-SHR-003 消费侧 assigned_item product 判定） */
    public List<ShowroomItem> listByIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<ShowroomItem>().in(ShowroomItem::getId, itemIds));
    }

    /**
     * RM-SHR-024 touchLastOrdered —— dye lot 窗口回写（EVT-SHR-003 ③，覆盖写可重入）。
     */
    public int touchLastOrdered(Collection<Long> showroomIds, Collection<Long> productIds, LocalDateTime ts) {
        if (showroomIds == null || showroomIds.isEmpty() || productIds == null || productIds.isEmpty()) {
            return 0;
        }
        return itemMapper.update(null, new LambdaUpdateWrapper<ShowroomItem>()
                .in(ShowroomItem::getShowroomId, showroomIds)
                .in(ShowroomItem::getProductId, productIds)
                .set(ShowroomItem::getLastOrderedAt, ts));
    }

    /**
     * RM-SHR-025 selectDyeLotProductIds —— dye lot 窗口命中（DyeLotPort 实现，IDX-SHR-004）。
     */
    public List<Long> selectDyeLotProductIds(Collection<Long> showroomIds, Collection<Long> productIds,
                                             LocalDateTime windowStart) {
        if (showroomIds == null || showroomIds.isEmpty() || productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<ShowroomItem>()
                        .select(ShowroomItem::getProductId)
                        .in(ShowroomItem::getShowroomId, showroomIds)
                        .in(ShowroomItem::getProductId, productIds)
                        .gt(ShowroomItem::getLastOrderedAt, windowStart))
                .stream()
                .map(ShowroomItem::getProductId)
                .distinct()
                .toList();
    }
}
