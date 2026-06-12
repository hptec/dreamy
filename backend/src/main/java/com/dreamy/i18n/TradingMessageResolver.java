package com.dreamy.i18n;

import com.dreamy.error.TradingErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * trading 域文案解析器（独立 bundle classpath:i18n/trading-messages，不触碰 identity messages）。
 * 约束: trading-api-detail §0 i18n（store en/es/fr，admin zh）；决策 27（数字 code 稳定锚点，前端按 code 映射）。
 */
@Component
public class TradingMessageResolver {

    private final MessageSource messageSource;

    public TradingMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/trading-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案 */
    public String resolve(TradingErrorCode code, Locale locale) {
        return resolve(code.getMessageKey(), locale);
    }

    /** 任意 key，支持占位参数 */
    public String resolve(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale == null ? Locale.ENGLISH : locale);
        } catch (NoSuchMessageException ex) {
            return key;
        }
    }
}
