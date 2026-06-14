package com.dreamy.domain.product.service;

import com.dreamy.domain.product.entity.CareInstructionDef;
import com.dreamy.domain.product.entity.ProductCareInstruction;
import com.dreamy.domain.product.entity.ProductFabricComposition;
import com.dreamy.domain.product.repository.CareInstructionDefRepository;
import com.dreamy.domain.product.repository.ProductCareInstructionRepository;
import com.dreamy.domain.product.repository.ProductFabricCompositionRepository;
import com.dreamy.dto.FabricCareDtos.*;
import com.dreamy.enums.CareCategory;
import com.dreamy.enums.CareStatus;
import com.dreamy.enums.FabricLayer;
import com.dreamy.enums.FabricMaterial;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.CatalogAuditRecorder;
import com.dreamy.infra.CatalogCacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面料护理服务（E-FC-05~09 护理标签字典 CRUD；E-FC-01~04 扩展商品 CRUD 的面料逻辑）。
 * L2 TRACE: catalog-fabric-care-api-detail §2~3 / TX-FC-001~006 / CV-FC-001~011。
 */
@Service
public class FabricCareService {

    private final CareInstructionDefRepository careDefRepo;
    private final ProductFabricCompositionRepository fabricRepo;
    private final ProductCareInstructionRepository careInstrRepo;
    private final CatalogAuditRecorder audit;
    private final CatalogCacheService cache;

    public FabricCareService(CareInstructionDefRepository careDefRepo,
                             ProductFabricCompositionRepository fabricRepo,
                             ProductCareInstructionRepository careInstrRepo,
                             CatalogAuditRecorder audit,
                             CatalogCacheService cache) {
        this.careDefRepo = careDefRepo;
        this.fabricRepo = fabricRepo;
        this.careInstrRepo = careInstrRepo;
        this.audit = audit;
        this.cache = cache;
    }

    // ==================== E-FC-05 listAdminCareInstructions ====================

    /** E-FC-05 列出护理标签（STEP-FC-01：category 给定→按类别筛，否则全量） */
    public List<CareInstructionDefDto> listCareInstructions(Integer categoryVal) {
        // V-FC-010 category 可选 ∈ {1..5}
        if (categoryVal != null) {
            CareCategory category = CareCategory.of(categoryVal);
            if (category == null) {
                throw CatalogException.fieldValidation("category", "invalid_enum");
            }
            return careDefRepo.listByCategory(category).stream()
                    .map(this::toCareDefDto).toList();
        }
        return careDefRepo.listAll().stream().map(this::toCareDefDto).toList();
    }

    // ==================== E-FC-06 createAdminCareInstruction ====================

    /** E-FC-06 创建护理标签（TX-FC-003） */
    @Transactional
    public CareInstructionDefDto createCareInstruction(CareInstructionUpsert req) {
        validateCareUpsert(req, null);
        // STEP-FC-01 code 唯一性检测
        if (careDefRepo.existsByCodeExcept(req.code().trim(), null)) {
            throw new CatalogException(CatalogErrorCode.CARE_CODE_EXISTS);
        }
        CareInstructionDef def = new CareInstructionDef();
        def.setCode(req.code().trim().toUpperCase());
        def.setSymbolUnicode(req.symbolUnicode());
        def.setLabelEn(req.labelEn().trim());
        def.setLabelZh(req.labelZh().trim());
        def.setCategory(CareCategory.of(req.category()));
        def.setSortOrder(req.sortOrder());
        def.setStatus(CareStatus.of(req.status()));
        // STEP-FC-02 INSERT
        careDefRepo.insert(def);
        audit.record("创建护理标签", "care_instruction_def:" + def.getId(), null);
        // STEP-FC-03 失效缓存
        afterCommitInvalidateCareDefs();
        return toCareDefDto(def);
    }

    // ==================== E-FC-07 updateAdminCareInstruction ====================

