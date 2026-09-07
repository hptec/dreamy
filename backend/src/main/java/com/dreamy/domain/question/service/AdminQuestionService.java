package com.dreamy.domain.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.QuestionBatchAction;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.entity.ProductQuestion;
import com.dreamy.domain.question.repository.ProductQuestionRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.AdminQuestionListDTO;
import com.dreamy.dto.ReviewDtos.AdminQuestionDto;
import com.dreamy.dto.ReviewDtos.BatchResult;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewAuditRecorder;
import com.dreamy.infra.ReviewTxRunner;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.support.ReviewFieldErrors;
import com.dreamy.support.ReviewParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 Q&A 服务（E-REV-13~15；TX-REV-008/009；TASK-049 question_answer_flow guard 内嵌）。
 * 前台可见写会创建持久化缓存任务，PDP Q&A 缓存由任务工作器清理。
 * L2 TRACE: V-REV-031~037 / RM-REV-031~035 / CV-REV-006/009 / CACHE-REV-002。
 */
@Service
public class AdminQuestionService {

    private final ProductQuestionRepository questionRepository;
    private final ReviewCatalogSnapshotPort catalogPort;
    private final ReviewAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;
    private final ReviewTxRunner tx;
    private final ObjectMapper objectMapper;

    public AdminQuestionService(ProductQuestionRepository questionRepository, ReviewCatalogSnapshotPort catalogPort,
                                ReviewAuditRecorder audit, CacheInvalidationTaskService cacheTasks,
                                ReviewTxRunner tx, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.catalogPort = catalogPort;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    // ==================== E-REV-13 listAdminQuestions ====================

    public AdminQuestionListDTO listAdminQuestions(Integer page, Integer pageSize, Long productId,
                                                   String answered, String search) {
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
        String parsedSearch = ReviewParams.parseSearch(search, errors);
        errors.throwIfAny();

        // STEP-REV-01 条件分页（含未回答与 hidden——后台全量视角；search 命中商品名时按 product_id IN 并入
        // ——与评价搜索同端口同语义）
        Set<Long> searchProductIds = parsedSearch == null ? null
                : catalogPort.searchProductIdsByKeyword(parsedSearch);
        Page<ProductQuestion> questionPage = questionRepository.pageByAdminFilter(pid, answeredFilter,
                parsedSearch, searchProductIds, parsedPage, parsedSize);
        // STEP-REV-02 product_name 批量派生（NP-REV-001）
        Set<Long> productIds = new LinkedHashSet<>();
        questionPage.getRecords().forEach(q -> productIds.add(q.getProductId()));
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs = catalogPort.getProductBriefs(productIds);
        // STEP-REV-03 标准 Paginated + unanswered_count 平铺（对齐评价 pending_count 模式）
        List<AdminQuestionDto> items = questionPage.getRecords().stream()
                .map(q -> toAdminDto(q, briefs))
                .toList();
        AdminQuestionListDTO dto = new AdminQuestionListDTO();
        dto.setData(items);
        dto.setTotalElements(questionPage.getTotal());
        dto.setPageNumber(parsedPage);
        dto.setPageSize(parsedSize);
        dto.setNumberOfElements(questionPage.getRecords().size());
        dto.setTotalPages(parsedSize > 0 ? (int) Math.ceil((double) questionPage.getTotal() / parsedSize) : 0);
        dto.setUnansweredCount(questionRepository.countUnanswered());
        return dto;
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
        // STEP-REV-02 状态机分支：首答（answer=NULL）CAS 原子置 visible；编辑保持 visible 现值（手动隐藏不被覆盖）。
        // expectAnswer 快照参与 CAS——他人先写 → affected=0 → 409805（对齐评价 bs-591 并发防护）
        String expectAnswer = question.getAnswer();
        tx.inTx(() -> {
            int affected = questionRepository.saveAnswer(id, trimmed, LocalDateTime.now(), expectAnswer);
            if (affected == 0) {
                throw new ReviewException(ReviewErrorCode.ANSWER_CONFLICT);
            }
            // STEP-REV-03 审计 action=回答提问
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("first_answer", expectAnswer == null);
            changes.put("answer_before", question.getAnswer());
            changes.put("answer_after", trimmed);
            audit.record(ReviewAuditRecorder.ACTION_ANSWER, "question#" + id, toJson(changes));
            // STEP-REV-04 创建 questions 缓存任务
            Long productId = question.getProductId();
            enqueueQuestion("question.answer", id, productId);
        });
        return readAdminDto(id);
    }

    /** 撤回回答（对齐评价 deleteReply：清空 answer/answer_time；幂等 204；visible 保持现值——前台双条件过滤兜底） */
    public void deleteAnswer(Long id) {
        ProductQuestion question = requireQuestion(id);
        // 幂等：未回答 → 直接返回（不写审计不发任务，不开事务）
        if (question.getAnswer() == null) {
            return;
        }
        tx.inTx(() -> {
            questionRepository.clearAnswer(id);
            audit.record(ReviewAuditRecorder.ACTION_ANSWER, "question#" + id,
                    toJson(Map.of("answer_cleared", true)));
            enqueueQuestion("question.answer.clear", id, question.getProductId());
        });
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
            // STEP-REV-05 创建 questions 缓存任务
            Long productId = question.getProductId();
            enqueueQuestion("question.visibility", id, productId);
        });
        return readAdminDto(id);
    }

    // ==================== 批量可见性（对齐评价 batchSet：skipped 语义 + ≤200 上限） ====================

    public BatchResult batchVisibility(List<Long> ids, String action) {
        // ids 非空、元素正整数、去重、≤200（防滥用——对齐 V-REV-024）
        ReviewFieldErrors errors = new ReviewFieldErrors();
        Set<Long> deduped = new LinkedHashSet<>();
        if (ids == null || ids.isEmpty()) {
            errors.reject("ids", "required");
        } else {
            for (Long id : ids) {
                if (id == null || id <= 0) {
                    errors.reject("ids", "invalid");
                    break;
                }
                deduped.add(id);
            }
            if (deduped.size() > 200) {
                errors.reject("ids", "too_many");
            }
        }
        QuestionBatchAction batchAction = QuestionBatchAction.of(action);
        if (batchAction == null) {
            errors.reject("action", "invalid_enum");
        }
        errors.throwIfAny();

        QuestionVisibility target = batchAction == QuestionBatchAction.HIDE
                ? QuestionVisibility.HIDDEN : QuestionVisibility.VISIBLE;
        List<Long> updatedIds = new ArrayList<>();
        List<Long> skippedIds = new ArrayList<>();
        Set<Long> touchedProducts = new LinkedHashSet<>();
        Map<Long, Long> productQuestionSample = new LinkedHashMap<>();

        tx.inTx(() -> {
            // 批量读取分拣（不存在 id 归入 skipped，批量语义不 404）
            Map<Long, ProductQuestion> byId = new HashMap<>();
            for (ProductQuestion q : questionRepository.listByIds(deduped)) {
                byId.put(q.getId(), q);
            }
            for (Long id : deduped) {
                ProductQuestion q = byId.get(id);
                if (q == null) {
                    skippedIds.add(id);
                    continue;
                }
                // 已是目标态 → skipped；并发漂移 CAS affected=0 → skipped
                boolean updated = q.getVisible() == target
                        ? false
                        : questionRepository.casBatchVisible(id, q.getVisible(), target) > 0;
                if (updated) {
                    updatedIds.add(id);
                    if (touchedProducts.add(q.getProductId())) {
                        productQuestionSample.put(q.getProductId(), id);
                    }
                } else {
                    skippedIds.add(id);
                }
            }
            // 审计（action=批量）
            audit.record(ReviewAuditRecorder.ACTION_BATCH, "questions/batch", toJson(Map.of(
                    "action", batchAction.getKey(),
                    "updated_ids", updatedIds,
                    "skipped_ids", skippedIds)));
            // touched 非空时按 product_id 去重创建缓存任务
            for (Long pid : touchedProducts) {
                enqueueQuestion("question.batch." + batchAction.getKey(), productQuestionSample.get(pid), pid);
            }
        });
        return new BatchResult(updatedIds, skippedIds);
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

    private void enqueueQuestion(String triggerPoint, Long questionId, Long productId) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "question", questionId, "product:" + productId, CacheInvalidationPlans.QUESTION,
                null, Map.of("product_id", productId), null);
    }

    private String toJson(Map<String, Object> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            return null;
        }
    }
}
