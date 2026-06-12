package com.dreamy.domain.tag.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.tag.entity.Tag;
import com.dreamy.domain.tag.entity.TagDimension;
import com.dreamy.domain.tag.entity.TagDimensionTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签维度仓储（RM-CAT-050~056）。
 * L2 TRACE: catalog-data-detail §2 TagDimensionRepository / TagDimensionTranslationRepository。
 */
@Repository
public class TagDimensionRepository {

    private final TagDimensionMapper dimensionMapper;
    private final TagDimensionTranslationMapper translationMapper;
    private final TagMapper tagMapper;

    public TagDimensionRepository(TagDimensionMapper dimensionMapper,
                                  TagDimensionTranslationMapper translationMapper,
                                  TagMapper tagMapper) {
        this.dimensionMapper = dimensionMapper;
        this.translationMapper = translationMapper;
        this.tagMapper = tagMapper;
    }

    /** RM-CAT-050 listAll */
    public List<TagDimension> listAll() {
        return dimensionMapper.selectList(new LambdaQueryWrapper<TagDimension>().orderByAsc(TagDimension::getId));
    }

    /** RM-CAT-051 findById */
    public TagDimension findById(Long id) {
        return id == null ? null : dimensionMapper.selectById(id);
    }

    /** RM-CAT-052 insert */
    public void insert(TagDimension dimension) {
        dimensionMapper.insert(dimension);
    }

    /** RM-CAT-053 update */
    public void update(TagDimension dimension) {
        dimensionMapper.updateById(dimension);
    }

    /** RM-CAT-054 deleteById（含 translation 级联，TX-CAT-017） */
    public void deleteById(Long id) {
        dimensionMapper.deleteById(id);
        translationMapper.delete(new LambdaQueryWrapper<TagDimensionTranslation>()
                .eq(TagDimensionTranslation::getTagDimensionId, id));
    }

    /** RM-CAT-055 listTranslationsByDimensionIds */
    public List<TagDimensionTranslation> listTranslationsByDimensionIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<TagDimensionTranslation>()
                .in(TagDimensionTranslation::getTagDimensionId, ids));
    }

    /** RM-CAT-056 replaceTranslations —— 整单覆盖 */
    public void replaceTranslations(Long dimensionId, List<TagDimensionTranslation> rows) {
        translationMapper.delete(new LambdaQueryWrapper<TagDimensionTranslation>()
                .eq(TagDimensionTranslation::getTagDimensionId, dimensionId));
        if (rows != null) {
            for (TagDimensionTranslation row : rows) {
                row.setTagDimensionId(dimensionId);
                translationMapper.insert(row);
            }
        }
    }

    /** 维度批量 tag_count 派生（E-CAT-27 STEP-CAT-01，NP-CAT-002 单次查询分组计数） */
    public Map<Long, Long> countTagsGroupByDimension() {
        List<Tag> all = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .select(Tag::getId, Tag::getDimensionId));
        Map<Long, Long> counts = new HashMap<>();
        for (Tag t : all) {
            counts.merge(t.getDimensionId(), 1L, Long::sum);
        }
        return counts;
    }
}
