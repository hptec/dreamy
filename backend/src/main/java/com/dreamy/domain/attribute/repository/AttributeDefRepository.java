package com.dreamy.domain.attribute.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.domain.attribute.entity.AttributeDefTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 属性字典仓储（RM-CAT-020~032）。
 * L2 TRACE: catalog-data-detail §2 AttributeDefRepository / AttributeDefTranslationRepository。
 */
@Repository
public class AttributeDefRepository {

    private final AttributeDefMapper defMapper;
    private final AttributeDefTranslationMapper translationMapper;

    public AttributeDefRepository(AttributeDefMapper defMapper, AttributeDefTranslationMapper translationMapper) {
        this.defMapper = defMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-CAT-020 listAll —— ORDER BY id（E-CAT-23 STEP-CAT-01） */
    public List<AttributeDef> listAll() {
        return defMapper.selectList(new LambdaQueryWrapper<AttributeDef>().orderByAsc(AttributeDef::getId));
    }

    /** RM-CAT-021 findById */
    public AttributeDef findById(Long id) {
        return id == null ? null : defMapper.selectById(id);
    }

    /** RM-CAT-022 existsByKey —— uk_attribute_def_key 兜底（V-CAT-053） */
    public boolean existsByKey(String key) {
        return defMapper.selectCount(new LambdaQueryWrapper<AttributeDef>().eq(AttributeDef::getKey, key)) > 0;
    }

    /** key 点查（EAV 迁移/种子幂等用） */
    public AttributeDef findByKey(String key) {
        return defMapper.selectOne(new LambdaQueryWrapper<AttributeDef>().eq(AttributeDef::getKey, key));
    }

    /** RM-CAT-023 insert */
    public void insert(AttributeDef def) {
        defMapper.insert(def);
    }

    /** RM-CAT-024 update */
    public void update(AttributeDef def) {
        defMapper.updateById(def);
    }

    /** RM-CAT-025 deleteById */
    public void deleteById(Long id) {
        defMapper.deleteById(id);
    }

    /** RM-CAT-026 listByIds —— attr_overrides key 与属性集行校验（V-CAT-047/050） */
    public List<AttributeDef> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return defMapper.selectList(new LambdaQueryWrapper<AttributeDef>().in(AttributeDef::getId, ids));
    }

    /** RM-CAT-030 listByDefIds —— translation 批查 */
    public List<AttributeDefTranslation> listTranslationsByDefIds(Collection<Long> defIds) {
        if (defIds == null || defIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<AttributeDefTranslation>()
                .in(AttributeDefTranslation::getAttributeDefId, defIds));
    }

    /** RM-CAT-031 replaceAll —— translation 整单覆盖 */
    public void replaceTranslations(Long defId, List<AttributeDefTranslation> rows) {
        deleteTranslationsByDefId(defId);
        if (rows != null) {
            for (AttributeDefTranslation row : rows) {
                row.setAttributeDefId(defId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-CAT-032 deleteByDefId */
    public void deleteTranslationsByDefId(Long defId) {
        translationMapper.delete(new LambdaQueryWrapper<AttributeDefTranslation>()
                .eq(AttributeDefTranslation::getAttributeDefId, defId));
    }
}
