package com.dreamy.identity.it;

import com.dreamy.identity.common.domain.model.LoginContext;
import com.dreamy.identity.common.domain.service.IdentityService;
import com.dreamy.identity.common.repository.entity.UserIdentityEntity;
import com.dreamy.identity.common.repository.mapper.UserIdentityMapper;
import com.dreamy.identity.common.repository.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-02/03：OIDC 归并事务原子性 + 重复回调幂等（真 MySQL，零 Mock）。
 * L0 TRACE: FUNC-004/005, TX-002, EDGE-016/017
 */
class OidcMergeIT extends AbstractIT {

    @Autowired IdentityService identityService;
    @Autowired UserIdentityMapper identityMapper;
    @Autowired UserMapper userMapper;

    private static final String PROVIDER = "google";
    private static final String SUB = "google-sub-it-001";
    private static final String EMAIL = "oidc-it@dreamy.com";
    // StubOidcVerifier 格式: sub|email|emailVerified
    private static final String ID_TOKEN = SUB + "|" + EMAIL + "|true";

    @BeforeEach
    void cleanup() {
        identityMapper.delete(new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getProvider, PROVIDER)
                .eq(UserIdentityEntity::getProviderUid, SUB));
        userMapper.delete(new LambdaQueryWrapper<>());
    }

    @Test
    @DisplayName("TC-IT-002 [P0]: oidcCallback 归并单事务 — identity 挂载 + user 原子（IT-02）")
    void oidcCallback_merge_atomicTransaction() {
        identityService.oidcLogin(PROVIDER, ID_TOKEN, null, LoginContext.empty());

        long identityCount = identityMapper.selectCount(new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getProvider, PROVIDER)
                .eq(UserIdentityEntity::getProviderUid, SUB));
        assertThat(identityCount).isEqualTo(1);

        long userCount = userMapper.selectCount(new LambdaQueryWrapper<>());
        assertThat(userCount).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-IT-003 [P0]: 重复 OIDC 回调幂等 — 唯一索引命中返回既有 user，不重复建号（IT-03）")
    void oidcCallback_duplicate_idempotent() {
        var r1 = identityService.oidcLogin(PROVIDER, ID_TOKEN, null, LoginContext.empty());
        var r2 = identityService.oidcLogin(PROVIDER, ID_TOKEN, null, LoginContext.empty());

        assertThat(r1.user().getId()).isEqualTo(r2.user().getId());

        long identityCount = identityMapper.selectCount(new LambdaQueryWrapper<UserIdentityEntity>()
                .eq(UserIdentityEntity::getProvider, PROVIDER)
                .eq(UserIdentityEntity::getProviderUid, SUB));
        assertThat(identityCount).isEqualTo(1);
    }
}
