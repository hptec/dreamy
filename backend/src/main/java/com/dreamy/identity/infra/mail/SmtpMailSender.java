package com.dreamy.identity.infra.mail;

import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.error.InfraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮件 SMTP 实现（identity.mail.mode=smtp）。
 * 约束: RT-001/FLOW-15（重试 3 次指数退避 1s/2s/4s，仍失败→EMAIL_SEND_FAILED 50002）；FUNC-034。
 */
@Component
@ConditionalOnProperty(name = "identity.mail.mode", havingValue = "smtp")
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {1000L, 2000L, 4000L};

    private final JavaMailSender javaMailSender;
    private final EmailTemplateRenderer renderer;
    private final String from;

    public SmtpMailSender(JavaMailSender javaMailSender,
                          EmailTemplateRenderer renderer,
                          org.springframework.core.env.Environment env) {
        this.javaMailSender = javaMailSender;
        this.renderer = renderer;
        this.from = env.getProperty("identity.mail.from", "noreply@dreamy.com");
    }

    @Override
    public void send(String to, String code, String locale, Map<String, String> vars) {
        EmailTemplateRenderer.Rendered r = renderer.render(code, locale, vars);
        Exception last = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(to);
                msg.setSubject(r.subject());
                msg.setText(r.body());
                javaMailSender.send(msg);
                return;
            } catch (Exception ex) {
                last = ex;
                log.warn("[MAIL-SMTP] send failed attempt={} code={}", attempt + 1, code);
                sleep(BACKOFF_MS[attempt]);
            }
        }
        // FLOW-15 重试仍失败
        throw new InfraException(ErrorCode.EMAIL_SEND_FAILED, last);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
