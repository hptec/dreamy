package com.dreamy.domain.user.model;

/**
 * 登录上下文（设备/IP/位置/浏览器），用于会话与登录历史记录。
 * 约束: user_session/login_history 字段；is_new_device 判定（FLOW-14）。
 */
public record LoginContext(String ip, String device, String browser, String location) {

    public static LoginContext empty() {
        return new LoginContext(null, null, null, null);
    }

    /** 设备指纹：device+browser 组合用于 is_new_device 判定（RM-041） */
    public String deviceFingerprint() {
        return (device == null ? "" : device) + "|" + (browser == null ? "" : browser);
    }
}
