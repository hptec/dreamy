package com.dreamy.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 汇率行情供应商端口（order-flow-complete E/G）：manual（stub，无供应商）| frankfurter（ECB 日频）。
 * 失败语义（§4.6）：HTTP 超时 5s，单次 run 不重试；抛 {@link ProviderException} 由调用方保留现值 + [ALERT]。
 */
public interface ExchangeRateProviderPort {

    /** 供应商报价（quoteDate=供应商报价日；rates 键为目标币种，值为 1 base = rate target） */
    record RateQuote(LocalDate quoteDate, Map<String, BigDecimal> rates) {
    }

    /** 供应商调用失败（超时/非 2xx/解析失败） */
    class ProviderException extends RuntimeException {
        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 供应商标识（manual | frankfurter） */
    String name();

    /** manual 模式：无供应商，refresh → 409905 */
    default boolean isManual() {
        return false;
    }

    /**
     * 拉取最新汇率。
     *
     * @param base       基准币种（USD）
     * @param currencies 目标币种列表
     */
    RateQuote fetchLatest(String base, List<String> currencies);
}
