package com.dreamy.catalog.dto;

import java.util.List;

/**
 * 后台商品批量操作载荷（API-CAT-01 batchAdminProducts；admin-prototype-alignment）。
 * 出参逐条容错语义：部分/全部失败仍 200，由调用方按 failures 展示。
 * L2 TRACE: catalog-api-detail API-CAT-01 入参/出参。
 */
public final class AdminProductBatchDtos {

    private AdminProductBatchDtos() {
    }

    /** 请求体 { action, ids }（V-001/V-002） */
    public record BatchRequest(String action, List<Long> ids) {
    }

    /** 行级失败明细 { id, error_code, message }（STEP-02 catch 包内） */
    public record BatchFailure(Long id, Integer errorCode, String message) {
    }

    /** 出参 { success_ids, failures }（STEP-04） */
    public record BatchResult(List<Long> successIds, List<BatchFailure> failures) {
    }
}
