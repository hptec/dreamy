package com.dreamy.catalog.domain.tag.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.catalog.domain.enums.TagStatus;
import com.dreamy.catalog.domain.tag.entity.Tag;
import com.dreamy.catalog.domain.tag.entity.TagTranslation;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 标签仓储（RM-CAT-060~070）。
 * L2 TRACE: catalog-data-detail §2 TagRepository / TagTranslationRepository。
 */
@Repository
public class TagRepository {

    private final TagMapper tagMapper;
    private final TagTranslationMapper translationMapper;

    public TagRepository(TagMapper tagMapper, TagTranslationMapper translationMapper) {
        this.tagMapper = tagMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-CAT-060 listByDimensionId —— idx_tag_dimension（dimensionId 可空=全量） */
    public List<Tag> listByDimensionId(Long dimensionId) {
        LambdaQueryWrapper<Tag> qw = new LambdaQueryWrapper<>();
        if (dimensionId != null) {
            qw.eq(Tag::getDimensionId, dimensionId);
        }
        qw.orderByAsc(Tag::getId);
        return tagMapper.selectList(qw);
    }

    /** RM-CAT-061 findById */
    public Tag findById(Long id) {
        return id == null ? null : tagMapper.selectById(id);
    }

    /** RM-CAT-062 listByIds —— tag_ids 存在性校验（V-CAT-034） */
    public List<Tag> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getId, ids));
    }

    /** RM-CAT-063 listEnabled —— 消费端 status=enabled（E-CAT-07） */
    public List<Tag> listEnabled(Long dimensionId) {
        LambdaQueryWrapper<Tag> qw = new LambdaQueryWrapper<Tag>().eq(Tag::getStatus, TagStatus.ENABLED);
        if (dimensionId != null) {
            qw.eq(Tag::getDimensionId, dimensionId);
        }
        qw.orderByAsc(Tag::getId);
        return tagMapper.selectList(qw);
    }

    /**
     * RM-CAT-064 searchEnabledByName —— E-CAT-02 STEP-CAT-04 标签命中
     * （主表 name LIKE + 附表 label LIKE UNION，标签量级小不建 FULLTEXT）。
     */
    public List<Long> searchEnabledByName(String q, String locale) {
        Set<Long> tagIds = new LinkedHashSet<>();
        // 主表 name LIKE
        List<Tag> byName = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getStatus, TagStatus.ENABLED)
                .like(Tag::getName, q));
        for (Tag t : byName) {
            tagIds.add(t.getId());
        }
        // 附表 label LIKE（locale=es/fr 时）
        if ("es".equals(locale) || "fr".equals(locale)) {
            List<TagTranslation> byLabel = translationMapper.selectList(new LambdaQueryWrapper<TagTranslation>()
                    .eq(TagTranslation::getLocale, locale)
                    .like(TagTranslation::getLabel, q));
            if (!byLabel.isEmpty()) {
                List<Long> candidate = byLabel.stream().map(TagTranslation::getTagId).toList();
                // 仅 enabled 标签入结果
                for (Tag t : tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                        .in(Tag::getId, candidate)
                        .eq(Tag::getStatus, TagStatus.ENABLED))) {
                    tagIds.add(t.getId());
                }
            }
        }
        return new ArrayList<>(tagIds);
    }

    /** RM-CAT-065 countByDimensionId —— 409506 guard */
    public long countByDimensionId(Long dimensionId) {
        return tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getDimensionId, dimensionId));
    }

    /** RM-CAT-066 insert */
    public void insert(Tag tag) {
        tagMapper.insert(tag);
    }

    /** RM-CAT-067 update */
    public void update(Tag tag) {
        tagMapper.updateById(tag);
    }

    /** RM-CAT-068 deleteById */
    public void deleteById(Long id) {
        tagMapper.deleteById(id);
    }

    /** RM-CAT-069 listTranslationsByTagIds */
    public List<TagTranslation> listTranslationsByTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<TagTranslation>()
                .in(TagTranslation::getTagId, tagIds));
    }

    /** RM-CAT-070 replaceTranslations —— 整单覆盖 */
    public void replaceTranslations(Long tagId, List<TagTranslation> rows) {
        translationMapper.delete(new LambdaQueryWrapper<TagTranslation>().eq(TagTranslation::getTagId, tagId));
        if (rows != null) {
            for (TagTranslation row : rows) {
                row.setTagId(tagId);
                translationMapper.insert(row);
            }
        }
    }

    /** tag_translation 级联删除（TX-CAT-020） */
    public void deleteTranslationsByTagId(Long tagId) {
        translationMapper.delete(new LambdaQueryWrapper<TagTranslation>().eq(TagTranslation::getTagId, tagId));
    }
}
