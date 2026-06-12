package com.dreamy.domain.contact.service;

import com.dreamy.domain.contact.entity.ContactMessage;
import com.dreamy.domain.contact.repository.ContactMessageRepository;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 联系表单服务（E-MKT-12 submitContactMessage；FLOW-P19③，决策 30——仅落表，无后续流转无邮件）。
 * 无判重：同人多次留言均落表。WAF 限流在 Cloudflare 层（决策 11）。
 * L2 TRACE: V-MKT-012~015 / RM-MKT-141 / TX-MKT-028 / TC-MKT-029。
 */
@Service
public class ContactService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ContactMessageRepository repository;

    public ContactService(ContactMessageRepository repository) {
        this.repository = repository;
    }

    /** E-MKT-12：提交（201） */
    public void submit(String name, String email, String subject, String message) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-012 name 必填 trim 非空 ≤100
        String parsedName = MarketingParams.trimToNull(name);
        if (parsedName == null) {
            errors.reject("name", "required");
        } else if (parsedName.length() > 100) {
            errors.reject("name", "too_long");
        }
        // V-MKT-013 email 必填格式 ≤255
        String parsedEmail = email == null ? null : email.trim();
        if (parsedEmail == null || parsedEmail.isEmpty()) {
            errors.reject("email", "required");
        } else if (parsedEmail.length() > 255 || !EMAIL_PATTERN.matcher(parsedEmail).matches()) {
            errors.reject("email", "format_invalid");
        }
        // V-MKT-014 subject 可选 ≤200
        String parsedSubject = MarketingParams.checkMaxLength(subject, 200, "subject", errors);
        // V-MKT-015 message 必填 trim 非空 ≤5000
        String parsedMessage = MarketingParams.trimToNull(message);
        if (parsedMessage == null) {
            errors.reject("message", "required");
        } else if (parsedMessage.length() > 5000) {
            errors.reject("message", "too_long");
        }
        errors.throwIfAny();
        // STEP-MKT-01 INSERT（无判重，TX-MKT-028 单语句）
        ContactMessage row = new ContactMessage();
        row.setName(parsedName);
        row.setEmail(parsedEmail);
        row.setSubject(parsedSubject);
        row.setMessage(parsedMessage);
        row.setSubmittedAt(LocalDateTime.now());
        repository.insert(row);
    }
}
