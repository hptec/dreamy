package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.site_builder.entity.FooterColumn;
import com.dreamy.domain.site_builder.entity.FooterLink;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FooterRepository {

    private final FooterColumnMapper columnMapper;
    private final FooterLinkMapper linkMapper;

    public FooterRepository(FooterColumnMapper columnMapper, FooterLinkMapper linkMapper) {
        this.columnMapper = columnMapper;
        this.linkMapper = linkMapper;
    }

    public List<FooterColumn> findAllColumnsOrderBySort() {
        return columnMapper.selectList(new LambdaQueryWrapper<FooterColumn>()
                .orderByAsc(FooterColumn::getSortOrder)
                .orderByAsc(FooterColumn::getId));
    }

    public List<FooterLink> findAllLinksOrderBySort() {
        return linkMapper.selectList(new LambdaQueryWrapper<FooterLink>()
                .orderByAsc(FooterLink::getSortOrder)
                .orderByAsc(FooterLink::getId));
    }

    public int deleteAllColumns() {
        return columnMapper.delete(new LambdaQueryWrapper<FooterColumn>());
    }

    public int deleteAllLinks() {
        return linkMapper.delete(new LambdaQueryWrapper<FooterLink>());
    }

    public int insertColumn(FooterColumn column) {
        return columnMapper.insert(column);
    }

    public int insertLink(FooterLink link) {
        return linkMapper.insert(link);
    }
}
