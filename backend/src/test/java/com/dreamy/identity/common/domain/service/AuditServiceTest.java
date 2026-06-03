package com.dreamy.identity.common.domain.service;

import com.dreamy.identity.error.BizException;
import com.dreamy.identity.error.ErrorCode;
import com.dreamy.identity.domain.audit.entity.OperationLogEntity;
import com.dreamy.identity.domain.audit.repository.OperationLogMapper;
import com.dreamy.identity.domain.audit.service.AuditService;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BLOCKER-5：操作日志导出流式 + 时间窗强制上限单元测试。
 * 约束: RM-102 流式（ResultHandler 逐行回调，不全量物化进堆 OOM）；
 * 强制 from/to 必传且跨度≤92天，越界 → 40000 VALIDATION_ERROR。
 * STUB_SCOPE: repository_io（OperationLogMapper 为 I/O 边界）。
 * L2 TRACE: RM-102
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock OperationLogMapper operationLogMapper;
    @InjectMocks AuditService auditService;

    @Test
    @DisplayName("TC-UNIT-060 [P0]: streamForExport 缺 from/to → 40000，不触发查询（BLOCKER-5 防全表流式）")
    void streamForExport_missingWindow_rejected() {
        assertThatThrownBy(() -> auditService.streamForExport(null, null, null, null, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        // 校验失败必须在查询前短路，不打 DB
        verify(operationLogMapper, never()).streamByFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-061 [P0]: streamForExport 跨度>92天 → 40000（BLOCKER-5 时间窗上限）")
    void streamForExport_windowTooWide_rejected() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(120);
        assertThatThrownBy(() -> auditService.streamForExport(null, null, from, to, e -> {}))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(operationLogMapper, never()).streamByFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-UNIT-062 [P0]: streamForExport 合法窗口 → 逐行回调消费，不全量返回（BLOCKER-5 流式）")
    @SuppressWarnings("unchecked")
    void streamForExport_validWindow_streamsRowByRow() {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(30);

        // 模拟 mapper 逐行回调 ResultHandler（流式语义）
        doAnswer(inv -> {
            ResultHandler<OperationLogEntity> handler = inv.getArgument(4);
            for (int i = 0; i < 3; i++) {
                OperationLogEntity row = new OperationLogEntity();
                row.setId((long) i);
                handler.handleResult(new SingleResultContext<>(row));
            }
            return null;
        }).when(operationLogMapper).streamByFilter(eq("登录"), eq(1L), eq(from), eq(to), any());

        List<Long> consumed = new ArrayList<>();
        auditService.streamForExport("登录", 1L, from, to, row -> consumed.add(row.getId()));

        assertThat(consumed).containsExactly(0L, 1L, 2L);
    }

    /** 测试用最小 ResultContext 实现（模拟 MyBatis 流式逐行上下文） */
    private static final class SingleResultContext<T> implements ResultContext<T> {
        private final T value;

        private SingleResultContext(T value) {
            this.value = value;
        }

        @Override
        public T getResultObject() {
            return value;
        }

        @Override
        public int getResultCount() {
            return 1;
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public void stop() {
            // no-op for test
        }
    }
}
