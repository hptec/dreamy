package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * HomePageSection 仓储（RM-001~008）。
 */
@Repository
public class HomePageSectionRepository {

    private final HomePageSectionMapper mapper;

    public HomePageSectionRepository(HomePageSectionMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<HomePageSection> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public List<HomePageSection> findAllOrderBySort() {
        return mapper.selectList(new LambdaQueryWrapper<HomePageSection>()
                .orderByAsc(HomePageSection::getSortOrder)
                .orderByAsc(HomePageSection::getId));
    }

    public List<HomePageSection> findEnabledOrderBySort() {
        return mapper.selectList(new LambdaQueryWrapper<HomePageSection>()
                .eq(HomePageSection::getEnabled, true)
                .orderByAsc(HomePageSection::getSortOrder)
                .orderByAsc(HomePageSection::getId));
    }

    public int insert(HomePageSection entity) {
        return mapper.insert(entity);
    }

    public int updateByIdAndVersion(HomePageSection entity) {
        int rows = mapper.update(entity, new LambdaUpdateWrapper<HomePageSection>()
                .eq(HomePageSection::getId, entity.getId())
                .eq(HomePageSection::getVersion, entity.getVersion()));
        if (rows == 0) {
            // 乐观锁冲突，调用方处理
            return 0;
        }
        entity.setVersion(entity.getVersion() + 1);
        return rows;
    }

    public int deleteById(Long id) {
        return mapper.deleteById(id);
    }

    public void replaceAll(List<HomePageSection> sections) {
        mapper.delete(new LambdaQueryWrapper<HomePageSection>().isNotNull(HomePageSection::getId));
        for (HomePageSection section : sections) {
            mapper.insert(section);
        }
    }

    public int batchUpdateSort(List<long[]> idSortPairs) {
        int total = 0;
        for (long[] pair : idSortPairs) {
            total += mapper.update(null, new LambdaUpdateWrapper<HomePageSection>()
                    .eq(HomePageSection::getId, pair[0])
                    .set(HomePageSection::getSortOrder, (int) pair[1])
                    .setSql("version = version + 1"));
        }
        return total;
    }

    public int updateEnabled(Long id, Boolean enabled, Integer expectedVersion) {
        return mapper.update(null, new LambdaUpdateWrapper<HomePageSection>()
                .eq(HomePageSection::getId, id)
                .eq(HomePageSection::getVersion, expectedVersion)
                .set(HomePageSection::getEnabled, enabled)
                .setSql("version = version + 1"));
    }
}
