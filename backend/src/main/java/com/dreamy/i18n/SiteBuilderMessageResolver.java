package com.dreamy.i18n;

import com.dreamy.error.SiteBuilderErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * site_builder 域文案解析器（独立 bundle classpath:i18n/site-builder-messages）。
 * 约束: i18n 策略（store en/es/fr by Accept-Language，缺省 en；admin 固定 zh）；
 * 决策 27（数字 code 稳定锚点，文案随 locale 变化）。
 */
@Component
public class SiteBuilderMessageResolver {

    private final MessageSource messageSource;

    public SiteBuilderMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/site-builder-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案；未配置文案时回退 code 名称，保证前端仍有稳定锚点 */
    public String resolve(SiteBuilderErrorCode code, Locale locale) {
        try {
            return messageSource.getMessage(code.getMessageKey(), null, locale == null ? Locale.ENGLISH : locale);
        } catch (NoSuchMessageException ex) {
            return code.name();
        }
    }
}
