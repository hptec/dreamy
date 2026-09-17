package com.dreamy.domain.audit.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.grpc.AuditGateClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * 审计领域服务（operation_log 写入与查询；表已收编 Rust 主库 dreamy_server，
 * 全部读写经 AuditGate gRPC 通道——Java 业务侧审计记录器/AOP/合并日志共用）。
 * 约束: FLOW-17 AOP 审计；RM-100（仅 insert）/RM-101（分页倒序）/RM-102（导出流式）；
 * EDGE-018 只读无 delete；MAP-006 changes JSON 原样 + operator_name 快照。
 */
@Service
public class AuditService {

    /** BLOCKER-5：导出时间窗强制上限（天）。未传 from/to 或跨度超限 → 拒绝，防全表流式拉取 */
    private static final long MAX_EXPORT_WINDOW_DAYS = 92L;

    /** 行视图(Controller DTO 映射与 CSV 导出共用;替代原直查实体) */
    public record OperationLogRow(
            Long id, String operatorName, String action, String target,
            String ip, String userAgent, String changes, LocalDateTime createdAt) {
    }

    private final AuditGateClient auditGateClient;

    public AuditService(AuditGateClient auditGateClient) {
        this.auditGateClient = auditGateClient;
    }

    /** RM-100 insert：写审计（best-effort,失败在 client 侧 ERROR 日志丢弃,不打断主流程） */
    public void record(Long operatorId, String operatorName, String action,
                       String target, String ip, String userAgent, String changesJson) {
        auditGateClient.record(operatorId, operatorName, action, target, ip, userAgent, changesJson);
    }

    /** RM-101 pageByFilter：按 action/operator/时间范围筛选，id 倒序（等价 created_at 倒序） */
    public IPage<OperationLogRow> page(int page, int pageSize, String action, Long operatorId,
                                       LocalDateTime from, LocalDateTime to) {
        AuditGateClient.Paged result = auditGateClient.page(action, operatorId, from, to, page, pageSize);
        Page<OperationLogRow> pg = new Page<>(page, pageSize, result.total());
        pg.setRecords(result.items().stream()
                .map(r -> new OperationLogRow(
                        r.id(), r.operatorName(), r.action(), r.target(),
                        r.ip(), null, r.changes(), r.createdAt()))
                .toList());
        return pg;
    }

    /**
     * RM-102 streamForExport（BLOCKER-5）：gRPC 服务端流式逐行回调，堆内不缓存行。
     * 时间窗上限仍本地强制（必传 from/to 且跨度≤{@value #MAX_EXPORT_WINDOW_DAYS} 天）。
     */
    public void streamForExport(String action, Long operatorId,
                                LocalDateTime from, LocalDateTime to,
                                Consumer<OperationLogRow> consumer) {
        enforceExportWindow(from, to);
        auditGateClient.stream(from, to, action, operatorId)
                .forEachRemaining(r -> consumer.accept(new OperationLogRow(
                        r.id(), r.operatorName(), r.action(), r.target(),
                        r.ip(), null, r.changes(), r.createdAt())));
    }

    /**
     * 导出时间窗前置校验：供 Controller 在写出 CSV 响应头/表头之前调用。
     * 必须先于 response.setContentType("text/csv") 执行——否则校验失败时响应类型已锁死为
     * text/csv，GlobalExceptionHandler 无法写出 JSON 错误体（HttpMessageNotWritableException）。
     */
    public void validateExportWindow(LocalDateTime from, LocalDateTime to) {
        enforceExportWindow(from, to);
    }

    /** BLOCKER-5：时间窗校验——必传 from/to，且跨度不超过上限，防无界全表流式 */
    private void enforceExportWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            // 缺时间窗 → 422，提示必须指定导出范围
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("field", "from/to",
                            "reason", "export requires both from and to within "
                                    + MAX_EXPORT_WINDOW_DAYS + " days"));
        }
        if (from.isAfter(to)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("field", "from/to", "reason", "from must be before to"));
        }
        if (Duration.between(from, to).toDays() > MAX_EXPORT_WINDOW_DAYS) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    java.util.Map.of("field", "from/to",
                            "reason", "export window exceeds " + MAX_EXPORT_WINDOW_DAYS + " days"));
        }
    }
}
