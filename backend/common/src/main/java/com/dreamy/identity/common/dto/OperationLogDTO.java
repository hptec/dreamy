package com.dreamy.identity.common.dto;

import java.time.OffsetDateTime;

/**
 * 操作日志出参（MAP-006）。changes JSON 原样；operator_name 快照。
 */
public record OperationLogDTO(
        String id,
        String operatorName,
        String action,
        String target,
        String ip,
        String changes,
        OffsetDateTime createdAt
) {
}
