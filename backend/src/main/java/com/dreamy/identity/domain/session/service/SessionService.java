package com.dreamy.identity.domain.session.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.identity.domain.enums.AuthProvider;
import com.dreamy.identity.domain.enums.LoginOutcome;
import com.dreamy.identity.domain.enums.SessionStatus;
import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.infra.SessionValidityCache;
import com.dreamy.identity.infra.mail.MailSender;
import com.dreamy.identity.domain.audit.entity.LoginHistory;
import com.dreamy.identity.domain.session.entity.UserSession;
import com.dreamy.identity.domain.audit.repository.LoginHistoryMapper;
import com.dreamy.identity.domain.session.repository.UserSessionMapper;
import com.dreamy.identity.security.JwtTokenProvider;
import com.dreamy.identity.security.TokenPair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话领域服务（user_session + login_history + 新设备通知）。
 * 约束: RM-030~043；TX-001（提交后写 Redis）；FLOW-04 滑动续期（RM-034 @version）；
 * FLOW-07/08/12 撤销 + Redis 单级失效（EDGE-023 强一致）；FLOW-14 新设备通知（FUNC-031）。
 */
@Service
public class SessionService {

    private final UserSessionMapper sessionMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final SessionValidityCache validityCache;
    private final MailSender mailSender;
    private final com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper;

    public SessionService(UserSessionMapper sessionMapper,
                          LoginHistoryMapper loginHistoryMapper,
                          JwtTokenProvider jwtTokenProvider,
                          SessionValidityCache validityCache,
                          MailSender mailSender,
                          com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper) {
        this.sessionMapper = sessionMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.validityCache = validityCache;
        this.mailSender = mailSender;
        this.dtoMapper = dtoMapper;
    }

    /** RM-041：is_new_device 判定（idx_login_user_created，设备指纹此前未出现过） */
    public boolean isNewDevice(Long userId, String fingerprint) {
        LambdaQueryWrapper<LoginHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(LoginHistory::getUserId, userId)
                .eq(LoginHistory::getDevice, splitDevice(fingerprint))
                .eq(LoginHistory::getResult, LoginOutcome.SUCCESS);
        return loginHistoryMapper.selectCount(qw) == 0;
    }

    private String splitDevice(String fingerprint) {
        int idx = fingerprint.indexOf('|');
        return idx >= 0 ? fingerprint.substring(0, idx) : fingerprint;
    }

    /**
     * STEP-07/08/09/10：签发 store 会话 + 登录历史 + 提交后写 Redis + 新设备通知。
     * 约束: TX-001（@Transactional，afterCommit 写 Redis/发邮件）。
     */
    @Transactional
    public TokenPair openStoreSession(Long userId, String email, AuthProvider method,
                                      boolean newDevice, LoginContext ctx) {
        TokenPair pair = jwtTokenProvider.issueStoreTokens(String.valueOf(userId), method.name().toLowerCase());
        LocalDateTime now = LocalDateTime.now();

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setTokenId(pair.getTokenId());
        session.setRefreshTokenId(pair.getRefreshTokenId());
        session.setAccessExpiresAt(pair.getAccessExpiresAt());
        session.setRefreshExpiresAt(pair.getRefreshExpiresAt());
        session.setDevice(ctx.device());
        session.setBrowser(ctx.browser());
        session.setIp(ctx.ip());
        session.setLocation(ctx.location());
        session.setIsNewDevice(newDevice);
        session.setMethod(method);
        session.setStatus(SessionStatus.ACTIVE);
        session.setLastActiveAt(now);
        session.setVersion(0);
        sessionMapper.insert(session);

        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setEmail(email);
        history.setMethod(method);
        history.setIp(ctx.ip());
        history.setDevice(ctx.device());
        history.setLocation(ctx.location());
        history.setResult(LoginOutcome.SUCCESS);
        history.setIsNewDevice(newDevice);
        history.setNotified(false);
        loginHistoryMapper.insert(history);

        // STEP-09/10：提交后写 Redis 单级 + 新设备通知（不阻塞主事务）
        afterCommit(() -> {
            validityCache.markValid(pair.getTokenId());
            if (newDevice && email != null) {
                notifyNewDevice(email, ctx, history.getId());
            }
        });
        return pair;
    }

