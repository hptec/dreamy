package com.dreamy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面料护理相关 DTO。
 * L2 TRACE: MAP-FC-001~003 / catalog-fabric-care-api-detail §1~3。
 */
public class FabricCareDtos {

    /**
     * MAP-FC-001 面料成分行（后台编辑/回显）。
     * L2 TRACE: E-FC-02/03/04 出参 fabric_compositions[]。
     */
    public record FabricCompositionDto(
            Long id,
            Long productId,
            Integer layer,
            Integer material,
            BigDecimal percentage,
            Integer sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * MAP-FC-002 护理标签字典条目（后台管理）。
     * L2 TRACE: E-FC-05/06/07/08/09 出参。
     */
    public record CareInstructionDefDto(
            Long id,
            String code,
            String symbolUnicode,
            String labelEn,
            String labelZh,
            Integer category,
            Integer sortOrder,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    /**
     * MAP-FC-003 护理标签消费端展示（PDP locale 已解析）。
     * L2 TRACE: E-FC-01 STEP-FC-02~03 出参 care_instructions[]。
     */
    public record StoreCareInstructionDto(
            Long id,
            String symbolUnicode,
            String label,
            Integer category
    ) {}

    /**
     * MAP-FC-008 面料成分提交行（来自 AdminProductUpsert.fabricCompositions）。
     * L2 TRACE: V-FC-001~002 校验输入。
     */
    public record FabricCompositionInput(
            Integer layer,
            Integer material,
            BigDecimal percentage,
            Integer sortOrder
    ) {}

    /**
     * 护理标签创建/编辑请求体（E-FC-06/07）。
     * L2 TRACE: V-FC-011~016。
     */
    public record CareInstructionUpsert(
            String code,
            String symbolUnicode,
            String labelEn,
            String labelZh,
            Integer category,
            Integer sortOrder,
            Integer status
    ) {}

    /**
     * 护理标签状态切换请求体（E-FC-09）。
     * L2 TRACE: V-FC-020。
     */
    public record CareStatusToggle(Integer status) {}
}
