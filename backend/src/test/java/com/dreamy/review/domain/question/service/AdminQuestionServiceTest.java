package com.dreamy.review.domain.question.service;

import com.dreamy.review.domain.enums.QuestionVisibility;
import com.dreamy.review.domain.question.entity.ProductQuestion;
import com.dreamy.review.domain.question.repository.ProductQuestionRepository;
import com.dreamy.review.error.ReviewErrorCode;
import com.dreamy.review.error.ReviewException;
import com.dreamy.review.infra.AfterCommitRunner;
import com.dreamy.review.infra.ReviewAuditRecorder;
import com.dreamy.review.infra.ReviewCacheService;
import com.dreamy.review.mq.ReviewEventPublisher;
import com.dreamy.review.port.CatalogSnapshotPort;
import com.dreamy.review.port.CatalogSnapshotPort.ProductBrief;
import com.dreamy.review.testsupport.ImmediateTxRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台 Q&A 状态机/可见性单元测试。
 * L2 TRACE: TC-REV-016 [P0]（首答自动 visible / 编辑保持现值）/ TC-REV-028 [P0]
 * （save_answer/edit_answer + trim guard）/ TC-REV-006（answer 边界）/ E-REV-15 幂等短路。
 */
@ExtendWith(MockitoExtension.class)
class AdminQuestionServiceTest {

    private static final long PRODUCT = 11L;

    @Mock
    ProductQuestionRepository questionRepository;
    @Mock
    CatalogSnapshotPort catalogPort;
    @Mock
    ReviewCacheService cache;
    @Mock
    ReviewAuditRecorder audit;
    @Mock
    ReviewEventPublisher events;

    AdminQuestionService service;

    @BeforeEach
    void setUp() {
        service = new AdminQuestionService(questionRepository, catalogPort, cache, audit,
                new AfterCommitRunner(), events, new ImmediateTxRunner(), new ObjectMapper());
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

    @Test
    @DisplayName("TC-REV-016/028 [P0]: 首次回答（answer NULL→非空）→ saveAnswer(firstAnswer=true) 自动置 visible")
    void firstAnswerAutoVisible() {
        when(questionRepository.findById(1L)).thenReturn(question(1L, null, QuestionVisibility.HIDDEN));
        service.putAnswer(1L, "  Yes it does.  ");
        verify(questionRepository).saveAnswer(eq(1L), eq("Yes it does."), any(LocalDateTime.class), eq(true));
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_ANSWER), eq("question#1"), anyString());
        verify(cache).invalidateProduct(ReviewCacheService.Family.QUESTIONS, PRODUCT);
        verify(events).publishContentInvalidated(ReviewEventPublisher.TYPE_QUESTION_CHANGED,
                "aurelia-gown", PRODUCT);
    }

    @Test
    @DisplayName("TC-REV-016 [P0]: 编辑回答（answered→answered）→ firstAnswer=false，visible 手动设定值不被覆盖")
    void editAnswerKeepsVisibility() {
        when(questionRepository.findById(2L)).thenReturn(question(2L, "old answer", QuestionVisibility.HIDDEN));
        service.putAnswer(2L, "new answer");
        verify(questionRepository).saveAnswer(eq(2L), eq("new answer"), any(LocalDateTime.class), eq(false));
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
        verify(questionRepository, never()).saveAnswer(anyLong(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("E-REV-15 [P0]: 可见性切换——未回答允许置 visible（CV-REV-009 读路径兜底）；同值幂等短路")
    void visibilityToggleAndIdempotency() {
        when(questionRepository.findById(4L)).thenReturn(question(4L, null, QuestionVisibility.HIDDEN));
        service.patchVisibility(4L, "visible");
        verify(questionRepository).updateVisible(4L, QuestionVisibility.VISIBLE);
        verify(audit).record(eq(ReviewAuditRecorder.ACTION_ANSWER), eq("question#4"), anyString());

        org.mockito.Mockito.clearInvocations(questionRepository, audit, events);
        when(questionRepository.findById(5L)).thenReturn(question(5L, null, QuestionVisibility.VISIBLE));
        service.patchVisibility(5L, "visible");
        verify(questionRepository, never()).updateVisible(anyLong(), any());
        verify(audit, never()).record(anyString(), anyString(), any());
        verify(events, never()).publishContentInvalidated(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("V-REV-034/037 [P0]: 不存在 → 404802；visible 枚举外 → 422801（bs-511）")
    void visibilityValidation() {
        when(questionRepository.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.patchVisibility(99L, "visible"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.QUESTION_NOT_FOUND));
        when(questionRepository.findById(6L)).thenReturn(question(6L, null, QuestionVisibility.HIDDEN));
        assertThatThrownBy(() -> service.patchVisibility(6L, "__invalid__"))
                .isInstanceOfSatisfying(ReviewException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ReviewErrorCode.FIELD_VALIDATION_FAILED));
    }
}
