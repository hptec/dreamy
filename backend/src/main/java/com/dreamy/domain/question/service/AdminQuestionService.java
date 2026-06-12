package com.dreamy.domain.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.entity.ProductQuestion;
import com.dreamy.domain.question.repository.ProductQuestionRepository;
import com.dreamy.dto.ReviewDtos.AdminQuestionDto;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewAfterCommitRunner;
import com.dreamy.infra.ReviewAuditRecorder;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.infra.ReviewCacheService.Family;
import com.dreamy.infra.ReviewTxRunner;
import com.dreamy.mq.ReviewEventPublisher;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.support.ReviewFieldErrors;
import com.dreamy.support.PaginatedFactory;
import com.dreamy.support.ReviewParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 Q&A 服务（E-REV-13~15；TX-REV-008/009；TASK-049 question_answer_flow guard 内嵌）。
 * 失效链：`review:questions:{pid}:*` + content.invalidated(type=question_changed)，PDP Q&A 区同步刷新。
 * L2 TRACE: V-REV-031~037 / RM-REV-031~035 / CV-REV-006/009 / CACHE-REV-002 / EVT-REV-002。
 */
@Service
public class AdminQuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ReviewCatalogSnapshotPort catalogPort;
    private final ReviewCacheService cache;
    private final ReviewAuditRecorder audit;
    private final ReviewAfterCommitRunner afterCommit;
    private final ReviewEventPublisher events;
    private final ReviewTxRunner tx;
    private final ObjectMapper objectMapper;

    public AdminQuestionService(ProductQuestionRepository questionRepository, ReviewCatalogSnapshotPort catalogPort,
                                ReviewCacheService cache, ReviewAuditRecorder audit,
                                ReviewAfterCommitRunner afterCommit, ReviewEventPublisher events,
                                ReviewTxRunner tx, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.catalogPort = catalogPort;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.events = events;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    // ==================== E-REV-13 listAdminQuestions ====================

    public Paginated<AdminQuestionDto> listAdminQuestions(Integer page, Integer pageSize, Long productId,
                                                          String answered) {
        // V-REV-031~033
        ReviewFieldErrors errors = new ReviewFieldErrors();
        int parsedPage = ReviewParams.parsePage(page, errors);
        int parsedSize = ReviewParams.parsePageSize(pageSize, errors);
        Long pid = ReviewParams.parsePositiveId(productId, "product_id", errors);
        Boolean answeredFilter = null;
        if (answered != null && !answered.isBlank() && !"all".equals(answered)) {
            if ("answered".equals(answered)) {
                answeredFilter = true;
            } else if ("unanswered".equals(answered)) {
                answeredFilter = false;
            } else {
                errors.reject("answered", "invalid_enum");
            }
        }
        errors.throwIfAny();

        // STEP-REV-01 条件分页（含未回答与 hidden——后台全量视角）
        Page<ProductQuestion> questionPage = questionRepository.pageByAdminFilter(pid, answeredFilter,
                parsedPage, parsedSize);
        // STEP-REV-02 product_name 批量派生（NP-REV-001）
        Set<Long> productIds = new LinkedHashSet<>();
        questionPage.getRecords().forEach(q -> productIds.add(q.getProductId()));
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs = catalogPort.getProductBriefs(productIds);
        // STEP-REV-03 标准 Paginated
        List<AdminQuestionDto> items = questionPage.getRecords().stream()
                .map(q -> toAdminDto(q, briefs))
                .toList();
        return PaginatedFactory.of(items, questionPage.getTotal(), parsedPage, parsedSize);
    }

    // ==================== E-REV-14 putAdminQuestionAnswer（question_answer_flow, TX-REV-008） ====================

    public AdminQuestionDto putAnswer(Long id, String answer) {
        ProductQuestion question = requireQuestion(id);
        // V-REV-035 trim 后 1..2000（js_guard answerDraft.trim() 后端兜底——save/edit guard）
        String trimmed = answer == null ? "" : answer.trim();
        if (trimmed.isEmpty()) {
            throw ReviewException.fieldValidation("answer", "blank");
        }
        if (trimmed.length() > 2000) {
            throw ReviewException.fieldValidation("answer", "too_long");
        }
        // STEP-REV-02 状态机分支：首次回答 save_answer 自动置 visible；edit_answer 保持现值（手动隐藏不被覆盖）
        boolean firstAnswer = question.getAnswer() == null;
        tx.inTx(() -> {
            questionRepository.saveAnswer(id, trimmed, LocalDateTime.now(), firstAnswer);
            // STEP-REV-03 审计 action=回答提问
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("first_answer", firstAnswer);
            changes.put("answer_before", question.getAnswer());
            changes.put("answer_after", trimmed);
            audit.record(ReviewAuditRecorder.ACTION_ANSWER, "question#" + id, toJson(changes));
            // STEP-REV-04 失效 questions + content.invalidated(question_changed)
            Long productId = question.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.QUESTIONS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_QUESTION_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return readAdminDto(id);
    }

    // ==================== E-REV-15 patchAdminQuestionVisibility（TX-REV-009） ====================

    public AdminQuestionDto patchVisibility(Long id, Integer visible) {
        ProductQuestion question = requireQuestion(id);
        // V-REV-037（bs-511）
        QuestionVisibility target = QuestionVisibility.of(visible);
        if (visible == null || target == null) {
            throw ReviewException.fieldValidation("visible", "invalid_enum");
        }
        // STEP-REV-02 幂等：目标值=当前值 → 直接返回当前行（不写审计不发事件，不开事务）
        if (target == question.getVisible()) {
            return readAdminDto(id);
        }
        tx.inTx(() -> {
            // STEP-REV-03 切换（未回答提问允许置 visible——前台双条件过滤兜底，CV-REV-009）
            questionRepository.updateVisible(id, target);
            // STEP-REV-04 审计归入「回答提问」（§0 归入规则：可见性切换 changes 记录 visible from/to）
            audit.record(ReviewAuditRecorder.ACTION_ANSWER, "question#" + id, toJson(Map.of(
                    "visible", Map.of(
                            "from", question.getVisible() == null ? "" : question.getVisible().getKey(),
                            "to", target.getKey()))));
            // STEP-REV-05 失效 + content.invalidated
            Long productId = question.getProductId();
            afterCommit.run(() -> {
                cache.invalidateProduct(Family.QUESTIONS, productId);
                events.publishContentInvalidated(ReviewEventPublisher.TYPE_QUESTION_CHANGED,
                        slugOf(productId), productId);
            });
        });
        return readAdminDto(id);
    }

    // ==================== 装配/工具 ====================

    /** V-REV-034/036 口径：不存在（含非法 id）→ 404802 */
    private ProductQuestion requireQuestion(Long id) {
        ProductQuestion question = id == null || id <= 0 ? null : questionRepository.findById(id);
        if (question == null) {
            throw new ReviewException(ReviewErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    private AdminQuestionDto readAdminDto(Long id) {
        ProductQuestion question = requireQuestion(id);
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs =
                catalogPort.getProductBriefs(List.of(question.getProductId()));
        return toAdminDto(question, briefs);
    }

    /** MAP-REV-005（asker 不脱敏 + visible + product_name 派生，商品已删除容忍 null） */
    private AdminQuestionDto toAdminDto(ProductQuestion q, Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs) {
        ReviewCatalogSnapshotPort.ProductBrief brief = briefs.get(q.getProductId());
        return new AdminQuestionDto(q.getId(), q.getProductId(), brief == null ? null : brief.name(),
                q.getAsker(), q.getQuestion(), q.getAskedAt(), q.getAnswer(), q.getAnswerTime(),
                q.getVisible() == null ? null : q.getVisible().getKey());
    }

    private String slugOf(Long productId) {
        ReviewCatalogSnapshotPort.ProductBrief brief = catalogPort.getProductBrief(productId);
        return brief == null ? null : brief.slug();
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            return null;
        }
    }
}
