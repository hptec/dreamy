package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.site_builder.entity.NavigationItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NavigationItemRepository {

    private final NavigationItemMapper mapper;

    public NavigationItemRepository(NavigationItemMapper mapper) {
        this.mapper = mapper;
    }

    public List<NavigationItem> findAllOrderBySort() {
        return mapper.selectList(new LambdaQueryWrapper<NavigationItem>()
                .orderByAsc(NavigationItem::getSortOrder)
                .orderByAsc(NavigationItem::getId));
    }

    public List<NavigationItem> findEnabledOrderBySort() {
        return mapper.selectList(new LambdaQueryWrapper<NavigationItem>()
                .eq(NavigationItem::getEnabled, true)
                .orderByAsc(NavigationItem::getSortOrder)
                .orderByAsc(NavigationItem::getId));
    }

    public int insert(NavigationItem entity) {
        return mapper.insert(entity);
    }

    public int updateById(NavigationItem entity) {
        return mapper.updateById(entity);
    }

    public int deleteByIdsNotIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return mapper.delete(new LambdaQueryWrapper<NavigationItem>());
        }
        return mapper.delete(new LambdaQueryWrapper<NavigationItem>()
                .notIn(NavigationItem::getId, ids));
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }
}
