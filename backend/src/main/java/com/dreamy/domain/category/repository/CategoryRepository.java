package com.dreamy.domain.category.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.entity.CategoryTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 分类仓储（RM-CAT-001~012）。
 * L2 TRACE: catalog-data-detail §2 CategoryRepository / CategoryTranslationRepository。
 */
@Repository
public class CategoryRepository {

    private final CategoryMapper categoryMapper;
    private final CategoryTranslationMapper translationMapper;

    public CategoryRepository(CategoryMapper categoryMapper, CategoryTranslationMapper translationMapper) {
        this.categoryMapper = categoryMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-CAT-001 listAll —— ORDER BY sort, id（树组装；E-CAT-06/15） */
    public List<Category> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .isNull(Category::getDeletedAt)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId));
    }

    /** RM-CAT-002 findById */
    public Category findById(Long id) {
        Category e = id == null ? null : categoryMapper.selectById(id);
        return (e == null || e.getDeletedAt() != null) ? null : e;
    }

    /** RM-CAT-003 insert */
    public void insert(Category category) {
        categoryMapper.insert(category);
    }

    /** RM-CAT-004 update */
    public void update(Category category) {
        categoryMapper.updateById(category);
    }

    /** RM-CAT-005 deleteById */
    public void deleteById(Long id) {
        categoryMapper.deleteById(id);
    }

    /** RM-CAT-006 countByParentId —— 删除 guard②（has_children，409502） */
    public long countByParentId(Long parentId) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .isNull(Category::getDeletedAt)
                .eq(Category::getParentId, parentId));
    }

    /** RM-CAT-007 countByAttributeSetId —— 409503 guard（AttributeSet 删除） */
    public long countByAttributeSetId(Long attributeSetId) {
        return categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .isNull(Category::getDeletedAt)
                .eq(Category::getAttributeSetId, attributeSetId));
    }

    /** RM-CAT-008 maxSortOfSiblings —— 新增分类缺省排序（同层 MAX(sort)） */
    public int maxSortOfSiblings(Long parentId) {
        LambdaQueryWrapper<Category> qw = new LambdaQueryWrapper<>();
        if (parentId == null) {
            qw.isNull(Category::getParentId);
        } else {
            qw.eq(Category::getParentId, parentId);
        }
        qw.isNull(Category::getDeletedAt);
        qw.orderByDesc(Category::getSort).last("LIMIT 1");
        Category top = categoryMapper.selectOne(qw);
        return top == null || top.getSort() == null ? -1 : top.getSort();
    }

    /** RM-CAT-010 listByCategoryIds —— translation 批查防 N+1（NP-CAT-001） */
    public List<CategoryTranslation> listTranslationsByCategoryIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<CategoryTranslation>()
                .in(CategoryTranslation::getCategoryId, categoryIds));
    }

    /** RM-CAT-011 replaceAll —— DELETE+批量 INSERT（整单覆盖，事务内调用） */
    public void replaceTranslations(Long categoryId, List<CategoryTranslation> rows) {
        deleteTranslationsByCategoryId(categoryId);
        if (rows != null) {
            for (CategoryTranslation row : rows) {
                row.setCategoryId(categoryId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-CAT-012 deleteByCategoryId */
    public void deleteTranslationsByCategoryId(Long categoryId) {
        translationMapper.delete(new LambdaQueryWrapper<CategoryTranslation>()
                .eq(CategoryTranslation::getCategoryId, categoryId));
    }
}
