package com.dreamy.infra.grpc;

import dreamy.audit.v1.AuditGateGrpc;
import dreamy.audit.v1.ListOperationLogsRequest;
import dreamy.audit.v1.ListOperationLogsResponse;
import dreamy.audit.v1.OperationLogRow;
import dreamy.audit.v1.RecordOperationLogRequest;
import dreamy.audit.v1.StreamOperationLogsRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/**
 * AuditGate gRPC 客户端(Java backend → Rust server;operation_log 已收编 Rust 主库)。
 *
 * 契约(与 proto/dreamy/audit/v1/audit.proto 一致):
 * - record 为 best-effort:任何失败仅 ERROR 日志丢弃,绝不抛出(审计不打断业务主流程,
 *   对齐原 AuditAspect 吞异常语义)
 * - 查询类 deadline 500ms、零重试;UNAVAILABLE/DEADLINE_EXCEEDED →
 *   {@link IdentityUnavailableException}(调用方转 HTTP 503)
 * - INVALID_ARGUMENT → IllegalStateException(编码 BUG,fail fast)
 *
 * 不设 enabled 开关:常开(回滚态业务审计仍走本通道)。
 */
@Component
public class AuditGateClient {

    private static final Logger log = LoggerFactory.getLogger(AuditGateClient.class);
    private static final long DEADLINE_MS = 500;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AuditGateGrpc.AuditGateBlockingStub stub;

    public AuditGateClient(GrpcChannels channels) {
        this.stub = AuditGateGrpc.newBlockingStub(channels.channel());
    }

    /** 行视图(REST DTO 与 CSV 导出共用;created_at 为 ISO-8601 无偏移) */
    public record Row(
            long id, String operatorName, String action, String target,
            String ip, String changes, LocalDateTime createdAt) {
    }

    public record Paged(List<Row> items, long total) {
    }

    /** best-effort 审计写入(失败 ERROR 日志丢弃,不抛出) */
    public void record(Long operatorId, String operatorName, String action, String target,
                       String ip, String userAgent, String changesJson) {
        try {
            RecordOperationLogRequest.Builder b = RecordOperationLogRequest.newBuilder()
                    .setAction(action);
            if (operatorId != null) {
                b.setOperatorId(operatorId);
            }
            if (operatorName != null) {
                b.setOperatorName(operatorName);
            }
            if (target != null) {
                b.setTarget(target);
            }
            if (ip != null) {
                b.setIp(ip);
            }
            if (userAgent != null) {
                b.setUserAgent(userAgent);
            }
            if (changesJson != null) {
                b.setChanges(changesJson);
            }
            stub.withDeadlineAfter(DEADLINE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .recordOperationLog(b.build());
        } catch (StatusRuntimeException e) {
            log.error("[audit-grpc] 审计写入失败({} action={}):丢弃不阻塞业务主流程",
                    e.getStatus().getCode(), action);
        }
    }

    /** 分页查询(ORDER BY id DESC;过滤语义与 REST 端点一致) */
    public Paged page(String action, Long operatorId, LocalDateTime from, LocalDateTime to,
                      int page, int pageSize) {
        ListOperationLogsRequest.Builder b = ListOperationLogsRequest.newBuilder()
                .setPage(page)
                .setPageSize(pageSize);
        if (action != null && !action.isEmpty()) {
            b.setAction(action);
        }
        if (operatorId != null) {
            b.setOperatorId(operatorId);
        }
        if (from != null) {
            b.setFrom(from.format(ISO));
        }
        if (to != null) {
            b.setTo(to.format(ISO));
        }
        ListOperationLogsResponse resp = call("listOperationLogs",
                s -> s.listOperationLogs(b.build()));
        List<Row> items = resp.getItemsList().stream().map(AuditGateClient::toRow).toList();
        return new Paged(items, resp.getTotal());
    }

    /** 导出流(ORDER BY id ASC;from/to 必传,92 天窗口由 AuditService 校验) */
    public Iterator<Row> stream(LocalDateTime from, LocalDateTime to, String action, Long operatorId) {
        StreamOperationLogsRequest.Builder b = StreamOperationLogsRequest.newBuilder()
                .setFrom(from.format(ISO))
                .setTo(to.format(ISO));
        if (action != null && !action.isEmpty()) {
            b.setAction(action);
        }
        if (operatorId != null) {
            b.setOperatorId(operatorId);
        }
        Iterator<OperationLogRow> raw = call("streamOperationLogs",
                s -> s.streamOperationLogs(b.build()));
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return raw.hasNext();
            }

            @Override
            public Row next() {
                return toRow(raw.next());
            }
        };
    }

    private static Row toRow(OperationLogRow r) {
        return new Row(
                r.getId(),
                r.getOperatorName().isEmpty() ? null : r.getOperatorName(),
                r.getAction(),
                r.getTarget().isEmpty() ? null : r.getTarget(),
                r.getIp().isEmpty() ? null : r.getIp(),
                r.getChanges().isEmpty() ? null : r.getChanges(),
                r.getCreatedAt().isEmpty() ? null : LocalDateTime.parse(r.getCreatedAt()));
    }

    private <T> T call(String rpc, Function<AuditGateGrpc.AuditGateBlockingStub, T> fn) {
        try {
            return fn.apply(stub.withDeadlineAfter(DEADLINE_MS, java.util.concurrent.TimeUnit.MILLISECONDS));
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                log.error("[audit-grpc] {} 不可达({}):转 503", rpc, code);
                throw new IdentityUnavailableException(rpc + ":" + code, e);
            }
            if (code == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalStateException("AuditGate." + rpc + " 参数非法(编码 BUG):"
                        + e.getStatus().getDescription(), e);
            }
            log.error("[audit-grpc] {} 未预期错误:{}", rpc, e.getStatus());
            throw new IllegalStateException("AuditGate." + rpc + " 未预期失败", e);
        }
    }
}
