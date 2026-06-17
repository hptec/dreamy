package com.dreamy.domain.collection.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionGroup;
import com.dreamy.domain.collection.entity.CollectionGroupTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 集合分组仓储（RM-CAT-050~056）。
 * L2 TRACE: catalog-data-detail §2 CollectionGroupRepository / CollectionGroupTranslationRepository。
 */
@Repository
public class CollectionGroupRepository {

    private final CollectionGroupMapper groupMapper;
    private final CollectionGroupTranslationMapper translationMapper;
    private final CollectionMapper collectionMapper;

    public CollectionGroupRepository(CollectionGroupMapper groupMapper,
                                     CollectionGroupTranslationMapper translationMapper,
                                     CollectionMapper collectionMapper) {
        this.groupMapper = groupMapper;
        this.translationMapper = translationMapper;
        this.collectionMapper = collectionMapper;
    }

    /** RM-CAT-050 listAll */
    public List<CollectionGroup> listAll() {
        return groupMapper.selectList(new LambdaQueryWrapper<CollectionGroup>()
                .isNull(CollectionGroup::getDeletedAt)
                .orderByAsc(CollectionGroup::getId));
    }

    /** RM-CAT-051 findById */
    public CollectionGroup findById(Long id) {
        CollectionGroup e = id == null ? null : groupMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-CAT-052 insert */
    public void insert(CollectionGroup group) {
        groupMapper.insert(group);
    }

    /** RM-CAT-053 update */
    public void update(CollectionGroup group) {
        groupMapper.updateById(group);
    }

    /** RM-CAT-054 deleteById（含 translation 级联，TX-CAT-017） */
    public void deleteById(Long id) {
        groupMapper.deleteById(id);
        translationMapper.delete(new LambdaQueryWrapper<CollectionGroupTranslation>()
                .eq(CollectionGroupTranslation::getCollectionGroupId, id));
    }

    /** RM-CAT-055 listTranslationsByGroupIds */
    public List<CollectionGroupTranslation> listTranslationsByGroupIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<CollectionGroupTranslation>()
                .in(CollectionGroupTranslation::getCollectionGroupId, ids));
    }

    /** RM-CAT-056 replaceTranslations —— 整单覆盖 */
    public void replaceTranslations(Long groupId, List<CollectionGroupTranslation> rows) {
        translationMapper.delete(new LambdaQueryWrapper<CollectionGroupTranslation>()
                .eq(CollectionGroupTranslation::getCollectionGroupId, groupId));
        if (rows != null) {
            for (CollectionGroupTranslation row : rows) {
                row.setCollectionGroupId(groupId);
                translationMapper.insert(row);
            }
        }
    }

    /** 分组批量 collection_count 派生（E-CAT-27 STEP-CAT-01，NP-CAT-002 单次查询分组计数） */
    public Map<Long, Long> countCollectionsGroupByGroup() {
        List<Collection> all = collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                .select(Collection::getId, Collection::getCollectionGroupId));
        Map<Long, Long> counts = new HashMap<>();
        for (Collection c : all) {
            counts.merge(c.getCollectionGroupId(), 1L, Long::sum);
        }
        return counts;
    }
}
