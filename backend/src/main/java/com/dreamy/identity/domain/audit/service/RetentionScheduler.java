package com.dreamy.identity.domain.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.identity.domain.session.entity.AdminSessionEntity;
import com.dreamy.identity.domain.audit.entity.LoginHistoryEntity;
import com.dreamy.identity.domain.otp.entity.OtpCodeEntity;
import com.dreamy.identity.domain.user.entity.UserEntity;
import com.dreamy.identity.domain.user.entity.UserIdentityEntity;
import com.dreamy.identity.domain.session.entity.UserSessionEntity;
import com.dreamy.identity.domain.session.repository.AdminSessionMapper;
import com.dreamy.identity.domain.audit.repository.LoginHistoryMapper;
import com.dreamy.identity.domain.otp.repository.OtpCodeMapper;
import com.dreamy.identity.domain.user.repository.UserIdentityMapper;
import com.dreamy.identity.domain.user.repository.UserMapper;
import com.dreamy.identity.domain.session.repository.UserSessionMapper;
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
        LambdaQueryWrapper<OtpCodeEntity> qw = new LambdaQueryWrapper<>();
        qw.in(OtpCodeEntity::getStatus, List.of("consumed", "expired", "locked"))
                .lt(OtpCodeEntity::getCreatedAt, cutoff);
        otpCodeMapper.delete(qw);
    }

    /** RM-037：user_session revoked 30d 清（idx_session_status_created） */
    public void cleanRevokedUserSessions() {
        LocalDateTime cutoff = now().minusDays(30);
        LambdaQueryWrapper<UserSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSessionEntity::getStatus, "revoked")
                .lt(UserSessionEntity::getCreatedAt, cutoff);
        userSessionMapper.delete(qw);
    }

    /** RM-091：admin_session revoked 30d 清（[INFERRED]） */
    public void cleanRevokedAdminSessions() {
        LocalDateTime cutoff = now().minusDays(30);
        LambdaQueryWrapper<AdminSessionEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSessionEntity::getStatus, "revoked")
                .lt(AdminSessionEntity::getCreatedAt, cutoff);
        adminSessionMapper.delete(qw);
    }

    /** RM-043：login_history 1 年清（idx_login_created） */
    public void cleanLoginHistory() {
        LocalDateTime cutoff = now().minusYears(1);
        LambdaQueryWrapper<LoginHistoryEntity> qw = new LambdaQueryWrapper<>();
        qw.lt(LoginHistoryEntity::getCreatedAt, cutoff);
        loginHistoryMapper.delete(qw);
    }

    /**
     * RM-005/006/018：注销账户 30d 宽限后不可逆匿名化（FUNC-033 RI-004 级联）。
     * 约束: status=deleted AND deleted_at<now-30d → 清 email/name/phone/avatar，status=anonymized；
     * 级联 user_identity provider_uid→anon:{id}，清 identifier/relay_email，connected=0。
     */
    @Transactional
    public void anonymizeExpiredDeletedUsers() {
        LocalDateTime cutoff = now().minusDays(30);
        List<UserEntity> targets = userMapper.findDeletedBefore(cutoff, BATCH);
        for (UserEntity user : targets) {
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
        LambdaUpdateWrapper<UserEntity> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserEntity::getId, userId)
                .eq(UserEntity::getStatus, "deleted")
                .set(UserEntity::getEmail, null)
                .set(UserEntity::getName, null)
                .set(UserEntity::getPhone, null)
                .set(UserEntity::getAvatar, null)
                .set(UserEntity::getStatus, "anonymized")
                .set(UserEntity::getAnonymized, true)
                .set(UserEntity::getAnonymizedAt, now);
        userMapper.update(null, uw);

        // RI-004 级联匿名化凭证 PII（provider_uid→anon:{identityId} 保唯一约束）
        LambdaQueryWrapper<UserIdentityEntity> q = new LambdaQueryWrapper<>();
        q.eq(UserIdentityEntity::getUserId, userId);
        for (UserIdentityEntity identity : identityMapper.selectList(q)) {
            LambdaUpdateWrapper<UserIdentityEntity> iu = new LambdaUpdateWrapper<>();
            iu.eq(UserIdentityEntity::getId, identity.getId())
                    .set(UserIdentityEntity::getProviderUid, "anon:" + identity.getId())
                    .set(UserIdentityEntity::getIdentifier, null)
                    .set(UserIdentityEntity::getRelayEmail, null)
                    .set(UserIdentityEntity::getConnected, false);
            identityMapper.update(null, iu);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}
