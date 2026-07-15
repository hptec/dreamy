package com.dreamy.domain.cache.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.cache.entity.CacheInvalidationStep;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CacheInvalidationStepRepository extends BaseMapper<CacheInvalidationStep> {
    default List<CacheInvalidationStep> listByTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CacheInvalidationStep>()
                .in(CacheInvalidationStep::getTaskId, taskIds)
                .orderByAsc(CacheInvalidationStep::getTaskId)
                .orderByAsc(CacheInvalidationStep::getId));
    }

    default int failRunningByTaskId(Long taskId, LocalDateTime completedAt, String errorMessage) {
        return update(null, new LambdaUpdateWrapper<CacheInvalidationStep>()
                .eq(CacheInvalidationStep::getTaskId, taskId)
                .eq(CacheInvalidationStep::getStatus, CacheInvalidationStep.STATUS_RUNNING)
                .set(CacheInvalidationStep::getStatus, CacheInvalidationStep.STATUS_FAILED)
                .set(CacheInvalidationStep::getCompletedAt, completedAt)
                .set(CacheInvalidationStep::getErrorMessage, errorMessage)
                .set(CacheInvalidationStep::getUpdatedAt, completedAt));
    }
}
