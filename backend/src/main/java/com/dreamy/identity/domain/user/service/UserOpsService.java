package com.dreamy.identity.domain.user.service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.domain.enums.UserStatus;
import com.dreamy.identity.domain.enums.UserTier;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.audit.entity.LoginHistory;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.domain.user.entity.User;
import com.dreamy.identity.domain.session.entity.UserSession;
import com.dreamy.identity.domain.audit.repository.LoginHistoryMapper;
import com.dreamy.identity.domain.user.repository.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户运营领域服务（admin 侧：listUsers/getUserDetail/toggleUserStatus/forceLogout）。
 * 约束: RM-007 pageByFilter；NP-001 防 N+1 批量查；FLOW-12 禁用/强制下线 + Redis 单级失效（EDGE-023）。
 */
@Service
public class UserOpsService {

    private final UserMapper userMapper;
    private final LoginHistoryMapper loginHistoryMapper;
    private final SessionService sessionService;
    private final IdentityService identityService;
    private final com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper;

    public UserOpsService(UserMapper userMapper,
                          LoginHistoryMapper loginHistoryMapper,
                          SessionService sessionService,
                          IdentityService identityService,
                          com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper) {
        this.userMapper = userMapper;
        this.loginHistoryMapper = loginHistoryMapper;
        this.sessionService = sessionService;
        this.identityService = identityService;
        this.dtoMapper = dtoMapper;
    }

    // ===== 表示层 DTO 组装（Controller 不接触 Entity）=====

    /** listUsers 分页 DTO（含 total/分页元数据） */
    public PageData<com.dreamy.identity.dto.UserProfileDTO> pageUserDTOs(
            int page, int pageSize, UserStatus status, UserTier tier, String emailLike) {
        IPage<User> pg = pageUsers(page, pageSize, status, tier, emailLike);
        List<com.dreamy.identity.dto.UserProfileDTO> items =
                pg.getRecords().stream().map(dtoMapper::toProfile).toList();
        return new PageData<>(items, pg.getTotal(), page, pageSize);
    }

    /** getUserDetail 组装：用户资料 + 登录方式 + 活跃会话 + 登录记录（全 DTO） */
    public UserDetailData userDetail(Long userId, int historyLimit) {
        com.dreamy.identity.dto.UserProfileDTO user = dtoMapper.toProfile(getUser(userId));
        List<com.dreamy.identity.dto.IdentityDTO> identities =
                identities(userId).stream().map(dtoMapper::toIdentity).toList();
        List<com.dreamy.identity.dto.SessionDTO> sessions =
                activeSessions(userId).stream().map(dtoMapper::toSession).toList();
        List<com.dreamy.identity.dto.LoginHistoryDTO> history =
                recentLoginHistory(userId, historyLimit).stream().map(dtoMapper::toLoginHistory).toList();
        return new UserDetailData(user, identities, sessions, history);
    }

    /** toggleUserStatus 后返回资料 DTO */
    public com.dreamy.identity.dto.UserProfileDTO toggleUserStatusDTO(Long userId, UserStatus status) {
        return dtoMapper.toProfile(toggleUserStatus(userId, status));
    }

    public record PageData<T>(List<T> items, long total, int page, int pageSize) {}

    public record UserDetailData(
            com.dreamy.identity.dto.UserProfileDTO user,
            List<com.dreamy.identity.dto.IdentityDTO> identities,
            List<com.dreamy.identity.dto.SessionDTO> sessions,
            List<com.dreamy.identity.dto.LoginHistoryDTO> loginHistory) {}

    /** RM-007 pageByFilter（Customers 页：status/tier/emailLike） */
    public IPage<User> pageUsers(int page, int pageSize, UserStatus status, UserTier tier, String emailLike) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(User::getStatus, status);
        }
        if (tier != null) {
            qw.eq(User::getTier, tier);
        }
        if (emailLike != null && !emailLike.isBlank()) {
            qw.like(User::getEmail, emailLike);
        }
        qw.orderByDesc(User::getCreatedAt);
        return userMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    public User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    /** NP-001：getUserDetail 近期登录历史（idx_login_user_created 单次批量） */
    public List<LoginHistory> recentLoginHistory(Long userId, int limit) {
        LambdaQueryWrapper<LoginHistory> qw = new LambdaQueryWrapper<>();
        qw.eq(LoginHistory::getUserId, userId)
                .orderByDesc(LoginHistory::getCreatedAt)
                .last("LIMIT " + limit);
        return loginHistoryMapper.selectList(qw);
    }

    public List<UserSession> activeSessions(Long userId) {
        return sessionService.listActive(userId);
    }

    public List<com.dreamy.identity.domain.user.entity.UserIdentity> identities(Long userId) {
        return identityService.listAllIdentities(userId);
    }

    /**
     * FLOW-12 toggleUserStatus（FUNC-022）。
     * 约束: TX-005 禁用 → UPDATE user status=disabled + 级联 revoke session + 清 Redis 单级（提交后）；
     * 清缓存 store:user:{userId}。
     */
    @CacheInvalidate(name = "store:user:", key = "#userId")
    @Transactional
    public User toggleUserStatus(Long userId, UserStatus status) {
        User user = getUser(userId);
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == UserStatus.DISABLED) {
            sessionService.revokeAll(userId);
        }
        return user;
    }

    /**
     * FLOW-12 forceLogoutUserSessions（FUNC-022 EDGE-023）。
     * 约束: scope=single → 撤销指定 session；scope=all → 撤销全部；DEL Redis 单级全集群即时生效。
     */
    @Transactional
    public void forceLogout(Long userId, String scope, Long sessionId) {
        if ("single".equals(scope) && sessionId != null) {
            UserSession session = sessionService.findById(sessionId);
            if (session == null || !userId.equals(session.getUserId())) {
                throw new BizException(ErrorCode.NOT_FOUND);
            }
            sessionService.revokeById(sessionId);
        } else {
            sessionService.revokeAll(userId);
        }
    }
}
