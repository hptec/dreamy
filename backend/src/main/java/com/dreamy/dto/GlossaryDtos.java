package com.dreamy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 翻译术语表域 DTO 集（glossary-api.openapi.yml）。
 * L2 TRACE: i18n-backend-api-detail.md §3 / FUNC-022 / EDGE-022。
 */
public final class GlossaryDtos {

    private GlossaryDtos() {
    }

    /**
     * 术语响应（GlossaryTerm）。含 id/term_en/term_es/term_fr/category/enabled/created_at/updated_at（§3.3）。
     */
    public record GlossaryTermDto(
            Long id,
            String termEn,
            String termEs,
            String termFr,
            String category,
            Boolean enabled,
            String createdAt,
            String updatedAt) {
    }

    /**
     * 新增/更新术语请求（GlossaryTermUpsert）。term_en 唯一（409401），term_en ≤ 128。
     */
    public record GlossaryTermUpsert(
            @NotBlank @Size(max = 128) String termEn,
            @Size(max = 128) String termEs,
            @Size(max = 128) String termFr,
            @Size(max = 32) String category,
            @NotNull Boolean enabled,
            /** 乐观锁：更新时回传 DB updated_at，不一致 → 409 冲突。创建时忽略。 */
            String updatedAt) {
    }
}
