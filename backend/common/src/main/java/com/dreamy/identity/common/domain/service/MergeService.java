package com.dreamy.identity.common.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.repository.entity.OperationLogEntity;
import com.dreamy.identity.common.repository.entity.UserEntity;
import com.dreamy.identity.common.repository.entity.UserIdentityEntity;
import com.dreamy.identity.common.repository.mapper.OperationLogMapper;
import com.dreamy.identity.common.repository.mapper.UserIdentityMapper;
import com.dreamy.identity.common.repository.mapper.UserMapper;
import com.dreamy.identity.common.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 账户归并领域服务（FLOW-03 STEP-02~05，复用于 verifyOtp/oidcCallback）。
 * 约束: RM-010（findByProviderUid 幂等核心）；DR-02 归并规则（email_verified 一致自动并 R1，冲突即拒 40902）；
 * TX-002（归并单事务 INSERT identity + operation_log 账户合并）；FUNC-025/028。
 */
@Service
public class MergeService {

    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final OperationLogMapper operationLogMapper;

    public MergeService(UserMapper userMapper,
                        UserIdentityMapper identityMapper,
                        OperationLogMapper operationLogMapper) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 解析/归并/新建 User，返回 (user, isNewAccount)。
     * @param provider     email/google/apple
     * @param providerUid  email=邮箱小写 / OIDC=sub
     * @param email        邮箱（可空，OIDC 提供）
     * @param emailVerified OIDC email_verified（email 渠道恒 true）
     * @param hiddenEmail  Apple Hide My Email
     * @param relayEmail   Apple relay 邮箱
     */
    @Transactional
    public MergeOutcome resolveOrMerge(String provider, String providerUid, String email,
                                       boolean emailVerified, boolean hiddenEmail, String relayEmail) {
        // STEP-02：(provider,provider_uid) 命中即幂等返回既有 User（uk_identity_provider_uid）
        UserIdentityEntity existing = findByProviderUid(provider, providerUid);
        if (existing != null) {
            UserEntity user = userMapper.selectById(existing.getUserId());
            return new MergeOutcome(user, false);
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // STEP-03：凭证不存在 → 查同邮箱 User
        UserEntity sameEmailUser = email == null ? null : userMapper.findByEmailActive(email);
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
        UserEntity user = new UserEntity();
        user.setId(IdGenerator.uuid());
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        user.setTier("regular");
        user.setStatus("active");
        user.setAnonymized(false);
        user.setJoinedAt(now);
        user.setVersion(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        boolean primary = "email".equals(provider);
        insertIdentity(user.getId(), provider, providerUid, email, primary,
                emailVerified, hiddenEmail, relayEmail, now);
        return new MergeOutcome(user, true);
    }

    /** RM-010 findByProviderUid */
    public UserIdentityEntity findByProviderUid(String provider, String providerUid) {
        LambdaQueryWrapper<UserIdentityEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentityEntity::getProvider, provider)
                .eq(UserIdentityEntity::getProviderUid, providerUid)
                .last("LIMIT 1");
        return identityMapper.selectOne(qw);
    }

    private void insertIdentity(String userId, String provider, String providerUid, String identifier,
                                boolean primary, boolean verified, boolean hiddenEmail,
                                String relayEmail, OffsetDateTime now) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(IdGenerator.uuid());
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
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        identityMapper.insert(identity);
    }

    /** TX-002：operator_name=系统 账户合并审计（FLOW-03） */
    private void writeMergeLog(String userId, String email) {
        OperationLogEntity logEntry = new OperationLogEntity();
        logEntry.setId(IdGenerator.uuid());
        logEntry.setOperatorId(null);
        logEntry.setOperatorName("系统");
        logEntry.setAction("账户合并");
        logEntry.setTarget(userId);
        logEntry.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        operationLogMapper.insert(logEntry);
    }

    public record MergeOutcome(UserEntity user, boolean newAccount) {
    }
}
