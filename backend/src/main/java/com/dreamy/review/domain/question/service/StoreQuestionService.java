package com.dreamy.review.domain.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.review.domain.enums.QuestionVisibility;
import com.dreamy.review.domain.question.entity.ProductQuestion;
import com.dreamy.review.domain.question.repository.ProductQuestionRepository;
import com.dreamy.review.dto.ReviewDtos.QuestionPageSnapshot;
import com.dreamy.review.dto.ReviewDtos.StoreQuestionCreate;
import com.dreamy.review.dto.ReviewDtos.StoreQuestionDto;
import com.dreamy.review.infra.ReviewCacheService;
import com.dreamy.review.infra.ReviewCacheService.Family;
import com.dreamy.review.infra.ReviewTxRunner;
import com.dreamy.review.port.CatalogSnapshotPort;
import com.dreamy.review.port.IdentityQueryPort;
import com.dreamy.review.support.FieldErrors;
import com.dreamy.review.support.NameMasker;
import com.dreamy.review.support.ReviewParams;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消费端 Q&A 服务（E-REV-03 列表 / E-REV-04 提问）。
 * L2 TRACE: V-REV-008~011 / TX-REV-010 / CACHE-REV-002 / MAP-REV-004 / CV-REV-005/009/010。
 */
@Service
public class StoreQuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ReviewCacheService cache;
    private final ReviewTxRunner tx;
    private final CatalogSnapshotPort catalogPort;
    private final IdentityQueryPort identityPort;

    public StoreQuestionService(ProductQuestionRepository questionRepository, ReviewCacheService cache,
                                ReviewTxRunner tx, CatalogSnapshotPort catalogPort,
                                IdentityQueryPort identityPort) {
        this.questionRepository = questionRepository;
        this.cache = cache;
        this.tx = tx;
        this.catalogPort = catalogPort;
        this.identityPort = identityPort;
    }

    // ==================== E-REV-03 listStoreQuestions ====================

    /** 公开端点（白名单 GET:/api/store/questions）；商品不存在 → 空页（同 E-REV-01 口径） */
    public Paginated<StoreQuestionDto> listStoreQuestions(Long productId, Integer page, Integer pageSize) {
        // V-REV-008/009
        FieldErrors errors = new FieldErrors();
        Long pid = ReviewParams.parseRequiredProductId(productId, errors);
        int parsedPage = ReviewParams.parsePage(page, errors);
        int parsedSize = ReviewParams.parsePageSize(pageSize, errors);
        errors.throwIfAny();

        // STEP-REV-01 查缓存（TTL 300s，key 不含 locale）
        String cacheKey = pid + ":" + parsedPage + ":" + parsedSize;
        Object cached = cache.get(Family.QUESTIONS, cacheKey);
        if (cached instanceof QuestionPageSnapshot snapshot) {
            return toPaginated(snapshot);
        }

        // STEP-REV-02 双条件过滤（visible AND answered，CV-REV-009；IDX-REV-005）
        Page<ProductQuestion> questionPage = questionRepository.pageVisibleAnsweredByProduct(
                pid, parsedPage, parsedSize);
        // STEP-REV-03 asker 脱敏输出（MAP-REV-004）
        List<StoreQuestionDto> items = questionPage.getRecords().stream()
                .map(q -> toStoreDto(q, true))
                .toList();
        // STEP-REV-04 装配标准 Paginated + 写缓存（空页同样缓存）
        QuestionPageSnapshot snapshot = new QuestionPageSnapshot(items, questionPage.getTotal(),
                parsedPage, parsedSize);
        cache.put(Family.QUESTIONS, cacheKey, snapshot);
        return toPaginated(snapshot);
    }

    // ==================== E-REV-04 createStoreQuestion ====================

    /**
     * StoreBearerAuth。单事务 TX-REV-010；默认 hidden + answer=null（question_answer_flow 初始态
     * unanswered）；不失效缓存不发 MQ（hidden+未回答前台不可见）。
     */
    public StoreQuestionDto createQuestion(Long customerId, StoreQuestionCreate req) {
        // V-REV-010 商品存在且 published（否则 404501 透传）
        FieldErrors errors = new FieldErrors();
        Long pid = ReviewParams.parseRequiredProductId(req.productId(), errors);
        // V-REV-011 question trim 后 1..1000
        String trimmed = req.question() == null ? "" : req.question().trim();
        if (trimmed.isEmpty()) {
            errors.reject("question", "blank");
        } else if (trimmed.length() > 1000) {
            errors.reject("question", "too_long");
        }
        errors.throwIfAny();

        CatalogSnapshotPort.ProductBrief brief = catalogPort.getProductBrief(pid);
        if (brief == null || !brief.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }

        return tx.inTx(() -> {
            // STEP-REV-01 asker 姓名快照（CV-REV-010）
            String asker = identityPort.getUserName(customerId);
            // STEP-REV-02 INSERT（visible='hidden', answer=null）
            ProductQuestion question = new ProductQuestion();
            question.setProductId(pid);
            question.setUserId(customerId);
            question.setAsker(asker);
            question.setQuestion(trimmed);
            question.setAskedAt(LocalDateTime.now());
            question.setVisible(QuestionVisibility.HIDDEN);
            questionRepository.insert(question);
            // 出参：asker 本人回执原样（answer/answer_time 为空）
            return toStoreDto(question, false);
        });
    }

    // ==================== 装配 ====================

    /** MAP-REV-004（mask=true 列表脱敏 / false 本人回执原样）；不暴露 visible/user_id */
    private StoreQuestionDto toStoreDto(ProductQuestion q, boolean mask) {
        String asker = mask ? NameMasker.mask(q.getAsker()) : q.getAsker();
        return new StoreQuestionDto(q.getId(), q.getProductId(), asker, q.getQuestion(),
                q.getAskedAt(), q.getAnswer(), q.getAnswerTime());
    }

    private Paginated<StoreQuestionDto> toPaginated(QuestionPageSnapshot snapshot) {
        Paginated<StoreQuestionDto> paginated = new Paginated<>();
        paginated.setData(snapshot.items());
        paginated.setTotalElements(snapshot.total());
        paginated.setPageNumber(snapshot.page());
        paginated.setPageSize(snapshot.pageSize());
        paginated.setNumberOfElements(snapshot.items().size());
        paginated.setTotalPages(snapshot.pageSize() > 0
                ? (int) Math.ceil((double) snapshot.total() / snapshot.pageSize()) : 0);
        return paginated;
    }
}
