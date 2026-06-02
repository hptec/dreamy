package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.repository.entity.UserEntity;
import com.dreamy.identity.common.repository.entity.UserIdentityEntity;
import com.dreamy.identity.common.repository.mapper.OperationLogMapper;
import com.dreamy.identity.common.repository.mapper.UserIdentityMapper;
import com.dreamy.identity.common.repository.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks MergeService mergeService;

    @Test
    @DisplayName("TC-UNIT-010: provider_uid 命中 → 幂等返回既有 User（DR-02）")
    void resolveOrMerge_existingProviderUid_idempotent() {
        UserIdentityEntity existing = identity("user-1");
        UserEntity user = activeUser("user-1");
        when(identityMapper.selectOne(any())).thenReturn(existing);
        when(userMapper.selectById("user-1")).thenReturn(user);

        var outcome = mergeService.resolveOrMerge("google", "sub-123", "a@b.com", true, false, null);

        assertThat(outcome.user().getId()).isEqualTo("user-1");
        assertThat(outcome.newAccount()).isFalse();
        verify(userMapper, never()).insert(any(UserEntity.class));
    }

    @Test
    @DisplayName("TC-UNIT-011: 同邮箱 email_verified=true → 自动归并 R1")
    void resolveOrMerge_sameEmailVerified_merges() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        UserEntity sameEmailUser = activeUser("user-2");
        sameEmailUser.setEmailVerified(true);
        when(userMapper.findByEmailActive("a@b.com")).thenReturn(sameEmailUser);

        var outcome = mergeService.resolveOrMerge("google", "sub-new", "a@b.com", true, false, null);

        assertThat(outcome.user().getId()).isEqualTo("user-2");
        assertThat(outcome.newAccount()).isFalse();
        verify(identityMapper).insert(any(UserIdentityEntity.class));
        verify(operationLogMapper).insert(any(com.dreamy.identity.common.repository.entity.OperationLogEntity.class)); // TX-002 账户合并审计
    }

    @Test
    @DisplayName("TC-UNIT-012: 同邮箱 email_verified=false → 40902 EMAIL_CONFLICT_UNVERIFIED（EDGE-017）")
    void resolveOrMerge_sameEmailUnverified_throws40902() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        UserEntity unverified = activeUser("user-3");
        unverified.setEmailVerified(false);
        when(userMapper.findByEmailActive("a@b.com")).thenReturn(unverified);

        assertThatThrownBy(() -> mergeService.resolveOrMerge("google", "sub-x", "a@b.com", true, false, null))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_CONFLICT_UNVERIFIED));
    }

    @Test
    @DisplayName("TC-UNIT-013: 无同邮箱 User → 新建 user + identity")
    void resolveOrMerge_noExisting_createsNew() {
        when(identityMapper.selectOne(any())).thenReturn(null);
        when(userMapper.findByEmailActive(any())).thenReturn(null);

        var outcome = mergeService.resolveOrMerge("email", "new@b.com", "new@b.com", true, false, null);

        assertThat(outcome.newAccount()).isTrue();
        verify(userMapper).insert(any(UserEntity.class));
        verify(identityMapper).insert(any(UserIdentityEntity.class));
    }

    private UserIdentityEntity identity(String userId) {
        var i = new UserIdentityEntity();
        i.setId("id-1");
        i.setUserId(userId);
        i.setProvider("google");
        i.setProviderUid("sub-123");
        return i;
    }

    private UserEntity activeUser(String id) {
        var u = new UserEntity();
        u.setId(id);
        u.setEmail("a@b.com");
        u.setStatus("active");
        u.setEmailVerified(true);
        return u;
    }
}
