package com.dreamy.domain.gateway.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.gateway.entity.ExternalGatewayConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * external_gateway_config 表 Mapper。
 * L2 TRACE: i18n-backend-data-detail.md §2 Repository RM-NNN。
 */
@Mapper
public interface ExternalGatewayConfigMapper extends BaseMapper<ExternalGatewayConfig> {
}
