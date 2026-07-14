package com.dreamy.infra;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.session.entity.UserSession;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.domain.session.repository.UserSessionMapper;
import com.dreamy.enums.AdminStatus;
import com.dreamy.enums.SessionStatus;
import org.springframework.stereotype.Component;

/**
 * 请求链路会话有效性校验器（BLOCKER-1）。
 * 约束: api-detail §0「会话有效性」——store 校验 DB session.status=active；admin 校验
 * admin_session.status=active 且管理员仍为 active。当前请求链不读取 Redis，避免缓存删除失败
 * 或网络分区形成继续授权窗口；登录/撤销仍维护兼容键，供滚动升级期间的旧实例使用。
 */
@Component
public class SessionValidator {

    private final UserSessionMapper userSessionMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final AdminUserMapper adminUserMapper;

    public SessionValidator(UserSessionMapper userSessionMapper,
                            AdminSessionMapper adminSessionMapper,
                            AdminUserMapper adminUserMapper) {
        this.userSessionMapper = userSessionMapper;
        this.adminSessionMapper = adminSessionMapper;
        this.adminUserMapper = adminUserMapper;
    }

    /** store access token：始终以 user_session.status='active' 为准。 */
    public boolean isStoreSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        LambdaQueryWrapper<UserSession> qw = new LambdaQueryWrapper<>();
        qw.eq(UserSession::getTokenId, tokenId)
                .eq(UserSession::getStatus, SessionStatus.ACTIVE);
        return userSessionMapper.selectCount(qw) > 0;
    }

    /** admin token：始终校验 active session 与 active 管理员。 */
    public boolean isAdminSessionValid(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        LambdaQueryWrapper<AdminSession> qw = new LambdaQueryWrapper<>();
        qw.eq(AdminSession::getTokenId, tokenId)
                .eq(AdminSession::getStatus, SessionStatus.ACTIVE);
        AdminSession session = adminSessionMapper.selectOne(qw);
        if (session == null) {
            return false;
        }
        AdminUser admin = adminUserMapper.selectById(session.getAdminId());
        return admin != null && admin.getStatus() == AdminStatus.ACTIVE;
    }
}
