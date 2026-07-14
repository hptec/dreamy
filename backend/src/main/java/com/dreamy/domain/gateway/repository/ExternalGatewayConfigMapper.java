package com.dreamy.domain.gateway.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * external_gateway_config 表 Mapper。
 * L2 TRACE: i18n-backend-data-detail.md §2 Repository RM-NNN。
 */
@Mapper
public interface ExternalGatewayConfigMapper extends BaseMapper<ExternalGatewayConfig> {

    /** 配置编辑 CAS；只更新表单字段，避免覆盖并发模型同步结果。 */
    @Update("UPDATE external_gateway_config SET "
            + "gateway_type = #{entity.gatewayType}, name = #{entity.name}, protocol = #{entity.protocol}, "
            + "base_url = #{entity.baseUrl}, api_key_encrypted = #{entity.apiKeyEncrypted}, "
            + "default_model = #{entity.defaultModel}, model_refresh_strategy = #{entity.modelRefreshStrategy}, "
            + "model_refresh_interval_min = #{entity.modelRefreshIntervalMin}, enabled = #{entity.enabled}, "
            + "extra_config = #{entity.extraConfig}, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateConfig(@Param("id") Long id,
                     @Param("expectedVersion") Integer expectedVersion,
                     @Param("entity") ExternalGatewayConfig entity);

    /** 模型同步成功：仅加载时 version 未变化才替换快照、清零失败数并推进版本。 */
    @Update("UPDATE external_gateway_config SET model_list = #{modelList}, models_synced_at = NOW(3), "
            + "consecutive_failures = 0, version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateSyncSuccess(@Param("id") Long id,
                          @Param("expectedVersion") Integer expectedVersion,
                          @Param("modelList") String modelList);

    /**
     * 模型同步失败：单条 SQL 原子 +1；scheduledRefresh=true 时第三次失败同时降级 manual 并禁用。
     * CASE 位于计数赋值之前，读取的是该行当前值，避免 MySQL 左到右赋值语义造成阈值偏移。
     */
    @Update("UPDATE external_gateway_config SET "
            + "enabled = CASE "
            + "WHEN COALESCE(consecutive_failures, 0) + 1 >= 3 AND model_refresh_strategy = 2 "
            + "AND #{scheduledRefresh} THEN 0 "
            + "ELSE enabled END, "
            + "model_refresh_strategy = CASE "
            + "WHEN COALESCE(consecutive_failures, 0) + 1 >= 3 AND model_refresh_strategy = 2 "
            + "AND #{scheduledRefresh} THEN 1 "
            + "ELSE model_refresh_strategy END, "
            + "consecutive_failures = COALESCE(consecutive_failures, 0) + 1, "
            + "version = version + 1, updated_at = NOW(3) "
            + "WHERE id = #{id} AND version = #{expectedVersion}")
    int incrementSyncFailure(@Param("id") Long id,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("scheduledRefresh") boolean scheduledRefresh);
}
