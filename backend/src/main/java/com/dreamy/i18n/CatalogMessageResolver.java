package com.dreamy.i18n;

import com.dreamy.error.CatalogErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * catalog 域文案解析器（独立 bundle classpath:i18n/catalog-messages，不触碰 identity messages）。
 * 约束: api-detail §0 i18n（store en/es/fr，admin zh）；决策 27（数字 code 稳定锚点，前端按 code 映射）；
 * E-CAT-05 尺码推荐话术 key（size_reco.*）按 locale 渲染。
 */
@Component
public class CatalogMessageResolver {

    private final MessageSource messageSource;

    public CatalogMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/catalog-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案 */
    public String resolve(CatalogErrorCode code, Locale locale) {
        return resolve(code.getMessageKey(), locale);
    }

    /** 任意 key（尺码推荐话术等），支持占位参数 */
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
