package com.dreamy.domain.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.enums.OtpStatus;
import com.dreamy.enums.SessionStatus;
import com.dreamy.enums.UserStatus;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.audit.entity.LoginHistory;
import com.dreamy.domain.otp.entity.OtpCode;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.entity.UserIdentity;
import com.dreamy.domain.session.entity.UserSession;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.domain.audit.repository.LoginHistoryMapper;
import com.dreamy.domain.otp.repository.OtpCodeMapper;
import com.dreamy.domain.user.repository.UserIdentityMapper;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.domain.session.repository.UserSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据保留清理与匿名化定时任务（FLOW-16，每日）。
 * 约束: FUNC-032/033；RM-025（OTP 24h）/RM-037（会话 30d）/RM-043（登录记录 1y）/RM-091（admin_session 30d）；
 * RM-005/006/018（注销 30d 宽限后不可逆匿名化 PII，RI-004 级联）；EDGE-026（operation_log 不删）。
 */
@Service
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);
    private static final int BATCH = 500;

    private final OtpCodeMapper otpCodeMapper;
    private final UserSessionMapper userSessionMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;

    public RetentionScheduler(OtpCodeMapper otpCodeMapper,
                              UserSessionMapper userSessionMapper,
                              AdminSessionMapper adminSessionMapper,
                              LoginHistoryMapper loginHistoryMapper,
                              UserMapper userMapper,
                              UserIdentityMapper identityMapper) {
        this.otpCodeMapper = otpCodeMapper;
        this.userSessionMapper = userSessionMapper;
        this.adminSessionMapper = adminSessionMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
    }

    /** 每日 03:00 执行全部保留清理 + 匿名化（FLOW-16） */
    @Scheduled(cron = "${identity.retention.cron:0 0 3 * * *}")
    public void runDailyRetention() {
        log.info("[RETENTION] daily retention task start");
        cleanOtp();
        cleanRevokedUserSessions();
        cleanRevokedAdminSessions();
        cleanLoginHistory();
        anonymizeExpiredDeletedUsers();
        log.info("[RETENTION] daily retention task done");
    }

    /** RM-025：otp_code 终态后 24h 清（idx_otp_status_created） */
    public void cleanOtp() {
        LocalDateTime cutoff = now().minusHours(24);
        LambdaQueryWrapper<OtpCode> qw = new LambdaQueryWrapper<>();
        qw.in(OtpCode::getStatus, List.of(OtpStatus.CONSUMED, OtpStatus.EXPIRED, OtpStatus.LOCKED))
                .lt(OtpCode::getCreatedAt, cutoff);
        otpCodeMapper.delete(qw);
    }

    /** RM-037：user_session revoked 30d 清（idx_session_status_created） */
    public void cleanRevokedUserSessions() {
        LocalDateTime cutoff = now().minusDays(30);
        LambdaQueryWrapper<UserSession> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSession::getStatus, SessionStatus.REVOKED)
                .lt(UserSession::getCreatedAt, cutoff);
        userSessionMapper.delete(qw);
    }

    /** RM-091：admin_session revoked 30d 清（[INFERRED]） */
    public void cleanRevokedAdminSessions() {
        LocalDateTime cutoff = now().minusDays(30);
        LambdaQueryWrapper<AdminSession> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSession::getStatus, SessionStatus.REVOKED)
                .lt(AdminSession::getCreatedAt, cutoff);
        adminSessionMapper.delete(qw);
    }

    /** RM-043：login_history 1 年清（idx_login_created） */
    public void cleanLoginHistory() {
        LocalDateTime cutoff = now().minusYears(1);
        LambdaQueryWrapper<LoginHistory> qw = new LambdaQueryWrapper<>();
        qw.lt(LoginHistory::getCreatedAt, cutoff);
        loginHistoryMapper.delete(qw);
    }

    /**
     * RM-005/006/018：注销账户 30d 宽限后不可逆匿名化（FUNC-033 RI-004 级联）。
     * 约束: status=deleted AND deleted_at<now-30d → 清 email/name/phone/avatar，status=anonymized；
     * 级联 user_identity provider_uid→anon:{id}，清 identifier/relay_email，connected=0。
     * DEC-004/A2：RM-005 findDeletedBefore 迁移为 LambdaQueryWrapper。
     */
    @Transactional
    public void anonymizeExpiredDeletedUsers() {
        LocalDateTime cutoff = now().minusDays(30);
        // RM-005：idx_user_status_deleted_at
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getStatus, UserStatus.DELETED)
                .lt(User::getDeletedAt, cutoff)
                .last("LIMIT " + BATCH);
        List<User> targets = userMapper.selectList(qw);
        for (User user : targets) {
            anonymizeUser(user.getId());
        }
        if (!targets.isEmpty()) {
            log.info("[RETENTION] anonymized {} accounts", targets.size());
        }
    }

    /** RM-006 + RM-018 单用户匿名化（事务内级联） */
    @Transactional
    public void anonymizeUser(Long userId) {
        LocalDateTime now = now();
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
        uw.eq(User::getId, userId)
                .eq(User::getStatus, UserStatus.DELETED)
                .set(User::getEmail, null)
                .set(User::getName, null)
                .set(User::getPhone, null)
                .set(User::getAvatar, null)
                .set(User::getStatus, UserStatus.ANONYMIZED)
                .set(User::getAnonymized, true)
                .set(User::getAnonymizedAt, now);
        userMapper.update(null, uw);

        // RI-004 级联匿名化凭证 PII（provider_uid→anon:{identityId} 保唯一约束）
        LambdaQueryWrapper<UserIdentity> q = new LambdaQueryWrapper<>();
        q.eq(UserIdentity::getUserId, userId);
        for (UserIdentity identity : identityMapper.selectList(q)) {
            LambdaUpdateWrapper<UserIdentity> iu = new LambdaUpdateWrapper<>();
            iu.eq(UserIdentity::getId, identity.getId())
                    .set(UserIdentity::getProviderUid, "anon:" + identity.getId())
                    .set(UserIdentity::getIdentifier, null)
                    .set(UserIdentity::getRelayEmail, null)
                    .set(UserIdentity::getConnected, false);
            identityMapper.update(null, iu);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