    /** E-FC-07 编辑护理标签（TX-FC-004） */
    @Transactional
    public CareInstructionDefDto updateCareInstruction(Long id, CareInstructionUpsert req) {
        // V-FC-017 护理标签存在
        CareInstructionDef def = requireCare(id);
        validateCareUpsert(req, id);
        // STEP-FC-01 code 变更时查重（排除自身）
        if (!def.getCode().equalsIgnoreCase(req.code().trim()) &&
                careDefRepo.existsByCodeExcept(req.code().trim(), id)) {
            throw new CatalogException(CatalogErrorCode.CARE_CODE_EXISTS);
        }
        def.setCode(req.code().trim().toUpperCase());
        def.setSymbolUnicode(req.symbolUnicode());
        def.setLabelEn(req.labelEn().trim());
        def.setLabelZh(req.labelZh().trim());
        def.setCategory(CareCategory.of(req.category()));
        def.setSortOrder(req.sortOrder());
        def.setStatus(CareStatus.of(req.status()));
        // STEP-FC-02 UPDATE
        careDefRepo.update(def);
        audit.record("编辑护理标签", "care_instruction_def:" + id, null);
        // STEP-FC-03 失效 care-defs + product（已挂载商品 PDP）
        afterCommitInvalidateCareDefs();
        afterCommitInvalidateProducts();
        return toCareDefDto(def);
    }

    // ==================== E-FC-08 deleteAdminCareInstruction ====================

    /** E-FC-08 删除护理标签（TX-FC-005；级联摘除 product_care_instruction，无 guard） */
    @Transactional
    public void deleteCareInstruction(Long id) {
        // STEP-FC-01 不存在 → 422512
        requireCare(id);
        // STEP-FC-02 级联摘除 + 物理删除
        careInstrRepo.deleteByCareId(id);  // RM-FC-024
        careDefRepo.deleteById(id);         // RM-FC-018
        // STEP-FC-03 审计
        audit.record("删除护理标签", "care_instruction_def:" + id, null);
        // STEP-FC-04 失效
        afterCommitInvalidateCareDefs();
        afterCommitInvalidateProducts();
    }

    // ==================== E-FC-09 toggleAdminCareInstructionStatus ====================

    /** E-FC-09 切换护理标签状态（TX-FC-006；幂等短路） */
    @Transactional
    public CareInstructionDefDto toggleStatus(Long id, Integer statusVal) {
        // V-FC-020 status ∈ {1,2}
        if (statusVal == null || CareStatus.of(statusVal) == null) {
            throw CatalogException.fieldValidation("status", "invalid_enum");
        }
        // STEP-FC-01 不存在 → 422512
        CareInstructionDef def = requireCare(id);
        CareStatus targetStatus = CareStatus.of(statusVal);
        // STEP-FC-02 幂等：目标态=当前态 → 直接返回
        if (def.getStatus() == targetStatus) {
            return toCareDefDto(def);
        }
        // STEP-FC-03 UPDATE status + 审计
        careDefRepo.updateStatus(id, targetStatus);  // RM-FC-019
        def.setStatus(targetStatus);
        audit.record("切换护理标签状态", "care_instruction_def:" + id, null);
        // STEP-FC-04 失效
        afterCommitInvalidateCareDefs();
        afterCommitInvalidateProducts();
        return toCareDefDto(def);
    }

    // ==================== 商品 CRUD 扩展方法（供 AdminProductService 调用）====================

