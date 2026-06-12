package com.dreamy.domain.product.service;

import com.dreamy.dto.AdminProductBatchDtos.BatchFailure;
import com.dreamy.dto.AdminProductBatchDtos.BatchResult;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.i18n.CatalogMessageResolver;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.support.CatalogFieldErrors;
import com.dreamy.i18n.RequestLocaleContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 后台商品批量操作服务（API-CAT-01 batchAdminProducts；admin-prototype-alignment ALIGN-007）。
 * 逐条容错整体不回滚（BE-DIM-4）：行级操作复用既有单品 service（STEP-01/RM-CAT-02a），
 * 每行独立事务提交（TX-CAT-01——本方法无事务，单品 service 方法各自开启行级事务，禁止把循环包进单事务）。
 * L2 TRACE: V-001/V-002 / STEP-01~04 / RM-CAT-02a~d / TX-CAT-01 / EC-CAT-01 / CV-CAT-01。
 */
@Service
public class AdminProductBatchService {

    private static final Logger log = LoggerFactory.getLogger(AdminProductBatchService.class);

    /** V-002 单次上限 200 件 */
    public static final int MAX_IDS = 200;

    /** 行级未知异常包内码（catalog-api-detail 错误码映射：500500，message 脱敏） */
    static final int ROW_INTERNAL_ERROR_CODE = 500500;

    private final AdminProductService adminProductService;
    private final CatalogAuditRecorder audit;
    private final CatalogMessageResolver messageResolver;
    private final ObjectMapper objectMapper;

    public AdminProductBatchService(AdminProductService adminProductService, CatalogAuditRecorder audit,
                                    CatalogMessageResolver messageResolver, ObjectMapper objectMapper) {
        this.adminProductService = adminProductService;
        this.audit = audit;
        this.messageResolver = messageResolver;
        this.objectMapper = objectMapper;
    }

    /** V-001 action 枚举（publish/unpublish/recommend/unrecommend/delete） */
    enum BatchAction {
        PUBLISH("publish", "上架"),
        UNPUBLISH("unpublish", "下架"),
        RECOMMEND("recommend", "推荐"),
        UNRECOMMEND("unrecommend", "取消推荐"),
        DELETE("delete", "删除");

        private final String key;
        private final String label;

        BatchAction(String key, String label) {
            this.key = key;
            this.label = label;
        }

        static BatchAction of(String raw) {
            for (BatchAction action : values()) {
                if (action.key.equals(raw)) {
                    return action;
                }
            }
            return null;
        }
    }

    /**
     * API-CAT-01 主流程。参数非法 → 422501（既有 4xx 参数错误口径，沿用 FIELD_VALIDATION_FAILED，
     * 不新增 400 码——catalog-api-detail「不新增错误码」约束）；部分/全部失败仍 200。
     */
    public BatchResult execute(String actionParam, List<Long> idsParam) {
        CatalogFieldErrors errors = new CatalogFieldErrors();
        // V-001 action ∈ {publish, unpublish, recommend, unrecommend, delete}，必填
        BatchAction action = BatchAction.of(actionParam);
        if (actionParam == null || actionParam.isBlank()) {
            errors.reject("action", "required");
        } else if (action == null) {
            errors.reject("action", "invalid_enum");
        }
        // V-002 ids 必填，1 <= size <= 200（超限文案「单次最多 200 件」，admin 端直出中文），元素去重
        if (idsParam == null || idsParam.isEmpty()) {
            errors.reject("ids", "required");
        } else if (idsParam.size() > MAX_IDS) {
            errors.reject("ids", "单次最多 200 件");
        } else if (idsParam.stream().anyMatch(Objects::isNull)) {
            errors.reject("ids", "element_invalid");
        }
        errors.throwIfAny();
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(idsParam));
        // STEP-02 逐条容错：行级独立事务（单品 service 自带），整体不回滚
        List<Long> successIds = new ArrayList<>();
        List<BatchFailure> failures = new ArrayList<>();
        for (Long id : ids) {
            try {
                applyRow(action, id);
                successIds.add(id);
            } catch (CatalogException ex) {
                // EC-CAT-01 / CV-CAT-01：delete 目标不存在 → 幂等成功（容忍已删除，BE-DIM-4）
                if (action == BatchAction.DELETE && ex.getErrorCode() == CatalogErrorCode.PRODUCT_NOT_FOUND) {
                    successIds.add(id);
                    continue;
                }
                failures.add(new BatchFailure(id, ex.getErrorCode().getCode(),
                        messageResolver.resolve(ex.getErrorCode(), RequestLocaleContext.get())));
            } catch (RuntimeException ex) {
                // 行级未知异常 → 500500（包内，message 脱敏不透出异常详情）
                log.error("[CATALOG-BATCH] row failed action={} id={}", action.key, id, ex);
                failures.add(new BatchFailure(id, ROW_INTERNAL_ERROR_CODE,
                        messageResolver.resolve("error.500500", RequestLocaleContext.get())));
            }
        }
        // STEP-03 写 OperationLog 一条（action=批量{操作名}，detail 含 ids 总数/成功数/失败数）
        audit.record("批量" + action.label, "商品×" + ids.size(),
                summaryJson(ids.size(), successIds.size(), failures.size()));
        // STEP-04 返回 200（控制器包 R）
        return new BatchResult(successIds, failures);
    }

    /** STEP-01 解析 action → 单品操作函数（复用既有单品 service：上架/下架/推荐置位/删除——RM-CAT-02a） */
    private void applyRow(BatchAction action, Long id) {
        switch (action) {
            // publish：draft→published（published 幂等成功——toggleStatus 同态短路，EC-CAT-01/RM-CAT-02d）
            case PUBLISH -> adminProductService.toggleStatus(id, "published");
            // unpublish：published→draft（draft 幂等成功）
            case UNPUBLISH -> adminProductService.toggleStatus(id, "draft");
            // recommend/unrecommend：recommend 置位（UPDATE 置位天然幂等）
            case RECOMMEND -> adminProductService.patchFlags(id, null, null, true, null);
            case UNRECOMMEND -> adminProductService.patchFlags(id, null, null, false, null);
            // delete：仅 draft 可删；published → 409509（单品 service 前置校验——RM-CAT-02c，行级捕获进 failures）
            case DELETE -> adminProductService.delete(id);
        }
    }

    private String summaryJson(int total, int success, int failed) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ids_total", total);
            detail.put("success_count", success);
            detail.put("failure_count", failed);
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            return null;
        }
    }
}
