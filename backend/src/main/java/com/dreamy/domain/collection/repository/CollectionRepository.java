package com.dreamy.domain.collection.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.enums.CollectionStatus;
import com.dreamy.domain.collection.entity.Collection;
import com.dreamy.domain.collection.entity.CollectionTranslation;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 集合仓储（RM-CAT-060~070）。
 * L2 TRACE: catalog-data-detail §2 CollectionRepository / CollectionTranslationRepository。
 */
@Repository
public class CollectionRepository {

    private final CollectionMapper collectionMapper;
    private final CollectionTranslationMapper translationMapper;

    public CollectionRepository(CollectionMapper collectionMapper,
                                CollectionTranslationMapper translationMapper) {
        this.collectionMapper = collectionMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-CAT-060 listByGroupId —— idx_collection_group（groupId 可空=全量） */
    public List<Collection> listByGroupId(Long groupId) {
        LambdaQueryWrapper<Collection> qw = new LambdaQueryWrapper<>();
        if (groupId != null) {
            qw.eq(Collection::getCollectionGroupId, groupId);
        }
        qw.orderByAsc(Collection::getId);
        return collectionMapper.selectList(qw);
    }

    /** RM-CAT-061 findById */
    public Collection findById(Long id) {
        return id == null ? null : collectionMapper.selectById(id);
    }

    /** RM-CAT-062 listByIds —— collection_ids 存在性校验（V-CAT-034） */
    public List<Collection> listByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                .in(Collection::getId, ids));
    }

    /** RM-CAT-063 listEnabled —— 消费端 status=enabled（E-CAT-07） */
    public List<Collection> listEnabled(Long groupId) {
        LambdaQueryWrapper<Collection> qw = new LambdaQueryWrapper<Collection>()
                .eq(Collection::getStatus, CollectionStatus.ENABLED);
        if (groupId != null) {
            qw.eq(Collection::getCollectionGroupId, groupId);
        }
        qw.orderByAsc(Collection::getId);
        return collectionMapper.selectList(qw);
    }

    /**
     * RM-CAT-064 searchEnabledByName —— E-CAT-02 STEP-CAT-04 集合命中
     * （主表 name LIKE + 附表 label LIKE UNION，集合量级小不建 FULLTEXT）。
     */
    public List<Long> searchEnabledByName(String q, String locale) {
        Set<Long> collectionIds = new LinkedHashSet<>();
        // 主表 name LIKE
        List<Collection> byName = collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getStatus, CollectionStatus.ENABLED)
                .like(Collection::getName, q));
        for (Collection c : byName) {
            collectionIds.add(c.getId());
        }
        // 附表 label LIKE（locale=es/fr 时）
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<CollectionTranslation> byLabel = translationMapper.selectList(new LambdaQueryWrapper<CollectionTranslation>()
                    .eq(CollectionTranslation::getLocale, locale)
                    .like(CollectionTranslation::getLabel, q));
            if (!byLabel.isEmpty()) {
                List<Long> candidate = byLabel.stream().map(CollectionTranslation::getCollectionId).toList();
                // 仅 enabled 集合入结果
                for (Collection c : collectionMapper.selectList(new LambdaQueryWrapper<Collection>()
                        .in(Collection::getId, candidate)
                        .eq(Collection::getStatus, CollectionStatus.ENABLED))) {
                    collectionIds.add(c.getId());
                }
            }
        }
        return new ArrayList<>(collectionIds);
    }

    /** RM-CAT-065 countByGroupId —— 409506 guard */
    public long countByGroupId(Long groupId) {
        return collectionMapper.selectCount(new LambdaQueryWrapper<Collection>()
                .eq(Collection::getCollectionGroupId, groupId));
    }

    /** RM-CAT-066 insert */
    public void insert(Collection collection) {
        collectionMapper.insert(collection);
    }

    /** RM-CAT-067 update */
    public void update(Collection collection) {
        collectionMapper.updateById(collection);
    }

    /** RM-CAT-068 deleteById */
    public void deleteById(Long id) {
        collectionMapper.deleteById(id);
    }

    /** RM-CAT-069 listTranslationsByCollectionIds */
    public List<CollectionTranslation> listTranslationsByCollectionIds(java.util.Collection<Long> collectionIds) {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<CollectionTranslation>()
                .in(CollectionTranslation::getCollectionId, collectionIds));
    }

    /** RM-CAT-070 replaceTranslations —— 整单覆盖 */
    public void replaceTranslations(Long collectionId, List<CollectionTranslation> rows) {
        translationMapper.delete(new LambdaQueryWrapper<CollectionTranslation>().eq(CollectionTranslation::getCollectionId, collectionId));
        if (rows != null) {
            for (CollectionTranslation row : rows) {
                row.setCollectionId(collectionId);
                translationMapper.insert(row);
            }
        }
    }

    /** collection_translation 级联删除（TX-CAT-020） */
    public void deleteTranslationsByCollectionId(Long collectionId) {
        translationMapper.delete(new LambdaQueryWrapper<CollectionTranslation>().eq(CollectionTranslation::getCollectionId, collectionId));
    }
}
