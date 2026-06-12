package com.dreamy.common.domain.service;
import com.dreamy.enums.*;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.domain.user.entity.User;
import com.dreamy.domain.user.entity.UserIdentity;
import com.dreamy.domain.audit.entity.OperationLog;
import com.dreamy.domain.audit.repository.OperationLogMapper;
import com.dreamy.domain.user.repository.UserIdentityMapper;
import com.dreamy.domain.user.repository.UserMapper;
import com.dreamy.domain.user.service.MergeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UT-04 MergeService 账户归并单元测试。
 * 约束: UT-04（email_verified=true 同邮箱→归并；false→40902；provider_uid 命中→幂等）；P0。
 */
@ExtendWith(MockitoExtension.class)
class MergeServiceTest {

    @Mock UserMapper userMapper;
    @Mock UserIdentityMapper identityMapper;
    @Mock OperationLogMapper operationLogMapper;
    @Mock RedissonClient redissonClient;
    @Mock org.redisson.api.RLock rLock;

    @InjectMocks MergeService mergeService;

    /** MergeService.resolveOrMerge 在 email 非空时走 huihao-redis 分布式锁（onIdLock）。
     * 默认 stub：getLock 返回 mock RLock，lock()/unlock() 为 no-op，isLocked() 默认 false。 */
    @org.junit.jupiter.api.BeforeEach
    void stubLock() {
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
    }

    @Test
    @DisplayName("TC-UNIT-010: provider_uid 命中 → 幂等返回既有 User（DR-02）")
    void resolveOrMerge_existingProviderUid_idempotent() {
        UserIdentity existing = identity(1L);
        User user = activeUser(1L);
        when(identityMapper.selectOne(any())).thenReturn(existing);
        when(userMapper.selectById(1L)).thenReturn(user);

        var outcome = mergeService.resolveOrMerge(AuthProvider.GOOGLE, "sub-123", "a@b.com", true, false, null, null, null);

        assertThat(outcome.user().getId()).isEqualTo(1L);
        assertThat(outcome.newAccount()).isFalse();
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("TC-UNIT-011: 同邮箱 email_verified=true → 自动归并 R1")
    void resolveOrMerge_sameEmailVerified_merges() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        User sameEmailUser = activeUser(2L);
        sameEmailUser.setEmailVerified(true);
        when(userMapper.selectOne(any())).thenReturn(sameEmailUser);

        var outcome = mergeService.resolveOrMerge(AuthProvider.GOOGLE, "sub-new", "a@b.com", true, false, null, null, null);

        assertThat(outcome.user().getId()).isEqualTo(2L);
        assertThat(outcome.newAccount()).isFalse();
        verify(identityMapper).insert(any(UserIdentity.class));
        verify(operationLogMapper).insert(any(OperationLog.class)); // TX-002 账户合并审计
    }

    @Test
    @DisplayName("TC-UNIT-012: 同邮箱 email_verified=false → 40902 EMAIL_CONFLICT_UNVERIFIED（EDGE-017）")
    void resolveOrMerge_sameEmailUnverified_throws40902() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        User unverified = activeUser(3L);
        unverified.setEmailVerified(false);
        when(userMapper.selectOne(any())).thenReturn(unverified);

        assertThatThrownBy(() -> mergeService.resolveOrMerge(AuthProvider.GOOGLE, "sub-x", "a@b.com", true, false, null, null, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_CONFLICT_UNVERIFIED));
    }

    @Test
    @DisplayName("TC-UNIT-013: 无同邮箱 User → 新建 user + identity")
    void resolveOrMerge_noExisting_createsNew() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectOne(any())).thenReturn(null);

        var outcome = mergeService.resolveOrMerge(AuthProvider.EMAIL, "new@b.com", "new@b.com", true, false, null, null, null);

        assertThat(outcome.newAccount()).isTrue();
        verify(userMapper).insert(any(User.class));
        verify(identityMapper).insert(any(UserIdentity.class));
    }

    private UserIdentity identity(Long userId) {
        var i = new UserIdentity();
        i.setId(1L);
        i.setUserId(userId);
        i.setProvider(AuthProvider.GOOGLE);
        i.setProviderUid("sub-123");
        return i;
    }

    private User activeUser(Long id) {
        var u = new User();
        u.setId(id);
        u.setEmail("a@b.com");
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        return u;
    }
}
