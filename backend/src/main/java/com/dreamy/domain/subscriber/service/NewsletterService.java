package com.dreamy.domain.subscriber.service;

import com.dreamy.enums.NewsletterSource;
import com.dreamy.enums.SubscriberStatus;
import com.dreamy.domain.subscriber.entity.NewsletterSubscriber;
import com.dreamy.domain.subscriber.repository.NewsletterSubscriberRepository;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/**
 * Newsletter 订阅/退订服务（E-MKT-11 subscribeNewsletter + 退订；FLOW-P19②，决策 26——仅落表不发码不发邮件）。
 * 订阅幂等：email 小写归一 + 单语句 upsert（已订阅空操作、已退订复活）；无论新增/重复/复活一律 {subscribed:true}。
 * 退订幂等：token 校验 + 单语句 UPDATE（WHERE 原子含代际谓词）；不存在/已退订一律 {unsubscribed:true}。
 * 响应特征一致，不泄露邮箱是否已存在（防枚举）。WAF 限流在 Cloudflare 层（决策 11，后端不实现）。
 * L2 TRACE: V-MKT-009~011 / RM-MKT-140 / TX-MKT-027 / CV-MKT-008 / TC-MKT-028。
 */
@Service
public class NewsletterService {

    /** V-MKT-009 RFC5322 实用子集（bs-543/544） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final NewsletterSubscriberRepository repository;
    private final UnsubscribeTokenService tokenService;

    public NewsletterService(NewsletterSubscriberRepository repository, UnsubscribeTokenService tokenService) {
        this.repository = repository;
        this.tokenService = tokenService;
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
        // STEP-MKT-02 单语句 upsert：已订阅空操作（首写胜出），已退订复活（status 2→1）
        NewsletterSubscriber subscriber = new NewsletterSubscriber();
        subscriber.setEmail(normalized);
        subscriber.setSource(sourceEnum);
        subscriber.setLocale(locale);
        subscriber.setSubscribedAt(LocalDateTime.now());
        repository.upsertReactivating(subscriber);
        // STEP-MKT-03 不发码不发邮件（决策 26 显式降级）；响应由 controller 恒定 {subscribed:true}
    }

    /**
     * 退订（邮件链接入口；恒 200 {unsubscribed:true}，token 无效/过期/代际落后 → 422704 field=token）。
     * token 有效但 email 不存在或已退订 → 幂等 200（不泄露存在性）。
     */
    public void unsubscribe(String token) {
        UnsubscribeTokenService.ParsedToken parsed;
        try {
            parsed = tokenService.parse(token);
        } catch (UnsubscribeTokenService.InvalidTokenException e) {
            rejectInvalidToken();
            return; // unreachable——throwIfAny 必抛
        }
        String email = parsed.email().toLowerCase();
        LocalDateTime genTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(parsed.genEpochMillis()), ZoneOffset.UTC);
        int updated = repository.unsubscribeByEmail(email, genTime);
        if (updated > 0) {
            return;
        }
        // 0 行两种可能：email 不存在（幂等 200）、已退订（幂等 200）、代际不一致（422）。
        // 该 SELECT 仅用于错误响应分类，状态正确性已由 UPDATE 原子谓词保证。
        NewsletterSubscriber existing = repository.findByEmail(email);
        if (existing != null
                && existing.getStatus() == SubscriberStatus.SUBSCRIBED
                && UnsubscribeTokenService.toEpochMillis(existing.getSubscribedAt()) != parsed.genEpochMillis()) {
            rejectInvalidToken();
        }
    }

    /** 生成退订链接 token（供未来邮件模板使用——决策 26 本期无邮件发送；gen 取持久化 subscribed_at；仅已订阅记录可生成） */
    public String generateUnsubscribeToken(String email) {
        NewsletterSubscriber existing = repository.findByEmail(email.trim().toLowerCase());
        if (existing == null || existing.getStatus() != SubscriberStatus.SUBSCRIBED) {
            return null;
        }
        return tokenService.generate(existing.getEmail(),
                UnsubscribeTokenService.toEpochMillis(existing.getSubscribedAt()));
    }

    private void rejectInvalidToken() {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        errors.reject("token", "invalid_or_expired");
        errors.throwIfAny();
    }
}
