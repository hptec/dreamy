package com.dreamy.domain.cache.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.cache.entity.CacheInvalidationTask;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CacheInvalidationTaskRepository extends BaseMapper<CacheInvalidationTask> {

    default Page<CacheInvalidationTask> pageList(Page<CacheInvalidationTask> page, String triggerMode,
                                                 String resourceType, Integer status) {
        LambdaQueryWrapper<CacheInvalidationTask> query = new LambdaQueryWrapper<>();
        if (triggerMode != null && !triggerMode.isBlank()) query.eq(CacheInvalidationTask::getTriggerMode, triggerMode);
        if (resourceType != null && !resourceType.isBlank()) query.eq(CacheInvalidationTask::getResourceType, resourceType);
        if (status != null) query.eq(CacheInvalidationTask::getStatus, status);
        return selectPage(page, query.orderByDesc(CacheInvalidationTask::getTriggeredAt));
    }

    default List<CacheInvalidationTask> findDue(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapper<CacheInvalidationTask>()
                .in(CacheInvalidationTask::getStatus,
                        CacheInvalidationTask.STATUS_PENDING,
                        CacheInvalidationTask.STATUS_SCHEDULED,
                        CacheInvalidationTask.STATUS_RETRYING)
                .le(CacheInvalidationTask::getScheduledAt, now)
                .and(q -> q.isNull(CacheInvalidationTask::getNextRetryAt)
                        .or().le(CacheInvalidationTask::getNextRetryAt, now))
                .orderByAsc(CacheInvalidationTask::getScheduledAt)
                .last("LIMIT " + limit));
    }

    default boolean claim(Long id, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<CacheInvalidationTask>()
                .eq(CacheInvalidationTask::getId, id)
                .in(CacheInvalidationTask::getStatus,
                        CacheInvalidationTask.STATUS_PENDING,
                        CacheInvalidationTask.STATUS_SCHEDULED,
                        CacheInvalidationTask.STATUS_RETRYING)
                .set(CacheInvalidationTask::getStatus, CacheInvalidationTask.STATUS_RUNNING)
                .set(CacheInvalidationTask::getUpdatedAt, now)) == 1;
    }

    default List<CacheInvalidationTask> findStaleRunning(LocalDateTime cutoff, int limit) {
        return selectList(new LambdaQueryWrapper<CacheInvalidationTask>()
                .eq(CacheInvalidationTask::getStatus, CacheInvalidationTask.STATUS_RUNNING)
                .le(CacheInvalidationTask::getUpdatedAt, cutoff)
                .orderByAsc(CacheInvalidationTask::getUpdatedAt)
                .last("LIMIT " + limit));
    }

    default int cancelFuture(String resourceType, String resourceId, String triggerPointPrefix, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<CacheInvalidationTask>()
                .eq(CacheInvalidationTask::getResourceType, resourceType)
                .eq(CacheInvalidationTask::getResourceId, resourceId)
                .likeRight(CacheInvalidationTask::getTriggerPoint, triggerPointPrefix)
                .in(CacheInvalidationTask::getStatus,
                        CacheInvalidationTask.STATUS_PENDING,
                        CacheInvalidationTask.STATUS_SCHEDULED,
                        CacheInvalidationTask.STATUS_RETRYING)
                .gt(CacheInvalidationTask::getScheduledAt, now)
                .set(CacheInvalidationTask::getStatus, CacheInvalidationTask.STATUS_CANCELLED)
                .set(CacheInvalidationTask::getCompletedAt, now));
    }
}
