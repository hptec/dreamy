package com.dreamy.i18n;

import com.dreamy.error.ReviewErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * review 域文案解析器（独立 bundle classpath:i18n/review-messages，不触碰 identity messages）。
 * 约束: review-api-detail §0 i18n（store en/es/fr，admin zh，前端按 code 映射文案——决策 27）。
 */
@Component
public class ReviewMessageResolver {

    private final MessageSource messageSource;

    public ReviewMessageResolver() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/review-messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setFallbackToSystemLocale(false);
        this.messageSource = ms;
    }

    /** 错误码文案 */
    public String resolve(ReviewErrorCode code, Locale locale) {
        try {
            return messageSource.getMessage(code.getMessageKey(), null,
                    locale == null ? Locale.ENGLISH : locale);
        } catch (NoSuchMessageException ex) {
            return code.getMessageKey();
        }
    }
}