    /** FLOW-02 失败登录记录（result=failed，独立写入） */
    public void recordFailedLogin(Long userId, String email, AuthProvider method, LoginContext ctx) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setEmail(email);
        history.setMethod(method);
        history.setIp(ctx.ip());
        history.setDevice(ctx.device());
        history.setLocation(ctx.location());
        history.setResult(LoginOutcome.FAILED);
        history.setIsNewDevice(false);
        history.setNotified(false);
        loginHistoryMapper.insert(history);
    }

    /** RM-030：refresh 续期（FLOW-04 STEP-01~04 滑动顺延 @version） */
    @Transactional
    public TokenPair refresh(String refreshJti) {
        LambdaQueryWrapper<UserSession> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSession::getRefreshTokenId, refreshJti)
                .eq(UserSession::getStatus, SessionStatus.ACTIVE);
        UserSession session = sessionMapper.selectOne(qw);
        LocalDateTime now = LocalDateTime.now();
        if (session == null || session.getRefreshExpiresAt() == null
                || session.getRefreshExpiresAt().isBefore(now)) {
            throw new BizException(ErrorCode.REFRESH_INVALID); // 40102
        }
        TokenPair pair = jwtTokenProvider.reissueStoreTokens(String.valueOf(session.getUserId()), session.getMethod().name().toLowerCase());
        String oldTokenId = session.getTokenId();
        session.setTokenId(pair.getTokenId());
        session.setRefreshTokenId(pair.getRefreshTokenId());
        session.setAccessExpiresAt(pair.getAccessExpiresAt());
        session.setRefreshExpiresAt(pair.getRefreshExpiresAt());
        session.setLastActiveAt(now);
        sessionMapper.updateById(session); // @version 乐观锁

        afterCommit(() -> {
            validityCache.invalidate(oldTokenId);
            validityCache.markValid(pair.getTokenId());
        });
        return pair;
    }

    /** RM-032：当前 user 的 active 会话列表（idx_session_user_status） */
    public List<UserSession> listActive(Long userId) {
        LambdaQueryWrapper<UserSession> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, SessionStatus.ACTIVE)
                .orderByDesc(UserSession::getCreatedAt);
        return sessionMapper.selectList(qw);
    }

    public UserSession findById(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    /** RM-032 表示层：当前 user 的 active 会话 DTO 列表（MAP-003 is_current 由 currentJti 补，不暴露 Entity） */
    public List<com.dreamy.identity.dto.SessionDTO> listActiveViews(Long userId, String currentJti) {
        return listActive(userId).stream().map(s -> {
            com.dreamy.identity.dto.SessionDTO dto = dtoMapper.toSession(s);
            boolean current = currentJti != null && currentJti.equals(s.getTokenId());
            return new com.dreamy.identity.dto.SessionDTO(
                    dto.id(), dto.device(), dto.browser(), dto.ip(), dto.location(),
                    dto.isNewDevice(), current, dto.lastActiveAt(), dto.createdAt());
        }).toList();
    }

    /** FLOW-07 表示层：按 userId+sessionId 撤销，归属校验下沉（EDGE-009：非归属抛 40300） */
    @Transactional
    public void revoke(Long userId, Long sessionId) {
        UserSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN); // 40300
        }
        revokeEntity(session);
    }

    /** FLOW-07 RM-035：撤销单会话 + DEL Redis（EDGE-009 归属校验由调用方先做） */
    @Transactional
    public void revokeById(Long sessionId) {
        UserSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        revokeEntity(session);
    }

    /** FLOW-07 RM-036：撤销当前 user 除 exceptJti 外全部 active 会话 */
    @Transactional
    public void revokeAllExcept(Long userId, String exceptJti) {
        for (UserSession session : listActive(userId)) {
            if (exceptJti != null && exceptJti.equals(session.getTokenId())) {
                continue;
            }
            revokeEntity(session);
        }
    }

    /** FLOW-08/12 RM-036：撤销当前 user 全部 active 会话（注销/禁用/强制下线） */
    @Transactional
    public void revokeAll(Long userId) {
        revokeAllExcept(userId, null);
    }

    private void revokeEntity(UserSession session) {
        LambdaUpdateWrapper<UserSession> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserSession::getId, session.getId())
                .set(UserSession::getStatus, SessionStatus.REVOKED);
        sessionMapper.update(null, uw);
        String tokenId = session.getTokenId();
        afterCommit(() -> validityCache.invalidate(tokenId));
    }

    private void notifyNewDevice(String email, LoginContext ctx, Long historyId) {
        try {
            mailSender.send(email, "new_device", "en", Map.of(
                    "device", ctx.device() == null ? "Unknown" : ctx.device(),
                    "ip", ctx.ip() == null ? "Unknown" : ctx.ip(),
                    "location", ctx.location() == null ? "Unknown" : ctx.location()));
            LambdaUpdateWrapper<LoginHistory> uw = new LambdaUpdateWrapper<>();
            uw.eq(LoginHistory::getId, historyId)
                    .set(LoginHistory::getNotified, true);
            loginHistoryMapper.update(null, uw); // RM-042 markNotified
        } catch (Exception ignored) {
            // FLOW-14 通知失败不影响登录主流程
        }
    }

    /** 事务提交后回调；无事务上下文时直接执行 */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
