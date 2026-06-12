package com.dreamy.domain.subscriber.service;

import com.dreamy.enums.NewsletterSource;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import com.dreamy.domain.subscriber.repository.NewsletterSubscriberRepository;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Newsletter 订阅服务（E-MKT-11 subscribeNewsletter；FLOW-P19②，决策 26——仅落表不发码不发邮件）。
 * 幂等：email 小写归一 + INSERT ON DUPLICATE 空操作（首写胜出）；无论新增或重复一律 {subscribed:true}
 * （响应特征一致，不泄露邮箱是否已存在——防枚举）。WAF 限流在 Cloudflare 层（决策 11，后端不实现）。
 * L2 TRACE: V-MKT-009~011 / RM-MKT-140 / TX-MKT-027 / CV-MKT-008 / TC-MKT-028。
 */
@Service
public class NewsletterService {

    /** V-MKT-009 RFC5322 实用子集（bs-543/544） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final NewsletterSubscriberRepository repository;

    public NewsletterService(NewsletterSubscriberRepository repository) {
        this.repository = repository;
    }

    /** E-MKT-11：订阅（恒 200 {subscribed:true}） */
    public void subscribe(String email, Integer source, String locale) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // STEP-MKT-01 email 小写归一（trim+lowercase——幂等判重口径统一，CV-MKT-008）
        String normalized = email == null ? null : email.trim().toLowerCase();
        // V-MKT-009 email 必填格式 ≤255
        if (normalized == null || normalized.isEmpty()) {
            errors.reject("email", "required");
        } else if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            errors.reject("email", "format_invalid");
        }
        // V-MKT-010 source 必填 ∈ {footer, modal, exit_intent}
        NewsletterSource sourceEnum = NewsletterSource.of(source);
        if (sourceEnum == null) {
            errors.reject("source", "invalid_enum");
        }
        // V-MKT-011 locale 必填 ∈ {en, es, fr}
        if (locale == null || !MarketingParams.LOCALES.contains(locale)) {
            errors.reject("locale", "invalid_enum");
        }
        errors.throwIfAny();
        // STEP-MKT-02 INSERT ON DUPLICATE KEY UPDATE id=id（重复订阅空操作，首写胜出）
        NewsletterSubscriber subscriber = new NewsletterSubscriber();
        subscriber.setEmail(normalized);
        subscriber.setSource(sourceEnum);
        subscriber.setLocale(locale);
        subscriber.setSubscribedAt(LocalDateTime.now());
        repository.insertIgnoreDuplicate(subscriber);
        // STEP-MKT-03 不发码不发邮件（决策 26 显式降级）；响应由 controller 恒定 {subscribed:true}
    }
}
