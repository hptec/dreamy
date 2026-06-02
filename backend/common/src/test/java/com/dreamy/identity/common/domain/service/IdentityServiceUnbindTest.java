package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.infra.mail.MailSender;
import com.dreamy.identity.common.infra.oidc.OidcVerifier;
import com.dreamy.identity.common.repository.entity.AuthConfigEntity;
import com.dreamy.identity.common.repository.entity.UserIdentityEntity;
import com.dreamy.identity.common.repository.mapper.UserIdentityMapper;
import com.dreamy.identity.common.repository.mapper.UserMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UT-03 User.unbindIdentity 领域不变量单元测试。
 * 约束: UT-03（主邮箱拒绝 40304 / 低于 min_methods 40305 / 合法解绑）；P0。
 * STUB_REASON: 隔离 DB/Redis，仅测 IdentityService 业务逻辑。
 * STUB_SCOPE: repository_io（UserIdentityMapper/AuthConfigService 为 I/O 边界）。
 * L0 TRACE: FUNC-009, EDGE-007/008
 * L2 TRACE: UT-03
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdentityServiceUnbindTest {

    @Mock UserMapper userMapper;
    @Mock UserIdentityMapper identityMapper;
    @Mock MergeService mergeService;
    @Mock SessionService sessionService;
    @Mock AuthConfigService authConfigService;
    @Mock OtpService otpService;
    @Mock OidcVerifier oidcVerifier;
    @Mock MailSender mailSender;

    @InjectMocks IdentityService identityService;

    /** 初始化 MyBatis-Plus lambda 缓存（LambdaUpdateWrapper 需要 TableInfo）。 */
    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new org.apache.ibatis.session.Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, UserIdentityEntity.class);
    }

    private AuthConfigEntity defaultConfig() {
        AuthConfigEntity cfg = new AuthConfigEntity();
        cfg.setMinMethods(1);
        return cfg;
    }

    @Test
    @DisplayName("TC-UNIT-050 [P0]: unbindIdentity 主邮箱 → 40304 PRIMARY_EMAIL_REQUIRED（EDGE-007）")
    void unbindIdentity_primaryEmail_throws40304() {
        // ARRANGE: identity 是主邮箱
        UserIdentityEntity primary = identity("id-1", "user-1", true, true);
        when(identityMapper.selectById("id-1")).thenReturn(primary);

        // ACT + ASSERT: L2 code=40304
        assertThatThrownBy(() -> identityService.unbindIdentity("user-1", "id-1"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PRIMARY_EMAIL_REQUIRED));
    }

    @Test
    @DisplayName("TC-UNIT-051 [P0]: unbindIdentity 低于 min_methods → 40305 MIN_METHODS_REQUIRED（EDGE-008）")
    void unbindIdentity_belowMinMethods_throws40305() {
        // ARRANGE: 非主邮箱，但只有 1 个 connected（解绑后 = 0 < min_methods=1）
        UserIdentityEntity nonPrimary = identity("id-2", "user-1", false, true);
        when(identityMapper.selectById("id-2")).thenReturn(nonPrimary);
        when(authConfigService.getConfig()).thenReturn(defaultConfig());
        // countConnected 返回 1（解绑后 0 < 1）
        when(identityMapper.selectCount(any())).thenReturn(1L);

        // ACT + ASSERT: L2 code=40305
        assertThatThrownBy(() -> identityService.unbindIdentity("user-1", "id-2"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MIN_METHODS_REQUIRED));
    }

    @Test
    @DisplayName("TC-UNIT-052 [P0]: unbindIdentity 合法解绑 → connected=false（FUNC-009）")
    void unbindIdentity_valid_setsConnectedFalse() {
        // ARRANGE: 非主邮箱，有 2 个 connected（解绑后 1 >= min_methods=1）
        UserIdentityEntity nonPrimary = identity("id-3", "user-1", false, true);
        when(identityMapper.selectById("id-3")).thenReturn(nonPrimary);
        when(authConfigService.getConfig()).thenReturn(defaultConfig());
        when(identityMapper.selectCount(any())).thenReturn(2L);
        when(identityMapper.update(any(), any())).thenReturn(1);

        // ACT
        identityService.unbindIdentity("user-1", "id-3");

        // ASSERT: L5 副作用 — update 被调用（connected=false）
        verify(identityMapper).update(any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-053 [P0]: unbindIdentity 归属校验失败 → 40300 FORBIDDEN（EDGE-007）")
    void unbindIdentity_wrongUser_throws40300() {
        // ARRANGE: identity 属于 user-2，但调用方是 user-1
        UserIdentityEntity other = identity("id-4", "user-2", false, true);
        when(identityMapper.selectById("id-4")).thenReturn(other);

        // ACT + ASSERT: L2 code=40300
        assertThatThrownBy(() -> identityService.unbindIdentity("user-1", "id-4"))
                .isInstanceOf(BizException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
    }

    // ===== helpers =====

    private UserIdentityEntity identity(String id, String userId, boolean isPrimary, boolean connected) {
        UserIdentityEntity i = new UserIdentityEntity();
        i.setId(id);
        i.setUserId(userId);
        i.setProvider("email");
        i.setProviderUid("user@example.com");
        i.setIsPrimary(isPrimary);
        i.setConnected(connected);
        return i;
    }
}
