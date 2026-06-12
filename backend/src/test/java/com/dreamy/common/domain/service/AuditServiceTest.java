package com.dreamy.common.domain.service;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.domain.audit.entity.OperationLog;
import com.dreamy.domain.audit.repository.OperationLogMapper;
import com.dreamy.domain.audit.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DEC-002（ORM 合规重构）：操作日志导出分页轮询 + 时间窗强制上限单元测试。
 * 约束: RM-102 分页轮询（每页 1000 条 LambdaQueryWrapper，逐页回调消费，不全量物化进堆 OOM）；
 * 强制 from/to 必传且跨度≤92天，越界 → 40000 VALIDATION_ERROR。
 * STUB_SCOPE: repository_io（OperationLogMapper 为 I/O 边界）。
 * L2 TRACE: RM-102 / DEC-002
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock OperationLogMapper operationLogMapper;
    @InjectMocks AuditService auditService;

    @Test
    @DisplayName("TC-UNIT-060 [P0]: streamForExport 缺 from/to → 40000，不触发查询（DEC-002 防全表扫描）")
    void streamForExport_missingWindow_rejected() {
        assertThatThrownBy(() -> auditService.streamForExport(null, null, null, null, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        // 校验失败必须在查询前短路，不打 DB
        verify(operationLogMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("TC-UNIT-061 [P0]: streamForExport 跨度>92天 → 40000（DEC-002 时间窗上限）")
    void streamForExport_windowTooWide_rejected() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(120);
        assertThatThrownBy(() -> auditService.streamForExport(null, null, from, to, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(operationLogMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("TC-UNIT-062 [P0]: streamForExport 合法窗口 → 逐行回调消费，不足一页即结束（DEC-002 分页轮询）")
    void streamForExport_validWindow_consumesPageRows() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(30);

        // 单页（3 条 < 1000 每页上限）→ 轮询一次即结束
        List<OperationLog> page = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            OperationLog row = new OperationLog();
            row.setId((long) i);
            page.add(row);
        }
        when(operationLogMapper.selectList(any())).thenReturn(page);

        List<Long> consumed = new ArrayList<>();
        auditService.streamForExport("登录", 1L, from, to, row -> consumed.add(row.getId()));

        assertThat(consumed).containsExactly(0L, 1L, 2L);
        // 不足一页 → 仅查询一次，不再翻页
        verify(operationLogMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("TC-UNIT-063 [P0]: streamForExport 满页 → id 游标翻页直到不足一页（DEC-002 分页轮询有界）")
    void streamForExport_fullPage_paginatesUntilExhausted() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(30);

        // 第一页满 1000 条（id 999..0 倒序）→ 触发翻页；第二页空 → 结束
        List<OperationLog> fullPage = new ArrayList<>(1000);
        for (int i = 999; i >= 0; i--) {
            OperationLog row = new OperationLog();
            row.setId((long) i);
            fullPage.add(row);
        }
        when(operationLogMapper.selectList(any()))
                .thenReturn(fullPage)
                .thenReturn(new ArrayList<>());

        List<Long> consumed = new ArrayList<>();
        auditService.streamForExport(null, null, from, to, row -> consumed.add(row.getId()));

        assertThat(consumed).hasSize(1000);
        // 满页触发第二次查询（空页）→ 共 2 次，证明 id 游标翻页且有界终止
        verify(operationLogMapper, times(2)).selectList(any());
    }
}
