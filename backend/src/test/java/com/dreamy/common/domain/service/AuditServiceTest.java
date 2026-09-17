package com.dreamy.common.domain.service;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.domain.audit.service.AuditService;
import com.dreamy.infra.grpc.AuditGateClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志服务 gRPC 化后单元测试（表收编 Rust 主库,AuditGate 通道）。
 * 约束: RM-102 导出流式（gRPC 服务端流逐行回调,不全量物化进堆）;
 * 强制 from/to 必传且跨度≤92天,越界 → 40000 VALIDATION_ERROR;RM-100 record 透传。
 * STUB_SCOPE: grpc_io（AuditGateClient 为 I/O 边界）。
 * L2 TRACE: RM-100 / RM-102
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock AuditGateClient auditGateClient;
    @InjectMocks AuditService auditService;

    @Test
    @DisplayName("TC-UNIT-060 [P0]: streamForExport 缺 from/to → 40000，不触发查询（防全表扫描）")
    void streamForExport_missingWindow_rejected() {
        assertThatThrownBy(() -> auditService.streamForExport(null, null, null, null, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        // 校验失败必须在查询前短路，不打 gRPC
        verify(auditGateClient, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-061 [P0]: streamForExport 跨度>92天 → 40000（时间窗上限）")
    void streamForExport_windowTooWide_rejected() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(120);
        assertThatThrownBy(() -> auditService.streamForExport(null, null, from, to, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(auditGateClient, never()).stream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-062 [P0]: streamForExport 合法窗口 → gRPC 流逐行回调消费")
    void streamForExport_validWindow_consumesStreamRows() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(30);

        List<AuditGateClient.Row> rows = List.of(
                new AuditGateClient.Row(0L, "op", "登录", "t", "1.2.3.4", null, null),
                new AuditGateClient.Row(1L, "op", "登录", "t", "1.2.3.4", null, null));
        when(auditGateClient.stream(from, to, "登录", 1L)).thenReturn(rows.iterator());

        List<Long> consumed = new java.util.ArrayList<>();
        auditService.streamForExport("登录", 1L, from, to, row -> consumed.add(row.id()));

        assertThat(consumed).containsExactly(0L, 1L);
    }

    @Test
    @DisplayName("TC-UNIT-064 [P1]: record 参数透传（operator/changes 可空,系统操作语义）")
    void record_passesThroughToGate() {
        auditService.record(null, "系统", "账户合并", "3", null, null, null);
        verify(auditGateClient).record(null, "系统", "账户合并", "3", null, null, null);
    }
}
