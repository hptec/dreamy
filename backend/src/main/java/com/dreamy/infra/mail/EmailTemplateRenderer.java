package com.dreamy.infra.mail;

import com.dreamy.infra.grpc.TemplateGateClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 邮件模板渲染器：经 TemplateGate gRPC 取模板（Rust 侧含 en 回退），
 * 本地替换 {{var}} 占位（语义与原直查渲染一致）。
 * 约束: RM-120（uk_template_code_locale 缺失回退,回退已下沉 Rust 侧）；I18N-PLAN。
 * 通道不可达 → IdentityUnavailableException（进邮件发送失败重试链,与原直查 DB 故障同语义）。
 */
@Component
public class EmailTemplateRenderer {

    private final TemplateGateClient templateGateClient;

    public EmailTemplateRenderer(TemplateGateClient templateGateClient) {
        this.templateGateClient = templateGateClient;
    }

    public Rendered render(String code, String locale, Map<String, String> vars) {
        TemplateGateClient.Template tpl = templateGateClient.getTemplate(code, locale)
                .orElse(null);
        String subject = code;
        String body = "";
        if (tpl != null) {
            subject = apply(tpl.subject(), vars);
            body = apply(tpl.body(), vars);
        }
        return new Rendered(subject, body);
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
