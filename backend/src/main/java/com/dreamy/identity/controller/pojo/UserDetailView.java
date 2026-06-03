package com.dreamy.identity.controller.pojo;

import com.dreamy.identity.dto.IdentityDTO;
import com.dreamy.identity.dto.LoginHistoryDTO;
import com.dreamy.identity.dto.SessionDTO;
import com.dreamy.identity.dto.UserProfileDTO;

import java.util.List;

/**
 * 用户详情出参（getUserDetail，FUNC-022）。组合用户资料 + 登录方式 + 活跃会话 + 登录记录。
 */
public record UserDetailView(
        UserProfileDTO user,
        List<IdentityDTO> identities,
        List<SessionDTO> sessions,
        List<LoginHistoryDTO> loginHistory
) {
}
