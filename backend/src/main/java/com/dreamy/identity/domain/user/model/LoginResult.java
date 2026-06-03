package com.dreamy.identity.domain.user.model;

import com.dreamy.identity.domain.user.entity.UserEntity;
import com.dreamy.identity.security.TokenPair;

/**
 * 登录结果（verifyOtp/oidcCallback 共用出参来源）。
 * 约束: API 出参 {tokens, user, is_new_account}（identity-api-detail §1.2/1.3）。
 */
public record LoginResult(UserEntity user, TokenPair tokens, boolean newAccount, boolean newDevice) {
}
