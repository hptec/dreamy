package com.dreamy.domain.cache.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.cache.entity.CacheInvalidationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 缓存失效日志 Repository。
 */
@Mapper
public interface CacheInvalidationLogRepository extends BaseMapper<CacheInvalidationLog> {

    /**
     * 分页查询失效日志（按触发时间倒序）。
     * @param page 分页对象
     * @param eventType 事件类型过滤（可选）
     * @param resourceType 资源类型过滤（可选）
     * @param status 状态过滤（可选）
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT * FROM cache_invalidation_log " +
            "WHERE 1=1 " +
            "<if test='eventType != null'> AND event_type = #{eventType} </if>" +
            "<if test='resourceType != null'> AND resource_type = #{resourceType} </if>" +
            "<if test='status != null'> AND status = #{status} </if>" +
            "ORDER BY triggered_at DESC" +
            "</script>")
    Page<CacheInvalidationLog> pageList(Page<CacheInvalidationLog> page,
                                        @Param("eventType") String eventType,
                                        @Param("resourceType") String resourceType,
                                        @Param("status") Integer status);
}
