package com.dreamy.infra.grpc;

import dreamy.mail.v1.GetEmailTemplateRequest;
import dreamy.mail.v1.GetEmailTemplateResponse;
import dreamy.mail.v1.TemplateGateGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * TemplateGate gRPC 客户端(Java backend → Rust server;email_template 已收编 Rust 主库)。
 *
 * 契约(与 proto/dreamy/mail/v1/mail.proto 一致):
 * - locale 未命中由 Rust 侧回退 en;两级均未命中 → NOT_FOUND → Optional.empty()
 *   (调用方维持原直查缺失语义:subject=code、body="")
 * - UNAVAILABLE/DEADLINE_EXCEEDED → {@link IdentityUnavailableException},
 *   进入邮件发送失败重试链(与原直查 DB 故障同语义,不静默吞)
 * - deadline 1s(邮件消费者为异步链路,容忍略高于会话校验)
 *
 * 不设 enabled 开关:常开。
 */
@Component
public class TemplateGateClient {

    private static final Logger log = LoggerFactory.getLogger(TemplateGateClient.class);
    private static final long DEADLINE_MS = 1000;

    private final TemplateGateGrpc.TemplateGateBlockingStub stub;

    public TemplateGateClient(GrpcChannels channels) {
        this.stub = TemplateGateGrpc.newBlockingStub(channels.channel());
    }

    public record Template(String subject, String body) {
    }

    /** 取模板(code+locale;Rust 侧含 en 回退;未命中 → empty) */
    public Optional<Template> getTemplate(String code, String locale) {
        Optional<GetEmailTemplateResponse> resp = callOptional("getEmailTemplate",
                s -> s.getEmailTemplate(GetEmailTemplateRequest.newBuilder()
                        .setCode(code)
                        .setLocale(locale)
                        .build()));
        return resp.map(r -> new Template(r.getSubject(), r.getBody()));
    }

    private <T> Optional<T> callOptional(String rpc, Function<TemplateGateGrpc.TemplateGateBlockingStub, T> fn) {
        try {
            return Optional.of(fn.apply(stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)));
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                log.error("[template-grpc] {} 不可达({}):转 503/失败重试链", rpc, code);
                throw new IdentityUnavailableException(rpc + ":" + code, e);
            }
            if (code == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalStateException("TemplateGate." + rpc + " 参数非法(编码 BUG):"
                        + e.getStatus().getDescription(), e);
            }
            log.error("[template-grpc] {} 未预期错误:{}", rpc, e.getStatus());
            throw new IllegalStateException("TemplateGate." + rpc + " 未预期失败", e);
        }
    }
}
