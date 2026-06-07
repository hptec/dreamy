package com.dreamy.identity.domain.user.service;

import com.alicp.jetcache.anno.CacheInvalidate;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.identity.domain.enums.AuthProvider;
import com.dreamy.identity.domain.enums.UserStatus;
import com.dreamy.identity.domain.user.model.LoginContext;
import com.dreamy.identity.domain.user.model.LoginResult;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.infra.mail.MailSender;
import com.dreamy.identity.infra.oidc.OidcResult;
import com.dreamy.identity.infra.oidc.OidcVerifier;
import com.dreamy.identity.domain.authconfig.entity.AuthConfig;
import com.dreamy.identity.domain.authconfig.service.AuthConfigService;
import com.dreamy.identity.domain.otp.service.OtpService;
import com.dreamy.identity.domain.session.service.SessionService;
import com.dreamy.identity.domain.user.entity.User;
import com.dreamy.identity.domain.user.entity.UserIdentity;
import com.dreamy.identity.domain.user.repository.UserIdentityMapper;
import com.dreamy.identity.domain.user.repository.UserMapper;
import com.dreamy.identity.security.TokenPair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 身份领域服务（OIDC 登录归并 / 绑定解绑 / 换主邮箱 / 资料 / 注销）。
 * 约束: FLOW-03/05/06/08；RM-010~018；R2 min_methods；缓存 store:user/identities 两级写失效。
 */
@Service
public class IdentityService {

    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final MergeService mergeService;
    private final SessionService sessionService;
    private final AuthConfigService authConfigService;
    private final OtpService otpService;
    private final OidcVerifier oidcVerifier;
    private final MailSender mailSender;
    private final com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper;

    public IdentityService(UserMapper userMapper,
                           UserIdentityMapper identityMapper,
                           MergeService mergeService,
                           SessionService sessionService,
                           AuthConfigService authConfigService,
                           OtpService otpService,
                           OidcVerifier oidcVerifier,
                           MailSender mailSender,
                           com.dreamy.identity.dto.mapper.IdentityDtoMapper dtoMapper) {
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.mergeService = mergeService;
        this.sessionService = sessionService;
        this.authConfigService = authConfigService;
        this.otpService = otpService;
        this.oidcVerifier = oidcVerifier;
        this.mailSender = mailSender;
        this.dtoMapper = dtoMapper;
    }

    /** FUNC-007 表示层：用户资料 DTO（不暴露 Entity） */
    public com.dreamy.identity.dto.UserProfileDTO getProfileView(Long userId) {
        return dtoMapper.toProfile(getProfile(userId));
    }

    /** FUNC-010 表示层：登录方式 DTO 列表（MAP-002 隐藏 provider_uid） */
    public List<com.dreamy.identity.dto.IdentityDTO> listIdentityViews(Long userId) {
        return listIdentities(userId).stream().map(dtoMapper::toIdentity).toList();
    }

    /** FLOW-05 表示层：绑定后返回最新登录方式 DTO 列表 */
    public List<com.dreamy.identity.dto.IdentityDTO> bindIdentityViews(
            Long userId, AuthProvider provider, String idToken, String email, String code, String ip) {
        return bindIdentity(userId, provider, idToken, email, code, ip)
                .stream().map(dtoMapper::toIdentity).toList();
    }

    /** FLOW-06 表示层：换主邮箱后返回最新登录方式 DTO 列表 */
    public List<com.dreamy.identity.dto.IdentityDTO> changePrimaryEmailViews(
            Long userId, String newEmail, String code) {
        return changePrimaryEmail(userId, newEmail, code)
                .stream().map(dtoMapper::toIdentity).toList();
    }

