package com.dreamy.infra.tracking;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 轨迹供应商配置（order-flow-complete §3.3 dreamy.tracking.*）。mode=stub（缺省）| track17。
 * api key 仅后端配置，不落日志。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.tracking")
public class TrackingProperties {

    /** stub | track17 */
    private String mode = "stub";

    /** 17TRACK API key（header 17token） */
    private String track17ApiKey = "";

    /** 17TRACK API base */
    private String track17BaseUrl = "https://api.17track.net/track/v2.2";

    /** 同步 cron（缺省每 30 分钟） */
    private String syncCron = "0 */30 * * * *";

    /** 供应商 HTTP 超时（ms） */
    private long providerTimeoutMs = 5000L;

    /** 同步间隔（分钟）：synced_at 早于 now-间隔 的包裹才重新拉取 */
    private int syncIntervalMinutes = 30;
}
