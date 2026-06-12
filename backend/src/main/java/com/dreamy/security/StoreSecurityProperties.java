package com.dreamy.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * StoreJwtFilter 配置化白名单（error-strategy L2 要求 2）。
 * - store-public-paths：公开路径白名单（七域共用同一机制，禁止逐端点硬编码在 filter 内；
 *   identity 既有 5 条公开路径迁移至此，行为保持不变）。
 * - showroom-guest-paths：guest 操作白名单（showroom-api-detail 0.2-b，条目形式与公开白名单完全一致）。
 * 条目形式：`METHOD:pattern`（AntPath），无 METHOD 前缀缺省匹配全部 method。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.security")
public class StoreSecurityProperties {

    /** 公开路径白名单（dreamy.security.store-public-paths） */
    private List<String> storePublicPaths = new ArrayList<>();

    /** showroom guest 操作白名单（dreamy.security.showroom-guest-paths） */
    private List<String> showroomGuestPaths = new ArrayList<>();
}
