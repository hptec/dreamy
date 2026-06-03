package com.dreamy.identity.infra.mail;

import java.util.Map;

/**
 * 邮件发送端口。
 * 约束: FLOW-15（有限重试 3 次指数退避 1s/2s/4s，仍失败→EMAIL_SEND_FAILED 50002）；
 * EmailTemplate 按 (code, locale) 取三语模板缺失回退默认 locale；FUNC-031/034。
 * 实现按 identity.mail.mode=stub|smtp 二选一注入。
 */
public interface MailSender {

    /**
     * 发送模板邮件。
     * @param to       收件邮箱
     * @param code     模板 code（otp/new_device/change_primary/account_deleted）
     * @param locale   en/es/fr
     * @param vars     模板变量（如 {{code}}/{{ttl}}）
     */
    void send(String to, String code, String locale, Map<String, String> vars);
}
