package com.dreamy.identity.infra.mail;

import com.dreamy.identity.domain.authconfig.entity.EmailTemplateEntity;
import com.dreamy.identity.domain.authconfig.repository.EmailTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮件模板渲染器：按 (code, locale) 取模板，缺失回退默认 locale(en)，替换 {{var}} 占位。
 * 约束: RM-120（uk_template_code_locale 缺失回退）；I18N-PLAN。
 */
@Component
public class EmailTemplateRenderer {

    private static final String DEFAULT_LOCALE = "en";

    private final EmailTemplateMapper templateMapper;

    public EmailTemplateRenderer(EmailTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public Rendered render(String code, String locale, Map<String, String> vars) {
        EmailTemplateEntity tpl = findTemplate(code, locale);
        if (tpl == null) {
            tpl = findTemplate(code, DEFAULT_LOCALE);
        }
        String subject = code;
        String body = "";
        if (tpl != null) {
            subject = apply(tpl.getSubject(), vars);
            body = apply(tpl.getBody(), vars);
        }
        return new Rendered(subject, body);
    }

    private EmailTemplateEntity findTemplate(String code, String locale) {
        LambdaQueryWrapper<EmailTemplateEntity> qw = new LambdaQueryWrapper<>();
        qw.eq(EmailTemplateEntity::getCode, code)
                .eq(EmailTemplateEntity::getLocale, locale)
                .last("LIMIT 1");
        return templateMapper.selectOne(qw);
    }

    private String apply(String text, Map<String, String> vars) {
        if (text == null || vars == null) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    public record Rendered(String subject, String body) {
    }
}
