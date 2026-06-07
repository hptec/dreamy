package com.dreamy.identity.domain.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.audit.entity.OperationLog;
import com.dreamy.identity.domain.audit.repository.OperationLogMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 审计领域服务（operation_log 写入与查询）。
 * 约束: FLOW-17 AOP 审计；RM-100（仅 insert）/RM-101（分页倒序）/RM-102（导出流式）；
 * EDGE-018 只读无 delete；MAP-006 changes JSON 原样 + operator_name 快照。
 */
@Service
public class AuditService {

    /** BLOCKER-5：导出时间窗强制上限（天）。未传 from/to 或跨度超限 → 拒绝，防全表流式拉取 */
    private static final long MAX_EXPORT_WINDOW_DAYS = 92L;

    /** DEC-002：导出分页轮询每页条数，逐页回调后丢弃，堆内常驻仅一页（替代游标流式） */
    private static final int EXPORT_PAGE_SIZE = 1000;

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** RM-100 insert：写审计（action ∈ ck_oplog_action 15 种枚举） */
    public void record(Long operatorId, String operatorName, String action,
                       String target, String ip, String userAgent, String changesJson) {
        OperationLog entry = new OperationLog();
        entry.setOperatorId(operatorId);
        entry.setOperatorName(operatorName);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setIp(ip);
        entry.setUserAgent(userAgent);
        entry.setChanges(changesJson);
        operationLogMapper.insert(entry);
    }

    /** RM-101 pageByFilter：按 action/operator/时间范围筛选，created_at 倒序（idx_oplog_created） */
    public IPage<OperationLog> page(int page, int pageSize, String action, Long operatorId,
                                          LocalDateTime from, LocalDateTime to) {
        return operationLogMapper.selectPage(new Page<>(page, pageSize), buildFilter(action, operatorId, from, to));
    }

    /**
     * RM-102 streamForExport（BLOCKER-5）：分页轮询导出，逐页回调写出，绝不全量物化进堆。
     * 约束: operation_log 百万行/月，原 selectList 全量进 List 必 OOM。
     *
     * DEC-002（ORM 合规重构）：由 native {@code @Select(<script>)} + fetchSize=Integer.MIN_VALUE 游标流式
     * 改为 LambdaQueryWrapper 分页轮询——每页 {@value #EXPORT_PAGE_SIZE} 条、按 id 游标递减（{@code id < lastId}）拉取，
     * 逐页回调消费后即丢弃，堆内常驻仅一页。强制时间窗上限（必传 from/to 且跨度≤{@value #MAX_EXPORT_WINDOW_DAYS} 天）
     * 保证轮询次数有界（防无界全表扫描）。不再持有长事务连接：每页查询独立从连接池借还。
     *
     * @param consumer 逐行回调（如 CSV writer.write）；由调用方负责写出，本方法不缓存行
     */
    public void streamForExport(String action, Long operatorId,
                                LocalDateTime from, LocalDateTime to,
                                Consumer<OperationLog> consumer) {
        enforceExportWindow(from, to);
        Long lastId = null;
        while (true) {
            List<OperationLog> pageRows = fetchExportPage(action, operatorId, from, to, lastId);
            if (pageRows.isEmpty()) {
                break;
            }
            for (OperationLog row : pageRows) {
                consumer.accept(row);
            }
            // 不足一页 → 已到末页，结束轮询
            if (pageRows.size() < EXPORT_PAGE_SIZE) {
                break;
            }
            // id 游标递减：下一页取 id 严格小于本页末条（id 自增，单调对应创建顺序）
            lastId = pageRows.get(pageRows.size() - 1).getId();
        }
    }

    /**
     * DEC-002：导出单页查询——LambdaQueryWrapper + id 游标（{@code id < lastId}）+ LIMIT {@value #EXPORT_PAGE_SIZE}。
     * 按 id 倒序取页，等价原 created_at DESC 导出顺序（id 自增与创建时序单调一致）。
     */
    private List<OperationLog> fetchExportPage(String action, Long operatorId,
                                                     LocalDateTime from, LocalDateTime to, Long lastId) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (action != null) {
            qw.eq(OperationLog::getAction, action);
        }
        if (operatorId != null) {
            qw.eq(OperationLog::getOperatorId, operatorId);
        }
        if (from != null) {
            qw.ge(OperationLog::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(OperationLog::getCreatedAt, to);
        }
        if (lastId != null) {
            qw.lt(OperationLog::getId, lastId);
        }
        qw.orderByDesc(OperationLog::getId)
                .last("LIMIT " + EXPORT_PAGE_SIZE);
        return operationLogMapper.selectList(qw);
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

    private LambdaQueryWrapper<OperationLog> buildFilter(String action, Long operatorId,
                                                               LocalDateTime from, LocalDateTime to) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (action != null) {
            qw.eq(OperationLog::getAction, action);
        }
        if (operatorId != null) {
            qw.eq(OperationLog::getOperatorId, operatorId);
        }
        if (from != null) {
            qw.ge(OperationLog::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(OperationLog::getCreatedAt, to);
        }
        qw.orderByDesc(OperationLog::getCreatedAt);
        return qw;
    }
}
