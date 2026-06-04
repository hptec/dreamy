package com.dreamy.identity.domain.otp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import huihao.redis.IdLockSupport;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * OTP 领域服务（发送 FLOW-01 / 校验登录 FLOW-02）。
 * 约束: V-001~004；STEP 全序列；RM-020~024；CV-004（仅存 code_hash）；TX-001（并发控制串行）；
 * 频控 42901/42902；401 40101（remaining_attempts）；410 41001/41002。
 *
 * DEC-001（ORM 合规重构）：OTP 并发控制由 DB 行锁（{@code SELECT ... FOR UPDATE}）改为
 * huihao-redis 分布式锁（IdLockSupport.onIdLock("otp:verify", email)）+ @Version 乐观锁兜底。
 * 锁范围仅覆盖 consumeValidCode（STEP-01~03 校验阶段）；resolveOrMerge/openStoreSession 在锁外执行，
 * 缩短锁持有时间。{@code @Transactional} 仍覆盖完整方法，锁释放先于事务提交（onIdLock 内部 try-finally 保证）。
 */
@Service
public class OtpService implements IdLockSupport {

    /** DEC-001：OTP 验证分布式锁前缀，按 email 加锁（onIdLock("otp:verify", email)） */
    private static final String OTP_VERIFY_LOCK = "otp:verify";

    private final OtpCodeMapper otpCodeMapper;
    private final AuthConfigService authConfigService;
    private final OtpRateLimiter rateLimiter;
    private final MailSender mailSender;
    private final MergeService mergeService;
    private final SessionService sessionService;
    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;
    private final OtpStateWriter otpStateWriter;

    public OtpService(OtpCodeMapper otpCodeMapper,
                      AuthConfigService authConfigService,
                      OtpRateLimiter rateLimiter,
                      MailSender mailSender,
                      MergeService mergeService,
                      SessionService sessionService,
                      PasswordEncoder passwordEncoder,
                      RedissonClient redissonClient,
                      OtpStateWriter otpStateWriter) {
        this.otpCodeMapper = otpCodeMapper;
        this.authConfigService = authConfigService;
        this.rateLimiter = rateLimiter;
        this.mailSender = mailSender;
        this.mergeService = mergeService;
        this.sessionService = sessionService;
        this.passwordEncoder = passwordEncoder;
        this.redissonClient = redissonClient;
        this.otpStateWriter = otpStateWriter;
    }

    @Override
    public RedissonClient getRedissonClient() {
        return redissonClient;
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
        otpCodeMapper.update(null,
                Wrappers.<OtpCodeEntity>lambdaUpdate()
                        .eq(OtpCodeEntity::getEmail, email)
                        .eq(OtpCodeEntity::getStatus, "pending")
                        .set(OtpCodeEntity::getStatus, "expired"));

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
     * FLOW-02 verifyOtp（单事务 TX-001）。
     * 约束: STEP-01 取 pending OTP → STEP-02 过期 → STEP-03 校验 hash（attempts/locked）
     * → STEP-04 归并/建号 → STEP-05 禁用拒签 → STEP-06~10 会话+历史+Redis+新设备。
     *
     * DEC-001：并发控制由 DB 行锁改为 huihao-redis 分布式锁。onIdLock("otp:verify", email) 仅包裹
     * consumeValidCode（STEP-01~03 校验阶段，含 attempts++ 乐观锁更新），归并/开会话在锁外执行以缩短锁持有时间。
     */
    @Transactional
    public LoginResult verifyOtp(String rawEmail, String code, LoginContext ctx) {
        String email = normalize(rawEmail);
        // STEP-01~03：分布式锁内纯校验码（取 pending + 过期 + hash + attempts/locked），通过则置 consumed
        onIdLock(OTP_VERIFY_LOCK, email, () -> consumeValidCode(email, code, ctx));

        // STEP-04 归并/建号（email 渠道 provider_uid=email，email_verified=true）——锁外执行
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
     * 仅执行 STEP-01~03（分布式锁 / 过期 / hash 校验 / attempts/locked），校验通过置 consumed，不返回 LoginResult。
     *
     * DEC-001：与 verifyOtp 共用 onIdLock("otp:verify", email) 分布式锁，保证按邮箱串行语义一致。
     * @throws BizException OTP_EXPIRED(41001) / OTP_INVALID(40101) / OTP_LOCKED(41002)
     */
    @Transactional
    public void verifyCodeOnly(String rawEmail, String code) {
        String email = normalize(rawEmail);
        onIdLock(OTP_VERIFY_LOCK, email, () -> consumeValidCode(email, code, LoginContext.empty()));
    }

    /**
     * STEP-01~03 共享：取 pending OTP → 过期/hash 校验（attempts++/locked）→ 正确则置 consumed。
     * 供 verifyOtp（登录全管线）与 verifyCodeOnly（仅校验码）复用，保证频控/锁定语义一致。
     *
     * DEC-001：互斥由调用方的 onIdLock("otp:verify", email) 分布式锁保证（替代原 SELECT ... FOR UPDATE 行锁）；
     * pending OTP 查询改用 LambdaQueryWrapper（消除 native SQL）；attempts++ 经 @Version 乐观锁兜底防并发绕过。
     */
    private void consumeValidCode(String email, String code, LoginContext ctx) {
        // STEP-01 取最新 pending OTP（DEC-001：LambdaQueryWrapper 替代 @Select ... FOR UPDATE，互斥由 onIdLock 保证）
        OtpCodeEntity otp = selectLatestPending(email);
        if (otp == null) {
            throw new BizException(ErrorCode.OTP_EXPIRED); // 41001 无 pending
        }
        LocalDateTime now = LocalDateTime.now();
        // STEP-02 过期
        if (otp.getExpiresAt().isBefore(now)) {
            otpStateWriter.updateStatus(otp.getId(), "expired", null);
            throw new BizException(ErrorCode.OTP_EXPIRED); // 41001
        }
        // STEP-03 校验 code_hash
        boolean match = passwordEncoder.matches(code, otp.getCodeHash());
        if (!match) {
            int nextAttempts = otp.getAttempts() + 1;
            if (nextAttempts >= otp.getMaxAttempts()) {
                otpStateWriter.updateStatus(otp.getId(), "locked", null);
                sessionService.recordFailedLogin(null, email, "email", ctx);
                throw new BizException(ErrorCode.OTP_LOCKED); // 41002
            }
            otpStateWriter.incrementAttempt(otp.getId());
            sessionService.recordFailedLogin(null, email, "email", ctx);
            Map<String, Object> details = new HashMap<>();
            details.put("remaining_attempts", otp.getMaxAttempts() - nextAttempts);
            throw new BizException(ErrorCode.OTP_INVALID, details); // 40101
        }
        // 正确 → consumed（version 条件防并发双消费）
        otpStateWriter.updateStatus(otp.getId(), "consumed", otp.getVersion());
    }

    /**
     * RM-020 取最新 pending OTP（DEC-001：LambdaQueryWrapper 替代原 lockPendingByEmail 的 native FOR UPDATE）。
     * 排序 created_at DESC 取首条，等价原 SQL 语义，行级互斥转由 onIdLock 分布式锁保证。
     */
    private OtpCodeEntity selectLatestPending(String email) {
        return otpCodeMapper.selectOne(
                Wrappers.<OtpCodeEntity>lambdaQuery()
                        .eq(OtpCodeEntity::getEmail, email)
                        .eq(OtpCodeEntity::getStatus, "pending")
                        .orderByDesc(OtpCodeEntity::getCreatedAt)
                        .last("LIMIT 1"));
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
