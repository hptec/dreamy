package com.dreamy.domain.user.model;

import com.dreamy.domain.user.entity.User;
import com.dreamy.security.TokenPair;

/**
 * 登录结果（verifyOtp/oidcCallback 共用出参来源）。
 * 约束: API 出参 {tokens, user, is_new_account}（identity-api-detail §1.2/1.3）。
 */
public record LoginResult(User user, TokenPair tokens, boolean newAccount, boolean newDevice) {
}
