package com.dreamy.identity.domain.otp.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.user.model.LoginResult;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.infra.OtpRateLimiter;
import com.dreamy.identity.infra.mail.MailSender;
import com.dreamy.identity.domain.authconfig.entity.AuthConfigEntity;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import com.dreamy.identity.domain.user.entity.UserEntity;
import com.dreamy.identity.domain.otp.repository.OtpCodeMapper;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import com.dreamy.identity.domain.user.service.MergeService;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.security.TokenPair;
import com.dreamy.identity.util.OtpGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * OTP 领域服务（发送 FLOW-01 / 校验登录 FLOW-02）。
 * 约束: V-001~004；STEP 全序列；RM-020~024；CV-004（仅存 code_hash）；TX-001（行锁串行）；
 * 频控 42901/42902；401 40101（remaining_attempts）；410 41001/41002。
 */
@Service
public class OtpService {

    private final OtpCodeMapper otpCodeMapper;
    private final AuthConfigService authConfigService;
    private final OtpRateLimiter rateLimiter;
    private final MailSender mailSender;
    private final MergeService mergeService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;

    public OtpService(OtpCodeMapper otpCodeMapper,
                      AuthConfigService authConfigService,
                      OtpRateLimiter rateLimiter,
                      MailSender mailSender,
                      MergeService mergeService,
                      SessionService sessionService,
                      PasswordEncoder passwordEncoder) {
        this.otpCodeMapper = otpCodeMapper;
        this.authConfigService = authConfigService;
        this.rateLimiter = rateLimiter;
        this.mailSender = mailSender;
        this.mergeService = mergeService;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * FLOW-01 sendOtp。
     * 约束: STEP-01 规范化 → STEP-02/03 频控 → STEP-04 读 AuthConfig → STEP-05 失效旧 pending
     * → STEP-06 生成明文仅存 code_hash → STEP-07 异步发邮件。
     * @return [resendAfterSeconds, otpLength]
     */
    public SendOtpResult sendOtp(String rawEmail, String locale, String ip) {
        String email = normalize(rawEmail); // STEP-01

        // STEP-02 重发间隔频控
        AuthConfigEntity cfg = authConfigService.getConfig(); // STEP-04
        OtpRateLimiter.RateDecision resend = rateLimiter.checkResend(email, cfg.getOtpResendSeconds());
        if (!resend.permitted()) {
            throw new BizException(resend.errorCode(), resend.details()); // 42901
        }
        // STEP-03 发码窗口频控
        OtpRateLimiter.RateDecision quota = rateLimiter.checkSendQuota(email, ip);
        if (!quota.permitted()) {
            throw new BizException(quota.errorCode()); // 42902
        }

        // STEP-05 失效旧 pending（RM-021 expireAllPending）
        LambdaUpdateWrapper<OtpCodeEntity> expire = new LambdaUpdateWrapper<>();
        expire.eq(OtpCodeEntity::getEmail, email)
                .eq(OtpCodeEntity::getStatus, "pending")
                .set(OtpCodeEntity::getStatus, "expired");
        otpCodeMapper.update(null, expire);

        // STEP-06 生成明文 → 仅持久化 code_hash（CV-004）
        LocalDateTime now = LocalDateTime.now();
        String plaintext = OtpGenerator.numeric(cfg.getOtpLength());
        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setEmail(email);
        otp.setCodeHash(passwordEncoder.encode(plaintext));
        otp.setLength(cfg.getOtpLength());
        otp.setExpiresAt(now.plusMinutes(cfg.getOtpTtlMinutes()));
        otp.setAttempts(0);
        otp.setMaxAttempts(cfg.getOtpMaxAttempts());
        otp.setStatus("pending");
        otp.setLastSentAt(now);
        otp.setVersion(0);
        otpCodeMapper.insert(otp);

        rateLimiter.recordSent(email, ip, cfg.getOtpResendSeconds());

        // STEP-07 发邮件（失败不阻塞主流程，FLOW-15 由 MailSender 内部重试）
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("code", plaintext);
            vars.put("ttl", String.valueOf(cfg.getOtpTtlMinutes()));
            mailSender.send(email, "otp", locale, vars);
        } catch (Exception ignored) {
            // EX-27 邮件失败提示重试但不阻塞（OtpCode 已落库）
        }
        return new SendOtpResult(cfg.getOtpResendSeconds(), cfg.getOtpLength());
    }

