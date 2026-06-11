package com.dreamy.catalog.domain.attribute.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.catalog.domain.attribute.entity.AttributeSet;
import com.dreamy.catalog.domain.attribute.entity.AttributeSetItem;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 属性集仓储（RM-CAT-040~047）。
 * L2 TRACE: catalog-data-detail §2 AttributeSetRepository / AttributeSetItemRepository。
 */
@Repository
public class AttributeSetRepository {

    private final AttributeSetMapper setMapper;
    private final AttributeSetItemMapper itemMapper;

    public AttributeSetRepository(AttributeSetMapper setMapper, AttributeSetItemMapper itemMapper) {
        this.setMapper = setMapper;
        this.itemMapper = itemMapper;
    }

    /** RM-CAT-040 listAll */
    public List<AttributeSet> listAll() {
        return setMapper.selectList(new LambdaQueryWrapper<AttributeSet>().orderByAsc(AttributeSet::getId));
    }

    /** RM-CAT-041 findById */
    public AttributeSet findById(Long id) {
        return id == null ? null : setMapper.selectById(id);
    }

    /** RM-CAT-042 insert */
    public void insert(AttributeSet set) {
        setMapper.insert(set);
    }

    /** RM-CAT-043 updateLabel */
    public void updateLabel(Long id, String label) {
        setMapper.update(null, new LambdaUpdateWrapper<AttributeSet>()
                .eq(AttributeSet::getId, id)
                .set(AttributeSet::getLabel, label));
    }

    /** RM-CAT-044 deleteById */
    public void deleteById(Long id) {
        setMapper.deleteById(id);
    }

    /** RM-CAT-045 listItemsBySetIds —— 矩阵批查（NP-CAT-001） */
    public List<AttributeSetItem> listItemsBySetIds(Collection<Long> setIds) {
        if (setIds == null || setIds.isEmpty()) {
            return List.of();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<AttributeSetItem>()
                .in(AttributeSetItem::getAttributeSetId, setIds)
                .orderByAsc(AttributeSetItem::getId));
    }

    /** RM-CAT-046 replaceItems —— 事务内 DELETE+批量 INSERT（TX-CAT-010 全量重写原子） */
    public void replaceItems(Long setId, List<AttributeSetItem> items) {
        itemMapper.delete(new LambdaQueryWrapper<AttributeSetItem>()
                .eq(AttributeSetItem::getAttributeSetId, setId));
        if (items != null) {
            for (AttributeSetItem item : items) {
                item.setAttributeSetId(setId);
                itemMapper.insert(item);
            }
        }
    }

    /** RM-CAT-047 countItemsByAttributeId —— 409507 guard（attribute_def 删除） */
    public long countItemsByAttributeId(Long attributeId) {
        return itemMapper.selectCount(new LambdaQueryWrapper<AttributeSetItem>()
                .eq(AttributeSetItem::getAttributeId, attributeId));
    }
}
