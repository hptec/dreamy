package com.dreamy.i18n;

import com.dreamy.error.MarketingErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * marketing 域文案解析器（独立 bundle classpath:i18n/marketing-messages，不触碰 identity messages）。
 * 约束: marketing-api-detail §0 i18n（store en/es/fr query 参数优先，admin zh）；决策 27（数字 code 稳定锚点）。
 */
@Component
public class MarketingMessageResolver {

    private final MessageSource messageSource;

    public MarketingMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/marketing-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案 */
    public String resolve(MarketingErrorCode code, Locale locale) {
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

    /** locale 字符串（en/es/fr/zh）→ Locale */
    public static Locale toLocale(String locale) {
        if (locale == null) {
            return Locale.ENGLISH;
        }
        return switch (locale) {
            case "es" -> Locale.forLanguageTag("es");
            case "fr" -> Locale.FRENCH;
            case "zh" -> Locale.CHINESE;
            default -> Locale.ENGLISH;
        };
    }
}
