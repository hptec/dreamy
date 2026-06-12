package com.dreamy.infra.ga4;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GA4 客户端配置（SVC-ANA §5.2；凭证仅后端配置——环境变量/配置中心，决策 19）。
 * mode=stub（dev/CI 默认）/ real（staging/生产，property-id 与 credentials-path 必填，启动校验快速暴露）。
 */
@Component
@ConfigurationProperties(prefix = "dreamy.ga4")
public class Ga4Properties {

    /** stub | real（DEC-ANA-7） */
    private String mode = "stub";

    /** GA4 媒体资源 ID（real 必填） */
    private String propertyId;

    /** service account JSON 路径（real 必填；文件权限 600；内容/路径不入日志——§5.4 脱敏） */
    private String credentialsPath;

    /** 连接超时（E-ANA-03 STEP-ANA-02 预算 2s） */
    private int connectTimeoutMs = 2000;

    /** 读超时（预算 8s，不重试） */
    private int readTimeoutMs = 8000;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(String propertyId) {
        this.propertyId = propertyId;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