    /**
     * FLOW-02 verifyOtp（单事务行锁串行 TX-001）。
     * 约束: STEP-01 FOR UPDATE → STEP-02 过期 → STEP-03 校验 hash（attempts/locked）
     * → STEP-04 归并/建号 → STEP-05 禁用拒签 → STEP-06~10 会话+历史+Redis+新设备。
     */
    @Transactional
    public LoginResult verifyOtp(String rawEmail, String code, LoginContext ctx) {
        String email = normalize(rawEmail);
        // STEP-01~03：纯校验码（行锁 + 过期 + hash + attempts/locked），通过则置 consumed
        consumeValidCode(email, code, ctx);

        // STEP-04 归并/建号（email 渠道 provider_uid=email，email_verified=true）
        MergeService.MergeOutcome outcome = mergeService.resolveOrMerge(
                "email", email, email, true, false, null);
        UserEntity user = outcome.user();

        // STEP-05 禁用拒签
        if ("disabled".equals(user.getStatus()) || "deleted".equals(user.getStatus())
                || "anonymized".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED); // 40301
        }

        // STEP-06 新设备判定
        boolean newDevice = sessionService.isNewDevice(user.getId(), ctx.deviceFingerprint());
        // STEP-07~10 会话 + 历史 + 提交后 Redis + 新设备通知
        TokenPair tokens = sessionService.openStoreSession(
                user.getId(), user.getEmail(), "email", newDevice, ctx);

        return new LoginResult(user, tokens, outcome.newAccount(), newDevice);
    }

    /**
     * BLOCKER-4：纯校验码（不归并、不开会话）。
     * 约束: FLOW-05 bindIdentity（email 分支）/ FLOW-06 changePrimaryEmail 仅需校验 new_email 的 OTP，
     * 不得复用 verifyOtp 的 resolveOrMerge+openStoreSession 全登录管线
     * （否则绑定时凭空建 user→命中 findByProviderUid 抛 40903 产生孤立账户；换主邮箱新建同 email user 违反 uk_user_email 回滚）。
     * 仅执行 STEP-01~03（行锁 / 过期 / hash 校验 / attempts/locked），校验通过置 consumed，不返回 LoginResult。
     * @throws BizException OTP_EXPIRED(41001) / OTP_INVALID(40101) / OTP_LOCKED(41002)
     */
    @Transactional
    public void verifyCodeOnly(String rawEmail, String code) {
        consumeValidCode(normalize(rawEmail), code, LoginContext.empty());
    }

    /**
     * STEP-01~03 共享：行锁取 pending OTP → 过期/hash 校验（attempts++/locked）→ 正确则置 consumed。
     * 供 verifyOtp（登录全管线）与 verifyCodeOnly（仅校验码）复用，保证频控/锁定语义一致。
     */
    private void consumeValidCode(String email, String code, LoginContext ctx) {
        // STEP-01 行锁 SELECT FOR UPDATE
        OtpCodeEntity otp = otpCodeMapper.lockPendingByEmail(email);
        if (otp == null) {
            throw new BizException(ErrorCode.OTP_EXPIRED); // 41001 无 pending
        }
        LocalDateTime now = LocalDateTime.now();
        // STEP-02 过期
        if (otp.getExpiresAt().isBefore(now)) {
            updateStatus(otp.getId(), "expired");
            throw new BizException(ErrorCode.OTP_EXPIRED); // 41001
        }
        // STEP-03 校验 code_hash
        boolean match = passwordEncoder.matches(code, otp.getCodeHash());
        if (!match) {
            int nextAttempts = otp.getAttempts() + 1;
            if (nextAttempts >= otp.getMaxAttempts()) {
                updateStatus(otp.getId(), "locked");
                sessionService.recordFailedLogin(null, email, "email", ctx);
                throw new BizException(ErrorCode.OTP_LOCKED); // 41002
            }
            incrementAttempt(otp.getId());
            sessionService.recordFailedLogin(null, email, "email", ctx);
            Map<String, Object> details = new HashMap<>();
            details.put("remaining_attempts", otp.getMaxAttempts() - nextAttempts);
            throw new BizException(ErrorCode.OTP_INVALID, details); // 40101
        }
        // 正确 → consumed
        updateStatus(otp.getId(), "consumed");
    }

    private void updateStatus(Long id, String status) {
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OtpCodeEntity> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("id", id).set("status", status);
        otpCodeMapper.update(null, uw);
    }

    /** RM-023 incrementAttempt（@version 乐观锁防并发绕过） */
    private void incrementAttempt(Long id) {
        OtpCodeEntity fresh = otpCodeMapper.selectById(id);
        fresh.setAttempts(fresh.getAttempts() + 1);
        otpCodeMapper.updateById(fresh);
    }

    private String normalize(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    public record SendOtpResult(int resendAfterSeconds, int otpLength) {
    }
}
