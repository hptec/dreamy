package com.dreamy.dto;

import java.time.LocalDateTime;

/**
 * 操作日志出参（MAP-006）。changes JSON 原样；operator_name 快照。
 */
public record OperationLogDTO(
        Long id,
        String operatorName,
        String action,
        String target,
        String ip,
        String changes,
        LocalDateTime createdAt
) {
}
