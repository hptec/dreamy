package com.dreamy.identity.common.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.repository.entity.OperationLogEntity;
import com.dreamy.identity.common.repository.mapper.OperationLogMapper;
import com.dreamy.identity.common.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** RM-100 insert：写审计（action ∈ ck_oplog_action 15 种枚举） */
    public void record(String operatorId, String operatorName, String action,
                       String target, String ip, String userAgent, String changesJson) {
        OperationLogEntity entry = new OperationLogEntity();
        entry.setId(IdGenerator.uuid());
        entry.setOperatorId(operatorId);
        entry.setOperatorName(operatorName);
        entry.setAction(action);
        entry.setTarget(target);
        entry.setIp(ip);
        entry.setUserAgent(userAgent);
        entry.setChanges(changesJson);
        entry.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        operationLogMapper.insert(entry);
    }

    /** RM-101 pageByFilter：按 action/operator/时间范围筛选，created_at 倒序（idx_oplog_created） */
    public IPage<OperationLogEntity> page(int page, int pageSize, String action, String operatorId,
                                          OffsetDateTime from, OffsetDateTime to) {
        return operationLogMapper.selectPage(new Page<>(page, pageSize), buildFilter(action, operatorId, from, to));
    }

    /**
     * RM-102 streamForExport（BLOCKER-5）：真流式导出，边查边写 response，绝不全量物化进堆。
     * 约束: operation_log 百万行/月，原 selectList 全量进 List 必 OOM；改用 MyBatis ResultHandler 游标逐行回调。
     * 强制时间窗上限（必传 from/to 且跨度≤{@value #MAX_EXPORT_WINDOW_DAYS} 天），否则 422 校验错误，防全表流式扫描。
     * @Transactional(readOnly) 保证游标拉取期间连接不被提前归还（MySQL streaming ResultSet 需持有连接）。
     *
     * @param consumer 逐行回调（如 CSV writer.write）；由调用方负责写出，本方法不缓存行
     */
    @Transactional(readOnly = true)
    public void streamForExport(String action, String operatorId,
                                OffsetDateTime from, OffsetDateTime to,
                                Consumer<OperationLogEntity> consumer) {
        enforceExportWindow(from, to);
        operationLogMapper.streamByFilter(action, operatorId, from, to,
                resultContext -> consumer.accept(resultContext.getResultObject()));
    }

    /** BLOCKER-5：时间窗校验——必传 from/to，且跨度不超过上限，防无界全表流式 */
    private void enforceExportWindow(OffsetDateTime from, OffsetDateTime to) {
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

    private LambdaQueryWrapper<OperationLogEntity> buildFilter(String action, String operatorId,
                                                               OffsetDateTime from, OffsetDateTime to) {
        LambdaQueryWrapper<OperationLogEntity> qw = new LambdaQueryWrapper<>();
        if (action != null) {
            qw.eq(OperationLogEntity::getAction, action);
        }
        if (operatorId != null) {
            qw.eq(OperationLogEntity::getOperatorId, operatorId);
        }
        if (from != null) {
            qw.ge(OperationLogEntity::getCreatedAt, from);
        }
        if (to != null) {
            qw.le(OperationLogEntity::getCreatedAt, to);
        }
        qw.orderByDesc(OperationLogEntity::getCreatedAt);
        return qw;
    }
}
