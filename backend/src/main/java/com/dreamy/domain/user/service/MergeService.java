package com.dreamy.domain.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.enums.AuthProvider;
import com.dreamy.enums.UserStatus;
import com.dreamy.enums.UserTier;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.domain.audit.entity.OperationLog;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.entity.UserIdentity;
import com.dreamy.domain.audit.repository.OperationLogMapper;
import com.dreamy.domain.user.repository.UserIdentityMapper;
import com.dreamy.domain.user.repository.UserMapper;
import huihao.redis.IdLockSupport;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账户归并领域服务（FLOW-03 STEP-02~05，复用于 verifyOtp/oidcCallback）。
 * 约束: RM-010（findByProviderUid 幂等核心）；DR-02 归并规则（email_verified 一致自动并 R1，冲突即拒 40902）；
 * TX-002（归并单事务 INSERT identity + operation_log 账户合并）；FUNC-025/028。
 * 并发：按 email 加 huihao-redis 分布式锁（IdLockSupport），消除多实例下"查同邮箱→新建"的 check-then-act 竞态。
 */
@Service
public class MergeService implements IdLockSupport {

    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final OperationLogMapper operationLogMapper;
    private final RedissonClient redissonClient;

    public MergeService(UserMapper userMapper,
                        UserIdentityMapper identityMapper,
                        OperationLogMapper operationLogMapper,
                        RedissonClient redissonClient) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.operationLogMapper = operationLogMapper;
        this.redissonClient = redissonClient;
    }

    @Override
    public RedissonClient getRedissonClient() {
        return redissonClient;
    }

    /**
     * 解析/归并/新建 User，返回 (user, isNewAccount)。
     * @param provider     email/google/apple
     * @param providerUid  email=邮箱小写 / OIDC=sub
     * @param email        邮箱（可空，OIDC 提供）
     * @param emailVerified OIDC email_verified（email 渠道恒 true）
     * @param hiddenEmail  Apple Hide My Email
     * @param relayEmail   Apple relay 邮箱
     * @param name         OIDC profile claim（可空）
     * @param avatar       OIDC picture claim（可空）
     */
    @Transactional
    public MergeOutcome resolveOrMerge(AuthProvider provider, String providerUid, String email,
                                       boolean emailVerified, boolean hiddenEmail, String relayEmail,
                                       String name, String avatar) {
        // STEP-02：(provider,provider_uid) 命中即幂等返回既有 User（uk_identity_provider_uid）
        UserIdentity existing = findByProviderUid(provider, providerUid);
        if (existing != null) {
            User user = userMapper.selectById(existing.getUserId());
            if (user == null) {
                identityMapper.deleteById(existing.getId());
            } else {
                return new MergeOutcome(user, false);
            }
        }

        // 按 email 加分布式锁：消除多实例并发下"查同邮箱→新建"的 check-then-act 竞态。
        // email 为空时无法按邮箱锁，回退依赖 uk_identity_provider_uid 唯一索引兜底。
        if (email == null) {
            return doResolveOrMerge(provider, providerUid, null, emailVerified, hiddenEmail, relayEmail, name, avatar);
        }
        return onIdLock("identity:merge:email", email,
                () -> doResolveOrMerge(provider, providerUid, email, emailVerified, hiddenEmail, relayEmail, name, avatar));
    }

    private MergeOutcome doResolveOrMerge(AuthProvider provider, String providerUid, String email,
                                          boolean emailVerified, boolean hiddenEmail, String relayEmail,
                                          String name, String avatar) {
        LocalDateTime now = LocalDateTime.now();

        // STEP-03：凭证不存在 → 查同邮箱 User（RM-002 findByEmailActive，DEC-004/A2 LambdaQueryWrapper）
        User sameEmailUser = findByEmailActive(email);
        if (sameEmailUser != null) {
            if (Boolean.TRUE.equals(sameEmailUser.getEmailVerified()) && emailVerified) {
                // 自动归并 R1：INSERT identity 挂既有 User + operation_log(账户合并)
                insertIdentity(sameEmailUser.getId(), provider, providerUid, email, false,
                        emailVerified, hiddenEmail, relayEmail, now);
                writeMergeLog(sameEmailUser.getId(), email);
                return new MergeOutcome(sameEmailUser, false);
            }
            // 未验证冲突 → 不静默合并（EDGE-017/021）
            throw new BizException(ErrorCode.EMAIL_CONFLICT_UNVERIFIED); // 40902
        }

        // 无同邮箱 User → 新建 user + user_identity
        User user = new User();
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        user.setName(name);
        user.setAvatar(avatar);
        user.setTier(UserTier.REGULAR);
        user.setStatus(UserStatus.ACTIVE);
        user.setAnonymized(false);
        user.setJoinedAt(now);
        user.setVersion(0);
        userMapper.insert(user);

        insertIdentity(user.getId(), provider, providerUid, email, true,
                emailVerified, hiddenEmail, relayEmail, now);
        return new MergeOutcome(user, true);
    }

    /** RM-010 findByProviderUid */
    public UserIdentity findByProviderUid(AuthProvider provider, String providerUid) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getProvider, provider)
                .eq(UserIdentity::getProviderUid, providerUid)
                .last("LIMIT 1");
        return identityMapper.selectOne(qw);
    }

    /** RM-002 findByEmailActive：命中 uk_user_email；status≠anonymized（DEC-004/A2 LambdaQueryWrapper） */
    public User findByEmailActive(String email) {
        if (email == null) {
            return null;
        }
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getEmail, email)
                .ne(User::getStatus, UserStatus.ANONYMIZED)
                .last("LIMIT 1");
        return userMapper.selectOne(qw);
    }

    private void insertIdentity(Long userId, AuthProvider provider, String providerUid, String identifier,
                                boolean primary, boolean verified, boolean hiddenEmail,
                                String relayEmail, LocalDateTime now) {
        UserIdentity identity = new UserIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setProviderUid(providerUid);
        identity.setIdentifier(identifier);
        identity.setIsPrimary(primary);
        identity.setVerified(verified);
        identity.setConnected(true);
        identity.setHiddenEmail(hiddenEmail);
        identity.setRelayEmail(relayEmail);
        identity.setRelayValid(relayEmail != null ? Boolean.TRUE : null);
        identity.setBoundAt(now);
        identity.setLastLoginAt(now);
        identityMapper.insert(identity);
    }

    /** TX-002：operator_name=系统 账户合并审计（FLOW-03） */
    private void writeMergeLog(Long userId, String email) {
        OperationLog logEntry = new OperationLog();
        logEntry.setOperatorId(null);
        logEntry.setOperatorName("系统");
        logEntry.setAction("账户合并");
        logEntry.setTarget(String.valueOf(userId));
        operationLogMapper.insert(logEntry);
    }

    public record MergeOutcome(User user, boolean newAccount) {
    }
}
