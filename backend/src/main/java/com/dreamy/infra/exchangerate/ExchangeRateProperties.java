package com.dreamy.infra.exchangerate;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 汇率供应商配置（order-flow-complete §3.3 dreamy.exchange-rate.*）。
 * mode=manual（缺省，五币种手工维护）| frankfurter（ECB 日频，api.frankfurter.app）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dreamy.exchange-rate")
public class ExchangeRateProperties {

    /** manual | frankfurter */
    private String mode = "manual";

    /** 每日刷新 cron（缺省 03:05） */
    private String refreshCron = "0 5 3 * * *";

    /** 供应商 HTTP 超时（ms） */
    private long providerTimeoutMs = 5000L;

    /** Frankfurter API base */
    private String frankfurterBaseUrl = "https://api.frankfurter.app";
}
