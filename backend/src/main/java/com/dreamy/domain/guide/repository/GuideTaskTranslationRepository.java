package com.dreamy.domain.guide.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.guide.entity.GuideTaskTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class GuideTaskTranslationRepository {
    private final GuideTaskTranslationMapper mapper;

    public GuideTaskTranslationRepository(GuideTaskTranslationMapper mapper) {
        this.mapper = mapper;
    }

    public List<GuideTaskTranslation> listByTaskIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of() : mapper.selectList(
                new LambdaQueryWrapper<GuideTaskTranslation>().in(GuideTaskTranslation::getTaskId, ids));
    }

    public void replace(Long taskId, List<GuideTaskTranslation> rows) {
        deleteByTaskId(taskId);
        if (rows != null) {
            rows.forEach(row -> {
                row.setTaskId(taskId);
                mapper.insert(row);
            });
        }
    }

    public void insert(GuideTaskTranslation row) {
        mapper.insert(row);
    }

    public void deleteByTaskId(Long taskId) {
        mapper.delete(new LambdaQueryWrapper<GuideTaskTranslation>()
                .eq(GuideTaskTranslation::getTaskId, taskId));
    }
}
