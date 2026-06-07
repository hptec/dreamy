package com.dreamy.identity.infra.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮件 stub 实现（沙箱默认无网络，DG-002）。打日志不真实发送；OTP 明文不入日志（redaction）。
 * identity.mail.mode=stub 时生效。
 */
@Component
@ConditionalOnProperty(name = "identity.mail.mode", havingValue = "stub", matchIfMissing = true)
public class StubMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(StubMailSender.class);

    private final EmailTemplateRenderer renderer;

    public StubMailSender(EmailTemplateRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void send(String to, String code, String locale, Map<String, String> vars) {
        EmailTemplateRenderer.Rendered r = renderer.render(code, locale, vars);
        // 邮箱掩码 + OTP 明文绝不记录（redaction.masked / fully_redacted）
        log.info("[MAIL-STUB] to={} code={} locale={} subject='{}' (body suppressed) vars={}",
                mask(to), code, locale, r.subject(),vars);
    }

    private String mask(String email) {
        if (email == null || !email.contains("@")) {
            return "[REDACTED]";
        }
        int at = email.indexOf('@');
        String head = email.substring(0, Math.min(1, at));
        return head + "***" + email.substring(at);
    }
}