    /**
     * FLOW-03 oidcCallback（归并单事务 + 外部集成）。
     * 约束: V-005~007；STEP-01 OIDC 验证（超时/不可达由 OidcVerifier 抛 50401/50201）；
     * STEP-02~04 归并（40902 未验证冲突）；STEP-05 禁用 40301；STEP-06 会话签发。
     */
    @Transactional
    public LoginResult oidcLogin(AuthProvider provider, String idToken, String nonce, LoginContext ctx) {
        // V-006 provider 开关
        AuthConfig cfg = authConfigService.getConfig();
        if (provider == AuthProvider.GOOGLE && !Boolean.TRUE.equals(cfg.getGoogleEnabled())) {
            throw new BizException(ErrorCode.PROVIDER_DISABLED); // 40303
        }
        if (provider == AuthProvider.APPLE && !Boolean.TRUE.equals(cfg.getAppleEnabled())) {
            throw new BizException(ErrorCode.PROVIDER_DISABLED); // 40303
        }
        // STEP-01 OIDC 验证（50401/50201 由 verifier 抛 InfraException）
        OidcResult oidc = oidcVerifier.verify(provider.name().toLowerCase(), idToken, nonce);

        // STEP-02~04 归并（命中 provider_uid 幂等；同邮箱已验证自动并；未验证冲突 40902）
        String identifier = oidc.hiddenEmail() ? oidc.relayEmail() : oidc.email();
        MergeService.MergeOutcome outcome = mergeService.resolveOrMerge(
                provider, oidc.sub(), oidc.email(), oidc.emailVerified(),
                oidc.hiddenEmail(), oidc.relayEmail(), oidc.name(), oidc.picture());
        User user = outcome.user();

        // STEP-05 禁用拒签
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED); // 40301
        }

        // STEP-06 会话签发
        boolean newDevice = sessionService.isNewDevice(user.getId(), ctx.deviceFingerprint());
        TokenPair tokens = sessionService.openStoreSession(
                user.getId(), user.getEmail(), provider, newDevice, ctx);
        return new LoginResult(user, tokens, outcome.newAccount(), newDevice);
    }

    /** getProfile（FUNC-007）：读缓存 store:user:{userId} 两级 300s */
    @Cached(name = "store:user:", key = "#userId", cacheType = CacheType.BOTH,
            expire = 300, localExpire = 300)
    public User getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return user;
    }

    /** listIdentities（FUNC-010）：读缓存 store:identities:{userId}，仅 connected=true */
    @Cached(name = "store:identities:", key = "#userId", cacheType = CacheType.BOTH,
            expire = 300, localExpire = 300)
    public List<UserIdentity> listIdentities(Long userId) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getConnected, true);
        return identityMapper.selectList(qw);
    }

    /** RM-011：用户全部凭证（不过滤 connected，用于 admin 详情/计数） */
    public List<UserIdentity> listAllIdentities(Long userId) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getUserId, userId);
        return identityMapper.selectList(qw);
    }

    /**
     * FLOW-05 bindIdentity（FUNC-008）。
     * 约束: STEP-01 OIDC/OTP 校验取 (provider,provider_uid)；STEP-02 占用 40903；STEP-03 INSERT/UPDATE connected。
     */
    @CacheInvalidate(name = "store:identities:", key = "#userId")
    @Transactional
    public List<UserIdentity> bindIdentity(Long userId, AuthProvider provider, String idToken,
                                                 String email, String code, String ip) {
        String providerUid;
        String identifier;
        boolean verified;
        boolean hidden = false;
        String relay = null;
        if (provider == AuthProvider.EMAIL) {
            // BLOCKER-4：仅校验 OTP 码（verifyCodeOnly），不走 verifyOtp 全登录管线。
            // 全管线会 resolveOrMerge 凭空建/归并 user，导致随后 findByProviderUid 命中抛 40903 并产生孤立账户。
            otpService.verifyCodeOnly(email, code);
            providerUid = email == null ? null : email.trim().toLowerCase();
            identifier = providerUid;
            verified = true;
        } else {
            OidcResult oidc = oidcVerifier.verify(provider.name().toLowerCase(), idToken, null);
            providerUid = oidc.sub();
            hidden = oidc.hiddenEmail();
            relay = oidc.relayEmail();
            identifier = hidden ? relay : oidc.email();
            verified = oidc.emailVerified();
        }
        // STEP-02 占用校验
        UserIdentity occupied = mergeService.findByProviderUid(provider, providerUid);
        if (occupied != null && !occupied.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.IDENTITY_TAKEN); // 40903
        }
        LocalDateTime now = LocalDateTime.now();
        if (occupied != null) {
            // STEP-03 重新连接既有凭证
            LambdaUpdateWrapper<UserIdentity> uw = new LambdaUpdateWrapper<>();
            uw.eq(UserIdentity::getId, occupied.getId())
                    .set(UserIdentity::getConnected, true)
                    .set(UserIdentity::getLastLoginAt, now);
            identityMapper.update(null, uw);
        } else {
            UserIdentity identity = new UserIdentity();
            identity.setUserId(userId);
            identity.setProvider(provider);
            identity.setProviderUid(providerUid);
            identity.setIdentifier(identifier);
            identity.setIsPrimary(false);
            identity.setVerified(verified);
            identity.setConnected(true);
            identity.setHiddenEmail(hidden);
            identity.setRelayEmail(relay);
            identity.setRelayValid(relay != null ? Boolean.TRUE : null);
            identity.setBoundAt(now);
            identity.setLastLoginAt(now);
            identityMapper.insert(identity);
        }
        return listConnected(userId);
    }

    /**
     * FLOW-05 unbindIdentity（FUNC-009 R2）。
     * 约束: STEP-01 归属校验 40300；STEP-02 主邮箱 40304；STEP-03 min_methods 40305；STEP-04 connected=false。
     */
    @CacheInvalidate(name = "store:identities:", key = "#userId")
    @Transactional
    public void unbindIdentity(Long userId, Long identityId) {
        UserIdentity identity = identityMapper.selectById(identityId);
        // STEP-01 归属
        if (identity == null || !identity.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN); // 40300
        }
        // STEP-02 主邮箱
        if (Boolean.TRUE.equals(identity.getIsPrimary())) {
            throw new BizException(ErrorCode.PRIMARY_EMAIL_REQUIRED); // 40304
        }
        // STEP-03 min_methods（RM-012 countConnected）
        AuthConfig cfg = authConfigService.getConfig();
        long connected = countConnected(userId);
        if (connected - 1 < cfg.getMinMethods()) {
            throw new BizException(ErrorCode.MIN_METHODS_REQUIRED); // 40305
        }
        // STEP-04 connected=false（RM-015）
        LambdaUpdateWrapper<UserIdentity> uw = new LambdaUpdateWrapper<>();
        uw.eq(UserIdentity::getId, identityId)
                .set(UserIdentity::getConnected, false);
        identityMapper.update(null, uw);
    }

    /**
     * FLOW-06 changePrimaryEmail（FUNC-026 EDGE-020）。
     * 约束: STEP-01 占用 40901；STEP-02 OTP 校验；STEP-03 单事务迁移 is_primary（TX-003 恒一个）；STEP-04 发邮件+失效缓存。
     */
    @CacheInvalidate(name = "store:identities:", key = "#userId")
    @Transactional
    public List<UserIdentity> changePrimaryEmail(Long userId, String newEmail, String code) {
        String normalized = newEmail == null ? null : newEmail.trim().toLowerCase();
        // STEP-01 被他人占用（RM-002 findByEmailActive，DEC-004/A2 LambdaQueryWrapper 替代 native SQL）
        User occupant = mergeService.findByEmailActive(normalized);
        if (occupant != null && !occupant.getId().equals(userId)) {
            throw new BizException(ErrorCode.EMAIL_EXISTS); // 40901
        }
        // STEP-02 OTP 校验（对 new_email）
        // BLOCKER-4：verifyCodeOnly 仅校验码，不归并不开会话。
        // 原 verifyOtp 会对 new_email 新建同 email 的 user，违反 uk_user_email 导致事务回滚。
        otpService.verifyCodeOnly(normalized, code);

        LocalDateTime now = LocalDateTime.now();
        // STEP-03 迁移 is_primary（旧 false）
        LambdaUpdateWrapper<UserIdentity> demote = new LambdaUpdateWrapper<>();
        demote.eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getIsPrimary, true)
                .set(UserIdentity::getIsPrimary, false);
        identityMapper.update(null, demote);

        // 新邮箱凭证 is_primary=true（不存在则建已验证 email 凭证）
        UserIdentity newPrimary = findEmailIdentity(userId, normalized);
        if (newPrimary == null) {
            newPrimary = new UserIdentity();
            newPrimary.setUserId(userId);
            newPrimary.setProvider(AuthProvider.EMAIL);
            newPrimary.setProviderUid(normalized);
            newPrimary.setIdentifier(normalized);
            newPrimary.setIsPrimary(true);
            newPrimary.setVerified(true);
            newPrimary.setConnected(true);
            newPrimary.setBoundAt(now);
            identityMapper.insert(newPrimary);
        } else {
            LambdaUpdateWrapper<UserIdentity> promote = new LambdaUpdateWrapper<>();
            promote.eq(UserIdentity::getId, newPrimary.getId())
                    .set(UserIdentity::getIsPrimary, true)
                    .set(UserIdentity::getVerified, true);
            identityMapper.update(null, promote);
        }
        // 更新 user 主邮箱
        User user = userMapper.selectById(userId);
        String oldEmail = user.getEmail();
        user.setEmail(normalized);
        user.setEmailVerified(true);
        userMapper.updateById(user);

        // STEP-04 发 change_primary 邮件（旧邮箱）
        if (oldEmail != null) {
            try {
                mailSender.send(oldEmail, "change_primary", "en", Map.of("new_email", normalized));
            } catch (Exception ignored) {
                // 不阻塞主流程
            }
        }
        evictUserCache(userId);
        return listConnected(userId);
    }

    /**
     * FLOW-08 deleteAccount（FUNC-027 EDGE-021/026）。
     * 约束: TX-006 单事务 user.deleted + 全 session revoked；提交后清全部 redis；发 account_deleted；匿名化由 FLOW-16 异步。
     */
    @CacheInvalidate(name = "store:identities:", key = "#userId")
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        // STEP-01 软删 user + 撤销全部会话（RM-004 @version / RM-036）
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
        uw.eq(User::getId, userId)
                .set(User::getStatus, UserStatus.DELETED)
                .set(User::getDeletedAt, now);
        userMapper.update(null, uw);
        sessionService.revokeAll(userId);
        evictUserCache(userId);

        // STEP-03 发 account_deleted 邮件
        if (user.getEmail() != null) {
            try {
                mailSender.send(user.getEmail(), "account_deleted", "en", Map.of());
            } catch (Exception ignored) {
                // 不阻塞
            }
        }
    }

    // ===== helpers =====

    private List<UserIdentity> listConnected(Long userId) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getConnected, true);
        return identityMapper.selectList(qw);
    }

    /** RM-012 countConnected */
    public long countConnected(Long userId) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getConnected, true);
        return identityMapper.selectCount(qw);
    }

    private UserIdentity findEmailIdentity(Long userId, String email) {
        LambdaQueryWrapper<UserIdentity> qw = new LambdaQueryWrapper<>();
        qw.eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getProvider, AuthProvider.EMAIL)
                .eq(UserIdentity::getProviderUid, email)
                .last("LIMIT 1");
        return identityMapper.selectOne(qw);
    }

    /** FLOW-06/08 资料缓存失效（store:user:{userId}） */
    @CacheInvalidate(name = "store:user:", key = "#userId")
    public void evictUserCache(Long userId) {
        // 注解驱动失效；方法体无需逻辑
    }
}
