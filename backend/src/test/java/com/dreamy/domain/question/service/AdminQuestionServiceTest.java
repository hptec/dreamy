package com.dreamy.domain.question.service;

import com.dreamy.enums.QuestionBatchAction;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.entity.ProductQuestion;
import com.dreamy.domain.question.repository.ProductQuestionRepository;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewAuditRecorder;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewCatalogSnapshotPort.ProductBrief;
import com.dreamy.testsupport.ReviewImmediateTxRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 后台 Q&A 状态机/可见性/搜索/批量单元测试。
 * L2 TRACE: TC-REV-016 [P0]（首答自动 visible / 编辑保持现值）/ TC-REV-028 [P0]
 * （save_answer/edit_answer + trim guard）/ TC-REV-006（answer 边界）/ E-REV-15 幂等短路 /
 * 列表搜索三件套（对齐评价 search 用例组）/ 回答 CAS 409805 / 撤回幂等 / 批量 skipped 语义。
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final long PRODUCT = 11L;

    @Mock
    ProductQuestionRepository questionRepository;
    @Mock
    ReviewCatalogSnapshotPort catalogPort;
    @Mock
    ReviewAuditRecorder audit;
    @Mock
    com.dreamy.domain.cache.service.CacheInvalidationTaskService cacheTasks;

    AdminQuestionService service;

    @BeforeEach
    void setUp() {
        service = new AdminQuestionService(questionRepository, catalogPort, audit, cacheTasks,
                new ReviewImmediateTxRunner(), new ObjectMapper());
        lenient().when(catalogPort.getProductBriefs(any())).thenReturn(Map.of(
                PRODUCT, new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true)));
        lenient().when(catalogPort.getProductBrief(PRODUCT))
                .thenReturn(new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true));
    }

    private ProductQuestion question(long id, String answer, QuestionVisibility visible) {
        ProductQuestion q = new ProductQuestion();
        q.setId(id);
        q.setProductId(PRODUCT);
        q.setUserId(7L);
        q.setAsker("Sophie R.");
        q.setQuestion("Does it fit?");
        q.setAskedAt(LocalDateTime.now());
        q.setAnswer(answer);
        q.setVisible(visible);
        return q;
    }

    // ==================== E-REV-13 列表（搜索三件套 + unanswered_count 平铺） ====================

    @Test
    @DisplayName("search 关键词经 port 命中商品 id 集合 → 并入 pageByAdminFilter；unanswered_count 平铺")
    void listSearchMergesProductIds() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProductQuestion> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of(question(1L, null, QuestionVisibility.HIDDEN)));
        page.setTotal(1);
        java.util.Set<Long> hits = java.util.Set.of(11L, 12L);
        when(catalogPort.searchProductIdsByKeyword("aurelia")).thenReturn(hits);
        when(questionRepository.pageByAdminFilter(isNull(), isNull(), eq("aurelia"), eq(hits), eq(1), eq(20)))
                .thenReturn(page);
        when(questionRepository.countUnanswered()).thenReturn(3L);

        var dto = service.listAdminQuestions(1, 20, null, "all", "aurelia");

        assertThat(dto.getData()).hasSize(1);
        assertThat(dto.getUnansweredCount()).isEqualTo(3L);
        verify(catalogPort).searchProductIdsByKeyword("aurelia");
    }

    @Test
    @DisplayName("search 无商品命中 → 空集合并入（asker/question/answer 仍可命中，不退化为无条件）")
    void listSearchNoProductHitPassesEmptySet() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProductQuestion> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(catalogPort.searchProductIdsByKeyword("zzz")).thenReturn(java.util.Set.of());
        when(questionRepository.pageByAdminFilter(isNull(), isNull(), eq("zzz"), eq(java.util.Set.of()), eq(1), eq(20)))
                .thenReturn(page);
        when(questionRepository.countUnanswered()).thenReturn(0L);

        service.listAdminQuestions(1, 20, null, null, "zzz");

        verify(questionRepository).pageByAdminFilter(isNull(), isNull(), eq("zzz"), eq(java.util.Set.of()), eq(1), eq(20));
    }

    @Test
    @DisplayName("search 为空 → 不调用商品关键词端口；answered 枚举映射 answer IS [NOT] NULL")
    void listNoSearchSkipsPort() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProductQuestion> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(questionRepository.pageByAdminFilter(isNull(), eq(true), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page);
        when(questionRepository.countUnanswered()).thenReturn(0L);

        service.listAdminQuestions(1, 20, null, "answered", "  ");

        verify(catalogPort, never()).searchProductIdsByKeyword(anyString());
        verify(questionRepository).pageByAdminFilter(isNull(), eq(true), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    @DisplayName("V-REV-031~033: page/page_size/product_id/answered 非法 → 422801")
    void listValidation() {
        assertThatThrownBy(() -> service.listAdminQuestions(0, 20, null, null, null))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.listAdminQuestions(1, 101, null, null, null))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.listAdminQuestions(1, 20, 0L, null, null))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.listAdminQuestions(1, 20, null, "bogus", null))
                .isInstanceOf(ReviewException.class);
        verify(questionRepository, never()).pageByAdminFilter(any(), any(), any(), any(), anyInt(), anyInt());
    }

    // ==================== E-REV-14 回答（状态机 + CAS） ====================

    @Test
    @DisplayName("TC-REV-016/028 [P0]: 首次回答（answer NULL→非空）→ CAS(WHERE answer IS NULL) 自动置 visible")
    void firstAnswerAutoVisible() {
        when(questionRepository.findById(1L)).thenReturn(question(1L, null, QuestionVisibility.HIDDEN));
        when(questionRepository.saveAnswer(eq(1L), eq("Yes it does."), any(LocalDateTime.class), isNull()))
                .thenReturn(1);
        service.putAnswer(1L, "  Yes it does.  ");
        verify(questionRepository).saveAnswer(eq(1L), eq("Yes it does."), any(LocalDateTime.class), isNull());
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_ANSWER), eq("question#1"), anyString());
        verify(cacheTasks).enqueue(anyString(), anyString(), anyString(),
                nullable(Object.class), nullable(String.class), anyList(), nullable(LocalDateTime.class),
                anyMap(), nullable(String.class));
    }

    @Test
    @DisplayName("TC-REV-016 [P0]: 编辑回答（answered→answered）→ CAS(WHERE answer=旧值)，visible 现值不被覆盖")
    void editAnswerKeepsVisibility() {
        when(questionRepository.findById(2L)).thenReturn(question(2L, "old answer", QuestionVisibility.HIDDEN));
        when(questionRepository.saveAnswer(eq(2L), eq("new answer"), any(LocalDateTime.class), eq("old answer")))
                .thenReturn(1);
        service.putAnswer(2L, "new answer");
        verify(questionRepository).saveAnswer(eq(2L), eq("new answer"), any(LocalDateTime.class), eq("old answer"));
    }

    @Test
    @DisplayName("并发冲突（CAS affected=0——他人先写）→ 409805 ANSWER_CONFLICT，不写审计")
    void answerConcurrentConflict() {
        when(questionRepository.findById(3L)).thenReturn(question(3L, "old answer", QuestionVisibility.HIDDEN));
        when(questionRepository.saveAnswer(eq(3L), eq("mine"), any(LocalDateTime.class), eq("old answer")))
                .thenReturn(0);
        assertThatThrownBy(() -> service.putAnswer(3L, "mine"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.ANSWER_CONFLICT));
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("TC-REV-028 [P0]: answer trim 空 → 422801 fields.answer=blank（guard 拒绝）；2001 超长拒绝")
    void answerTrimGuard() {
        when(questionRepository.findById(3L)).thenReturn(question(3L, null, QuestionVisibility.HIDDEN));
        assertThatThrownBy(() -> service.putAnswer(3L, "   "))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
        assertThatThrownBy(() -> service.putAnswer(3L, "a".repeat(2001)))
                .isInstanceOf(ReviewException.class);
        verify(questionRepository, never()).saveAnswer(anyLong(), anyString(), any(), anyString());
    }

    // ==================== 撤回回答（对齐评价 deleteReply） ====================

    @Test
    @DisplayName("撤回回答 → 清空 answer/answer_time + 审计 + 缓存任务；未回答幂等直接返回")
    void deleteAnswerAndIdempotency() {
        when(questionRepository.findById(7L)).thenReturn(question(7L, "old answer", QuestionVisibility.VISIBLE));
        service.deleteAnswer(7L);
        verify(questionRepository).clearAnswer(7L);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_ANSWER), eq("question#7"), anyString());
        verify(cacheTasks).enqueue(anyString(), anyString(), anyString(),
                nullable(Object.class), nullable(String.class), anyList(), nullable(LocalDateTime.class),
                anyMap(), nullable(String.class));

        // 未回答 → 幂等短路（不清空不审计）
        org.mockito.Mockito.clearInvocations(questionRepository, audit, cacheTasks);
        when(questionRepository.findById(8L)).thenReturn(question(8L, null, QuestionVisibility.HIDDEN));
        service.deleteAnswer(8L);
        verify(questionRepository, never()).clearAnswer(anyLong());
        verify(audit, never()).record(anyString(), anyString(), anyString());
    }

    // ==================== E-REV-15 可见性 ====================

    @Test
    @DisplayName("E-REV-15 [P0]: 可见性切换——未回答允许置 visible（CV-REV-009 读路径兜底）；同值幂等短路")
    void visibilityToggleAndIdempotency() {
        when(questionRepository.findById(4L)).thenReturn(question(4L, null, QuestionVisibility.HIDDEN));
        service.patchVisibility(4L, 1);
        verify(questionRepository).updateVisible(4L, QuestionVisibility.VISIBLE);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_ANSWER), eq("question#4"), anyString());

        org.mockito.Mockito.clearInvocations(questionRepository, audit);
        when(questionRepository.findById(5L)).thenReturn(question(5L, null, QuestionVisibility.VISIBLE));
        service.patchVisibility(5L, 1);
        verify(questionRepository, never()).updateVisible(anyLong(), any());
        verify(audit, never()).record(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("V-REV-034/037 [P0]: 不存在 → 404802；visible 枚举外 → 422801（bs-511）")
    void visibilityValidation() {
        when(questionRepository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.patchVisibility(99L, 1))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.QUESTION_NOT_FOUND));
        when(questionRepository.findById(6L)).thenReturn(question(6L, null, QuestionVisibility.HIDDEN));
        assertThatThrownBy(() -> service.patchVisibility(6L, 99))
                .isInstanceOf(ReviewException.class);
    }

    // ==================== 批量可见性（对齐评价 batchSet） ====================

    @Test
    @DisplayName("batch hide×{visible→更新, 已 hidden→skipped}；不存在 id→skipped；ids 校验 ≤200")
    void batchHideSemantics() {
        ProductQuestion vis = question(1L, "a", QuestionVisibility.VISIBLE);
        ProductQuestion hidden = question(2L, "b", QuestionVisibility.HIDDEN);
        when(questionRepository.listByIds(anyCollection()))
                .thenReturn(List.of(vis, hidden));
        when(questionRepository.casBatchVisible(1L, QuestionVisibility.VISIBLE, QuestionVisibility.HIDDEN))
                .thenReturn(1);

        var result = service.batchVisibility(List.of(1L, 2L, 3L), "hide");

        assertThat(result.updatedIds()).containsExactly(1L);
        assertThat(result.skippedIds()).containsExactlyInAnyOrder(2L, 3L);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_BATCH), eq("questions/batch"), anyString());
        verify(cacheTasks).enqueue(anyString(), anyString(), anyString(),
                nullable(Object.class), nullable(String.class), anyList(), nullable(LocalDateTime.class),
                anyMap(), nullable(String.class));
    }

    @Test
    @DisplayName("批量并发漂移（CAS affected=0）→ skipped；action 枚举外 → 422801")
    void batchValidationAndDrift() {
        // 并发漂移：读取时 visible，CAS 时已被切走 → affected=0 → skipped
        ProductQuestion vis = question(1L, "a", QuestionVisibility.VISIBLE);
        when(questionRepository.listByIds(anyCollection())).thenReturn(List.of(vis));
        when(questionRepository.casBatchVisible(1L, QuestionVisibility.VISIBLE, QuestionVisibility.HIDDEN))
                .thenReturn(0);
        var result = service.batchVisibility(List.of(1L), "hide");
        assertThat(result.skippedIds()).containsExactly(1L);

        // 校验
        assertThatThrownBy(() -> service.batchVisibility(List.of(), "hide"))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.batchVisibility(List.of(1L), "bogus"))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.batchVisibility(
                java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList(), "hide"))
                .isInstanceOf(ReviewException.class);
    }
}
