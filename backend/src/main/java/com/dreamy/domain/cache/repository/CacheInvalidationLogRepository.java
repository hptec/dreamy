package com.dreamy.domain.cache.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.cache.entity.CacheInvalidationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 缓存失效日志 Repository。
 * 用 BaseMapper + LambdaQueryWrapper，确保 autoResultMap + JacksonTypeHandler 生效
 * （自定义 @Select 的 SELECT * 不走 typeHandler，JSON 字段会返回 null）。
 */
@Mapper
public interface CacheInvalidationLogRepository extends BaseMapper<CacheInvalidationLog> {

    /**
     * 分页查询失效日志（按触发时间倒序）。
     */
    default Page<CacheInvalidationLog> pageList(Page<CacheInvalidationLog> page,
                                                String eventType, String resourceType, Integer status) {
        LambdaQueryWrapper<CacheInvalidationLog> wrapper = new LambdaQueryWrapper<>();
        if (eventType != null) {
            wrapper.eq(CacheInvalidationLog::getEventType, eventType);
        }
        if (resourceType != null) {
            wrapper.eq(CacheInvalidationLog::getResourceType, resourceType);
        }
        if (status != null) {
            wrapper.eq(CacheInvalidationLog::getStatus, status);
        }
        wrapper.orderByDesc(CacheInvalidationLog::getTriggeredAt);
        return selectPage(page, wrapper);
    }
}
