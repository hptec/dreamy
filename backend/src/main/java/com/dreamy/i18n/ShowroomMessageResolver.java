package com.dreamy.i18n;

import com.dreamy.error.ShowroomErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * showroom 域文案解析器（独立 bundle classpath:i18n/showroom-messages，不触碰 identity messages）。
 * 约束: showroom-api-detail §0 i18n（store en/es/fr 按请求 locale 返回错误 message，
 * 前端按 code 映射 next-intl 字典——决策 27）。
 */
@Component
public class ShowroomMessageResolver {

    private final MessageSource messageSource;

    public ShowroomMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/showroom-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案 */
    public String resolve(ShowroomErrorCode code, Locale locale) {
        try {
            return messageSource.getMessage(code.getMessageKey(), null,
                    locale == null ? Locale.ENGLISH : locale);
        } catch (NoSuchMessageException ex) {
            return code.getMessageKey();
        }
    }
}