    /**
     * 校验并保存面料成分（TX-FC-001 内；E-FC-03/04 STEP-FC-01~02）。
     * CV-FC-003 每层 percentage 总和必须=100（js_guard）。
     */
    public void replaceFabricCompositions(Long productId, List<FabricCompositionInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            fabricRepo.deleteByProductId(productId);
            return;
        }
        // V-FC-001 枚举校验
        Map<String, String> fieldErrors = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            FabricCompositionInput row = inputs.get(i);
            if (row.layer() == null || FabricLayer.of(row.layer()) == null) {
                fieldErrors.put("fabric_compositions[" + i + "].layer", "invalid_enum");
            }
            if (row.material() == null || FabricMaterial.of(row.material()) == null) {
                fieldErrors.put("fabric_compositions[" + i + "].material", "invalid_enum");
            }
            if (row.percentage() == null || row.percentage().compareTo(BigDecimal.ZERO) < 0
                    || row.percentage().compareTo(BigDecimal.valueOf(100)) > 0) {
                fieldErrors.put("fabric_compositions[" + i + "].percentage", "out_of_range");
            }
        }
        if (!fieldErrors.isEmpty()) {
            throw CatalogException.fieldValidation(fieldErrors);
        }
        // V-FC-002 (layer, sort_order) 唯一
        validateLayerSortOrderUnique(inputs);
        // STEP-FC-01 percentage 总和校验（js_guard）—— CV-FC-003
        validatePercentageSum(inputs);
        // STEP-FC-02 整单覆盖（RM-FC-003）
        List<ProductFabricComposition> entities = toFabricEntities(inputs);
        fabricRepo.replaceAll(productId, entities);
    }

    /**
     * 校验并保存护理标签关联（TX-FC-001 内；E-FC-03/04 STEP-FC-03）。
     * CV-FC-006 care_id 全部存在且 status=active。
     */
    public void replaceCareInstructions(Long productId, List<Long> careIds) {
        if (careIds == null || careIds.isEmpty()) {
            careInstrRepo.deleteByProductId(productId);
            return;
        }
        // V-FC-003 care_ids 存在性检测（status=active）
        for (Long careId : careIds) {
            CareInstructionDef def = careDefRepo.findById(careId);
            if (def == null || def.getStatus() != CareStatus.ACTIVE) {
                throw CatalogException.fieldValidation("care_instruction_ids", "not_exists");
            }
        }
        careInstrRepo.replaceAll(productId, careIds);  // RM-FC-022
    }

    /** 装配消费端 PDP 面料成分（MAP-FC-005；STEP-FC-01） */
    public List<FabricCompositionDto> loadForStore(Long productId) {
        return fabricRepo.listByProductId(productId).stream()
                .map(this::toFabricDto).toList();
    }

    /** 装配消费端 PDP 护理标签（MAP-FC-003；STEP-FC-02~03） */
    public List<StoreCareInstructionDto> loadCareForStore(Long productId, String locale) {
        List<ProductCareInstruction> pcis = careInstrRepo.listByProductId(productId);
        List<StoreCareInstructionDto> result = new ArrayList<>();
        for (ProductCareInstruction pci : pcis) {
            CareInstructionDef def = careDefRepo.findById(pci.getCareId());
            if (def == null || def.getStatus() != CareStatus.ACTIVE) continue;
            // STEP-FC-03 locale 选 label
            String label = "zh".equalsIgnoreCase(locale) ? def.getLabelZh() : def.getLabelEn();
            result.add(new StoreCareInstructionDto(def.getId(), def.getSymbolUnicode(), label, def.getCategory().getKey()));
        }
        return result;
    }

    /** 装配后台编辑详情面料成分（MAP-FC-001；STEP-FC-01） */
    public List<FabricCompositionDto> loadForAdmin(Long productId) {
        return fabricRepo.listByProductId(productId).stream()
                .map(this::toFabricDto).toList();
    }

    /** 装配后台编辑详情护理标签 ID 列表（MAP-FC-006；STEP-FC-02） */
    public List<Long> loadCareIdsForAdmin(Long productId) {
        return careInstrRepo.listByProductId(productId).stream()
                .map(ProductCareInstruction::getCareId).toList();
    }

    // ==================== 内部方法 ====================

    private CareInstructionDef requireCare(Long id) {
        CareInstructionDef def = careDefRepo.findById(id);
        if (def == null) throw new CatalogException(CatalogErrorCode.CARE_NOT_FOUND);
        return def;
    }

    private void validateCareUpsert(CareInstructionUpsert req, Long exceptId) {
        Map<String, String> errors = new HashMap<>();
        if (req.code() == null || req.code().trim().isEmpty()) errors.put("code", "required");
        else if (req.code().trim().length() > 64) errors.put("code", "too_long");
        else if (!req.code().trim().matches("^[A-Z0-9_]+$")) errors.put("code", "invalid_format");
        if (req.symbolUnicode() != null && req.symbolUnicode().length() > 16) errors.put("symbol_unicode", "too_long");
        if (req.labelEn() == null || req.labelEn().trim().isEmpty()) errors.put("label_en", "required");
        else if (req.labelEn().trim().length() > 128) errors.put("label_en", "too_long");
        if (req.labelZh() == null || req.labelZh().trim().isEmpty()) errors.put("label_zh", "required");
        else if (req.labelZh().trim().length() > 128) errors.put("label_zh", "too_long");
        if (req.category() == null || CareCategory.of(req.category()) == null) errors.put("category", "invalid_enum");
        if (req.status() == null || CareStatus.of(req.status()) == null) errors.put("status", "invalid_enum");
        if (!errors.isEmpty()) throw CatalogException.fieldValidation(errors);
    }

    /** V-FC-002 (layer, sort_order) 组合不重复 */
    private void validateLayerSortOrderUnique(List<FabricCompositionInput> inputs) {
        long distinctCount = inputs.stream()
                .filter(r -> r.layer() != null && r.sortOrder() != null)
                .map(r -> r.layer() + "_" + r.sortOrder())
                .distinct().count();
        long withSortOrder = inputs.stream()
                .filter(r -> r.layer() != null && r.sortOrder() != null).count();
        if (distinctCount < withSortOrder) {
            throw CatalogException.fieldValidation("fabric_compositions", "duplicate_layer_sort_order");
        }
    }

    /** STEP-FC-01 CV-FC-003 每层 percentage 总和 = 100 */
    private void validatePercentageSum(List<FabricCompositionInput> inputs) {
        Map<Integer, BigDecimal> sumByLayer = new HashMap<>();
        for (int i = 0; i < inputs.size(); i++) {
            FabricCompositionInput row = inputs.get(i);
            if (row.layer() == null || row.percentage() == null) continue;
            sumByLayer.merge(row.layer(), row.percentage(), BigDecimal::add);
        }
        for (Map.Entry<Integer, BigDecimal> entry : sumByLayer.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new CatalogException(CatalogErrorCode.FABRIC_PERCENTAGE_INVALID,
                        Map.of("layer", entry.getKey(), "actual_sum", entry.getValue()));
            }
        }
    }

    private List<ProductFabricComposition> toFabricEntities(List<FabricCompositionInput> inputs) {
        List<ProductFabricComposition> result = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            FabricCompositionInput row = inputs.get(i);
            ProductFabricComposition e = new ProductFabricComposition();
            e.setLayer(FabricLayer.of(row.layer()));
            e.setMaterial(FabricMaterial.of(row.material()));
            e.setPercentage(row.percentage());
            e.setSortOrder(row.sortOrder() != null ? row.sortOrder() : i);
            result.add(e);
        }
        return result;
    }

    // DTO 映射 MAP-FC-001
    private FabricCompositionDto toFabricDto(ProductFabricComposition e) {
        return new FabricCompositionDto(e.getId(), e.getProductId(),
                e.getLayer().getKey(), e.getMaterial().getKey(),
                e.getPercentage(), e.getSortOrder(), e.getCreatedAt(), e.getUpdatedAt());
    }

    // DTO 映射 MAP-FC-002
    private CareInstructionDefDto toCareDefDto(CareInstructionDef e) {
        return new CareInstructionDefDto(e.getId(), e.getCode(), e.getSymbolUnicode(),
                e.getLabelEn(), e.getLabelZh(),
                e.getCategory().getKey(), e.getSortOrder(), e.getStatus().getKey(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private void afterCommitInvalidateCareDefs() {
        try {
            cache.invalidateFamily(CatalogCacheService.Family.PRODUCT);
        } catch (Exception ignored) {
            // EC-CAT-002：缓存失效失败不影响主流程
        }
    }

    private void afterCommitInvalidateProducts() {
        try {
            cache.invalidateFamily(CatalogCacheService.Family.PRODUCT);
        } catch (Exception ignored) {
            // EC-CAT-002
        }
    }
}
