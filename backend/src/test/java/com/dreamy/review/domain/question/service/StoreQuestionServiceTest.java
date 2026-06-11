package com.dreamy.review.domain.question.service;

import com.dreamy.catalog.error.CatalogException;
import com.dreamy.review.domain.enums.QuestionVisibility;
import com.dreamy.review.domain.question.entity.ProductQuestion;
import com.dreamy.review.domain.question.repository.ProductQuestionRepository;
import com.dreamy.review.dto.ReviewDtos.StoreQuestionCreate;
import com.dreamy.review.dto.ReviewDtos.StoreQuestionDto;
import com.dreamy.review.error.ReviewException;
import com.dreamy.review.infra.ReviewCacheService;
import com.dreamy.review.port.CatalogSnapshotPort;
import com.dreamy.review.port.CatalogSnapshotPort.ProductBrief;
import com.dreamy.review.port.IdentityQueryPort;
import com.dreamy.review.testsupport.ImmediateTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-REV-04 提问提交单元测试。
 * L2 TRACE: V-REV-010/011 / TX-REV-010（visible=hidden + answer=null 初始态）/ TC-REV-006（question 边界）/
 * TC-REV-021 单测面（404501 透传 bs-704）/ TC-REV-035 单测面（201 hidden）。
 */
@ExtendWith(MockitoExtension.class)
class StoreQuestionServiceTest {

    private static final long USER = 77L;
    private static final long PRODUCT = 11L;

    @Mock
    ProductQuestionRepository questionRepository;
    @Mock
    ReviewCacheService cache;
    @Mock
    CatalogSnapshotPort catalogPort;
    @Mock
    IdentityQueryPort identityPort;

    StoreQuestionService service;

    @BeforeEach
    void setUp() {
        service = new StoreQuestionService(questionRepository, cache, new ImmediateTxRunner(),
                catalogPort, identityPort);
        lenient().when(catalogPort.getProductBrief(PRODUCT))
                .thenReturn(new ProductBrief(PRODUCT, "aurelia-gown", "Aurelia Gown", true));
    }

    @Test
    @DisplayName("TC-REV-035 单测面 [P0]: 提交提问 → visible=hidden + answer=null（unanswered 初始态）+ asker 快照原样回执")
    void createDefaultsHidden() {
        when(identityPort.getUserName(USER)).thenReturn("Sophie Reyes");
        StoreQuestionDto dto = service.createQuestion(USER, new StoreQuestionCreate(PRODUCT, "  Does it fit?  "));
        assertThat(dto.answer()).isNull();
        assertThat(dto.answerTime()).isNull();
        // 本人回执 asker 原样（不脱敏）
        assertThat(dto.asker()).isEqualTo("Sophie Reyes");
        verify(questionRepository).insert(org.mockito.ArgumentMatchers.argThat((ProductQuestion q) ->
                q.getVisible() == QuestionVisibility.HIDDEN
                        && q.getAnswer() == null
                        && q.getUserId() == USER
                        && "Does it fit?".equals(q.getQuestion())
                        && q.getAskedAt() != null));
    }

    @Test
    @DisplayName("TC-REV-006 [P0]: question trim 空 / 1001 超长 → 422801；合法 1000 通过")
    void questionBoundaries() {
        assertThatThrownBy(() -> service.createQuestion(USER, new StoreQuestionCreate(PRODUCT, "   ")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.createQuestion(USER, new StoreQuestionCreate(PRODUCT, "a".repeat(1001))))
                .isInstanceOf(ReviewException.class);
        service.createQuestion(USER, new StoreQuestionCreate(PRODUCT, "a".repeat(1000)));
        verify(questionRepository).insert(any(ProductQuestion.class));
    }

    @Test
    @DisplayName("TC-REV-021 单测面 [P0]: 商品不存在/未发布 → 404501 透传（bs-704），不落库")
    void productGuard() {
        when(catalogPort.getProductBrief(PRODUCT)).thenReturn(null);
        assertThatThrownBy(() -> service.createQuestion(USER, new StoreQuestionCreate(PRODUCT, "hi")))
                .isInstanceOf(CatalogException.class);
        verify(questionRepository, never()).insert(any());
    }
}
