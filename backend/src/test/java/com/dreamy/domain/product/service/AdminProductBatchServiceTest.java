package com.dreamy.domain.product.service;

import com.dreamy.dto.AdminProductBatchDtos.BatchResult;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.infra.CatalogAuditRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台商品批量操作服务单元测试（API-CAT-01；admin-prototype-alignment）。
 * STUB_SCOPE: 单品 service（行级操作委托面）+ 基建（audit/i18n）。
 * L2 TRACE: V-001/V-002 / STEP-01~04 / TX-CAT-01（行级委托不包整体事务）/ RM-CAT-02c·d / EC-CAT-01 / CV-CAT-01。
 */
@ExtendWith(MockitoExtension.class)
class AdminProductBatchServiceTest {

    @Mock
    AdminProductService adminProductService;
    @Mock
    CatalogAuditRecorder audit;
    @Mock
    CatalogMessageResolver messageResolver;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    AdminProductBatchService service;

    @Test
    @DisplayName("V-001 [P0]: action 缺失/枚举外 → 422501 FIELD_VALIDATION_FAILED")
    void actionValidation() {
        assertThatThrownBy(() -> service.execute(null, List.of(1L)))
                .isInstanceOf(CatalogException.class)
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.execute("archive", List.of(1L)))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        verify(adminProductService, never()).toggleStatus(anyLong(), any());
    }

    @Test
    @DisplayName("V-002 [P0]: ids 缺失/空/超 200 → 422501（超限文案「单次最多 200 件」）")
    void idsValidation() {
        assertThatThrownBy(() -> service.execute("publish", null))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.execute("publish", List.of()))
                .satisfies(ex -> assertThat(((CatalogException) ex).getErrorCode())
                        .isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED));
        List<Long> tooMany = LongStream.rangeClosed(1, 201).boxed().toList();
        assertThatThrownBy(() -> service.execute("publish", tooMany))
                .satisfies(ex -> {
                    CatalogException ce = (CatalogException) ex;
                    assertThat(ce.getErrorCode()).isEqualTo(CatalogErrorCode.FIELD_VALIDATION_FAILED);
                    assertThat(ce.getDetails().toString()).contains("单次最多 200 件");
                });
        verify(adminProductService, never()).toggleStatus(anyLong(), any());
    }

    @Test
    @DisplayName("V-002 [P1]: ids 元素去重——重复 id 仅执行一次行级操作")
    void idsDeduplicated() {
        BatchResult result = service.execute("publish", List.of(1L, 1L, 2L));
        verify(adminProductService, times(1)).toggleStatus(1L, 2);
        verify(adminProductService, times(1)).toggleStatus(2L, 2);
        assertThat(result.successIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("STEP-02 [P0]: publish 逐条容错——部分失败仍 200，failures 含行级码（404501）")
    void publishPartialFailure() {
        lenient().doThrow(new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND))
                .when(adminProductService).toggleStatus(2L, 2);
        when(messageResolver.resolve(eq(CatalogErrorCode.PRODUCT_NOT_FOUND), any())).thenReturn("商品不存在");
        BatchResult result = service.execute("publish", List.of(1L, 2L));
        assertThat(result.successIds()).containsExactly(1L);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).id()).isEqualTo(2L);
        assertThat(result.failures().get(0).errorCode()).isEqualTo(404501);
        assertThat(result.failures().get(0).message()).isEqualTo("商品不存在");
    }

    @Test
    @DisplayName("RM-CAT-02c [P0]: delete 命中已发布 → 行级 409509 进 failures；EC-CAT-01 不存在 → 幂等成功")
    void deleteGuardAndIdempotency() {
        lenient().doThrow(new CatalogException(CatalogErrorCode.PRODUCT_NOT_DELETABLE))
                .when(adminProductService).delete(1L);
        lenient().doThrow(new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND))
                .when(adminProductService).delete(2L);
        when(messageResolver.resolve(eq(CatalogErrorCode.PRODUCT_NOT_DELETABLE), any())).thenReturn("已发布商品需先下架");
        BatchResult result = service.execute("delete", List.of(1L, 2L, 3L));
        // 不存在(2) → 容忍已删除视为成功（BE-DIM-4）；draft(3) → 正常删除成功
        assertThat(result.successIds()).containsExactly(2L, 3L);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).errorCode()).isEqualTo(409509);
    }

    @Test
    @DisplayName("STEP-01 [P1]: action → 单品操作函数映射（unpublish/recommend/unrecommend 委托面）")
    void actionDelegation() {
        service.execute("unpublish", List.of(1L));
        verify(adminProductService).toggleStatus(1L, 1);
        service.execute("recommend", List.of(2L));
        verify(adminProductService).patchFlags(2L, null, null, true, null);
        service.execute("unrecommend", List.of(3L));
        verify(adminProductService).patchFlags(3L, null, null, false, null);
    }

    @Test
    @DisplayName("错误码映射 [P0]: 行级未知异常 → 500500（包内，message 脱敏）")
    void rowInternalError() {
        doThrow(new IllegalStateException("jdbc connection reset: secret-dsn"))
                .when(adminProductService).toggleStatus(1L, 2);
        when(messageResolver.resolve(eq("error.500500"), any())).thenReturn("服务器内部错误");
        BatchResult result = service.execute("publish", List.of(1L));
        assertThat(result.successIds()).isEmpty();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).errorCode()).isEqualTo(500500);
        // message 脱敏：不透出异常详情
        assertThat(result.failures().get(0).message()).isEqualTo("服务器内部错误");
    }

    @Test
    @DisplayName("STEP-03 [P0]: 写 OperationLog 一条（action=批量{操作名}，detail 含总数/成功数/失败数）")
    void auditSummaryRecordedOnce() {
        lenient().doThrow(new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND))
                .when(adminProductService).toggleStatus(2L, 2);
        when(messageResolver.resolve(eq(CatalogErrorCode.PRODUCT_NOT_FOUND), any())).thenReturn("商品不存在");
        service.execute("publish", List.of(1L, 2L));
        verify(audit, times(1)).record(eq("批量上架"), eq("商品×2"),
                contains("\"ids_total\":2"));
    }

    @Test
    @DisplayName("STEP-04 [P1]: 全部失败仍正常返回（200 语义，由调用方按 failures 展示）")
    void allFailedStillReturns() {
        doThrow(new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND))
                .when(adminProductService).patchFlags(anyLong(), any(), any(), any(), any());
        lenient().when(messageResolver.resolve(any(CatalogErrorCode.class), any())).thenReturn("商品不存在");
        BatchResult result = service.execute("recommend", List.of(1L, 2L));
        assertThat(result.successIds()).isEmpty();
        assertThat(result.failures()).hasSize(2);
    }
}
