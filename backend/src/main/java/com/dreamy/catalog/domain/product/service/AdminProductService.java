package com.dreamy.catalog.domain.product.service;

import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import com.dreamy.catalog.domain.category.service.CategoryTreeService;
import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.attribute.service.ProductAttributeConfigService;
import com.dreamy.catalog.domain.attribute.service.ProductAttributeConfigService.ResolvedAttr;
import com.dreamy.catalog.domain.enums.ImageKind;
import com.dreamy.catalog.domain.enums.ProductStatus;
import com.dreamy.catalog.domain.product.entity.Product;
import com.dreamy.catalog.domain.product.entity.ProductAttributeValue;
import com.dreamy.catalog.domain.product.entity.ProductImage;
import com.dreamy.catalog.domain.product.entity.ProductTranslation;
import com.dreamy.catalog.domain.product.entity.SizeChartRow;
import com.dreamy.catalog.domain.product.entity.Sku;
import com.dreamy.catalog.domain.product.repository.ProductAttributeValueRepository;
import com.dreamy.catalog.domain.product.repository.ProductImageRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository.AdminFilter;
import com.dreamy.catalog.domain.product.repository.ProductTagRepository;
import com.dreamy.catalog.domain.product.repository.ProductTranslationRepository;
import com.dreamy.catalog.domain.product.repository.SizeChartRowRepository;
import com.dreamy.catalog.domain.product.repository.SkuRepository;
import com.dreamy.catalog.domain.tag.entity.Tag;
import com.dreamy.catalog.domain.tag.repository.TagRepository;
import com.dreamy.catalog.dto.AdminProductDetail;
import com.dreamy.catalog.dto.AdminProductListItem;
import com.dreamy.catalog.dto.AdminProductUpsert;
import com.dreamy.catalog.dto.AttributeValueDto;
import com.dreamy.catalog.dto.ProductImageDto;
import com.dreamy.catalog.dto.SizeChartRowDto;
import com.dreamy.catalog.dto.SkuDto;
import com.dreamy.catalog.dto.TranslationDtos.ProductTranslationDto;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.event.ContentInvalidatedPublisher;
import com.dreamy.catalog.infra.AfterCommitRunner;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import com.dreamy.catalog.infra.CatalogCacheService;
import com.dreamy.catalog.infra.CatalogCacheService.Family;
import com.dreamy.catalog.port.TradingQueryPort;
import com.dreamy.catalog.support.FieldErrors;
import com.dreamy.catalog.support.PaginatedSupport;
import com.dreamy.catalog.support.StoreParams;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台商品服务（E-CAT-08~14；TX-CAT-001~005；TASK-040 product_lifecycle guard）。
 * L2 TRACE: V-CAT-020~042 / RM-CAT-080~090·120~125 / CACHE-CAT-001·002·004 失效链 / EC-CAT-001 编辑不重试。
 */
@Service
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductImageRepository imageRepository;
    private final SkuRepository skuRepository;
    private final SizeChartRowRepository sizeChartRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final AttributeDefRepository attributeDefRepository;
    private final ProductAttributeConfigService attributeConfigService;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final CatalogCacheService cache;
    private final CatalogAuditRecorder audit;
    private final AfterCommitRunner afterCommit;
    private final ContentInvalidatedPublisher invalidatedPublisher;
    private final TradingQueryPort tradingQueryPort;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public AdminProductService(ProductRepository productRepository,
                               ProductTranslationRepository translationRepository,
                               ProductImageRepository imageRepository, SkuRepository skuRepository,
                               SizeChartRowRepository sizeChartRepository,
                               ProductTagRepository productTagRepository,
                               ProductAttributeValueRepository attributeValueRepository,
                               AttributeDefRepository attributeDefRepository,
                               ProductAttributeConfigService attributeConfigService,
                               TagRepository tagRepository,
                               CategoryRepository categoryRepository, CategoryTreeService treeService,
                               CatalogCacheService cache, CatalogAuditRecorder audit,
                               AfterCommitRunner afterCommit, ContentInvalidatedPublisher invalidatedPublisher,
                               TradingQueryPort tradingQueryPort,
                               TransactionTemplate transactionTemplate, ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.translationRepository = translationRepository;
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.productTagRepository = productTagRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.attributeDefRepository = attributeDefRepository;
        this.attributeConfigService = attributeConfigService;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.invalidatedPublisher = invalidatedPublisher;
        this.tradingQueryPort = tradingQueryPort;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== E-CAT-08 listAdminProducts ====================

    /** E-CAT-08：后台列表（V-CAT-020~022；派生列批查防 N+1——STEP-CAT-03） */
    public Paginated<AdminProductListItem> pageList(Integer pageParam, Integer pageSizeParam, String statusParam,
                                                    Long categoryId, String search) {
        FieldErrors errors = new FieldErrors();
        int page = StoreParams.parsePage(pageParam, errors);
        int pageSize = StoreParams.parsePageSize(pageSizeParam, errors);
        // V-CAT-021 status ∈ {all, draft, published} 缺省 all
        ProductStatus status = null;
        if (statusParam != null && !statusParam.isBlank() && !"all".equals(statusParam)) {
            status = ProductStatus.of(statusParam);
            if (status == null) {
                errors.reject("status", "invalid_enum");
            }
        }
        // V-CAT-022 search maxLength 80（trim 后空视为未提供）
        String normalizedSearch = null;
        if (search != null && !search.trim().isEmpty()) {
            if (search.trim().length() > 80) {
                errors.reject("search", "too_long");
            } else {
                normalizedSearch = search.trim();
            }
        }
        errors.throwIfAny();
        // STEP-CAT-01 条件（category_id 含子树）+ STEP-CAT-02 分页（sort ASC, id DESC）
        List<Long> categoryIds = treeService.subtreeIds(categoryId);
        Page<Product> result = productRepository.pageAdminList(
                new AdminFilter(status, categoryIds, normalizedSearch), page, pageSize);
        // STEP-CAT-03 派生列批量装配
        List<AdminProductListItem> items = assembleListItems(result.getRecords());
        return PaginatedSupport.of(items, result.getTotal(), page, pageSize);
    }

    // ==================== E-CAT-09 createAdminProduct（TX-CAT-001） ====================

    @Transactional
    public AdminProductDetail create(AdminProductUpsert req) {
        validateUpsert(req, null, null);
        // STEP-CAT-01 slug 唯一性 → 409501
        if (productRepository.existsBySlugExcept(req.slug(), null)) {
            throw new CatalogException(CatalogErrorCode.SLUG_EXISTS);
        }
        // STEP-CAT-02 sku_code 全局唯一性 → 409504（details.sku_codes）
        checkSkuCodes(req.skus(), null);
        // STEP-CAT-03 INSERT product（缺省 false 标记；冗余列初始化 0）
        Product product = new Product();
        applyUpsert(product, req);
        product.setSales30d(0);
        product.setRatingAvg(BigDecimal.ZERO);
        product.setRatingCount(0);
        productRepository.insert(product);
        // STEP-CAT-04 批量 INSERT 子表（sku version=0）
        imageRepository.replaceAll(product.getId(), toImageRows(req.images()));
        skuRepository.insertBatch(toSkuRows(product.getId(), req.skus()));
        sizeChartRepository.replaceAll(product.getId(), toSizeChartRows(req.sizeChart()));
        productTagRepository.replaceAll(product.getId(), dedupe(req.tagIds()));
        translationRepository.replaceAll(product.getId(), toTranslationRows(req.translations()));
        attributeValueRepository.replaceAll(product.getId(), toAttributeRows(req.attributes()));
        // STEP-CAT-05 审计
        audit.record("创建商品", product.getName(), null);
        // STEP-CAT-06 提交后失效链 + MQ（status=published 才需 revalidate 路径，事件统一发布由消费者按 type 处理）
        String slug = product.getSlug();
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.PRODUCTS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.CATEGORIES);
            cache.invalidateFamily(Family.TAGS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_PRODUCT_CREATED, slug, null);
        });
        return loadDetail(product.getId());
    }

    // ==================== E-CAT-10 getAdminProduct ====================

    /** E-CAT-10：编辑详情（EN 主表字段原样，translations 三语 tab 全量——MAP-CAT-004） */
    public AdminProductDetail get(Long id) {
        AdminProductDetail detail = loadDetail(id);
        if (detail == null) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        return detail;
    }

    // ==================== E-CAT-11 updateAdminProduct（TX-CAT-002） ====================

    @Transactional
    public AdminProductDetail update(Long id, AdminProductUpsert req) {
        // STEP-CAT-01 商品存在
        Product existing = productRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        List<Sku> ownedSkus = skuRepository.listByProductId(id);
        Set<Long> ownedSkuIds = new HashSet<>();
        for (Sku sku : ownedSkus) {
            ownedSkuIds.add(sku.getId());
        }
        validateUpsert(req, ownedSkuIds, id);
        // STEP-CAT-02 slug 查重（排除自身）→ 409501；sku_code 查重（排除本商品行）→ 409504
        if (productRepository.existsBySlugExcept(req.slug(), id)) {
            throw new CatalogException(CatalogErrorCode.SLUG_EXISTS);
        }
        checkSkuCodes(req.skus(), id);
        // STEP-CAT-03 并发防丢失（409508）：无 SKU 携带 id 时以可选回传 updated_at 与 DB 比对
        boolean hasCarriedSkuIds = req.skus() != null && req.skus().stream().anyMatch(s -> s.id() != null);
        if (!hasCarriedSkuIds && req.updatedAt() != null && !req.updatedAt().isBlank()) {
            checkUpdatedAtConflict(existing, req.updatedAt());
        }
        // STEP-CAT-05（sku 差异化前置于主表更新无碍原子性——同一事务）：
        // 带 id 行 CAS UPDATE（affected=0 → 整体回滚 409508，EC-CAT-001 不重试）；新行 INSERT(version=0)；缺席行 DELETE
        applySkuDiff(id, ownedSkus, req.skus());
        // STEP-CAT-04 UPDATE 主表（冗余列不受整单覆盖影响——RM-CAT-087 强制剔除）
        String oldSlug = existing.getSlug();
        applyUpsert(existing, req);
        productRepository.update(existing);
        // STEP-CAT-05 其余子表整单覆盖（DELETE+批量 INSERT）
        imageRepository.replaceAll(id, toImageRows(req.images()));
        sizeChartRepository.replaceAll(id, toSizeChartRows(req.sizeChart()));
        productTagRepository.replaceAll(id, dedupe(req.tagIds()));
        translationRepository.replaceAll(id, toTranslationRows(req.translations()));
        attributeValueRepository.replaceAll(id, toAttributeRows(req.attributes()));
        // STEP-CAT-06 审计
        audit.record("编辑商品", existing.getName(), null);
        // STEP-CAT-07 提交后失效链（新旧 slug 都失效；search 不主动失效 60s TTL 兜底）+ MQ
        String newSlug = existing.getSlug();
        afterCommit.run(() -> {
            cache.invalidateProductSlug(oldSlug);
            cache.invalidateProductSlug(newSlug);
            cache.invalidateFamily(Family.PRODUCTS);
            cache.invalidateFamily(Family.RECO);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_PRODUCT_UPDATED, newSlug, oldSlug);
        });
        return loadDetail(id);
    }

    // ==================== E-CAT-12 deleteAdminProduct（TX-CAT-003） ====================

    @Transactional
    public void delete(Long id) {
        // STEP-CAT-01 存在性
        Product existing = productRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        // STEP-CAT-02 状态机 guard：published 禁止直删 → 409509（TASK-040 published→deleted guard）
        if (existing.getStatus() == ProductStatus.PUBLISHED) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_DELETABLE);
        }
        // STEP-CAT-03 物理删除七表（订单行为快照不受影响）
        productRepository.deleteById(id);
        imageRepository.deleteByProductId(id);
        skuRepository.deleteByProductId(id);
        sizeChartRepository.deleteByProductId(id);
        productTagRepository.deleteByProductId(id);
        translationRepository.deleteByProductId(id);
        attributeValueRepository.deleteByProductId(id);
        // STEP-CAT-04 审计
        audit.record("删除商品", existing.getName(), null);
        // STEP-CAT-05 提交后失效（draft 无消费端页面，不发 revalidate 事件）
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.PRODUCTS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.CATEGORIES);
            cache.invalidateFamily(Family.TAGS);
        });
    }

    // ==================== E-CAT-13 toggleAdminProductStatus（TX-CAT-004） ====================

    /** 幂等短路不开事务（STEP-CAT-02）；变更走 TransactionTemplate 单事务 */
    public AdminProductListItem toggleStatus(Long id, String statusParam) {
        // V-CAT-040 status 必填枚举
        ProductStatus target = ProductStatus.of(statusParam);
        if (statusParam == null || statusParam.isBlank()) {
            throw CatalogException.fieldValidation("status", "required");
        }
        if (target == null) {
            throw CatalogException.fieldValidation("status", "invalid_enum");
        }
        // STEP-CAT-01 商品存在
        Product existing = productRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        // STEP-CAT-02 幂等：目标态=当前态 → 直接返回（不写审计不发事件不开事务——TC-CAT-035）
        if (existing.getStatus() == target) {
            return assembleListItems(List.of(existing)).get(0);
        }
        ProductStatus from = existing.getStatus();
        Map<String, Object> beforeChange = new LinkedHashMap<>();
        beforeChange.put("status", from == null ? null : from.getKey());
        Map<String, Object> afterChange = new LinkedHashMap<>();
        afterChange.put("status", target.getKey());
        transactionTemplate.executeWithoutResult(tx -> {
            // STEP-CAT-03 UPDATE + 审计（changes={from,to}）
            productRepository.updateStatus(id, target);
            audit.record("商品上下架", existing.getName(), changesJson(beforeChange, afterChange));
            // STEP-CAT-04 提交后失效链 + MQ（下架后 PDP 回 404501）
            String slug = existing.getSlug();
            afterCommit.run(() -> {
                cache.invalidateProductSlug(slug);
                cache.invalidateFamily(Family.PRODUCTS);
                cache.invalidateFamily(Family.RECO);
                cache.invalidateFamily(Family.CATEGORIES);
                cache.invalidateFamily(Family.TAGS);
                invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_PRODUCT_STATUS_CHANGED, slug, null);
            });
        });
        Product refreshed = productRepository.findById(id);
        return assembleListItems(List.of(refreshed)).get(0);
    }

    // ==================== E-CAT-14 patchAdminProductFlags（TX-CAT-005） ====================

    @Transactional
    public AdminProductListItem patchFlags(Long id, Boolean isNew, Boolean isBest, Boolean recommend, Integer sort) {
        // V-CAT-041 至少一个字段（minProperties=1）
        if (isNew == null && isBest == null && recommend == null && sort == null) {
            throw CatalogException.fieldValidation("_body", "empty");
        }
        // V-CAT-042 sort >= 0
        if (sort != null && sort < 0) {
            throw CatalogException.fieldValidation("sort", "range_invalid");
        }
        // STEP-CAT-01 商品存在
        Product existing = productRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        // STEP-CAT-02 仅 UPDATE 提交字段 + 审计（flags 行内变更归入"编辑商品"，changes before/after）
        Map<String, Object> before = flagsSnapshot(existing);
        productRepository.patchFlags(id, isNew, isBest, recommend, sort);
        Product refreshed = productRepository.findById(id);
        audit.record("编辑商品", existing.getName(), changesJson(before, flagsSnapshot(refreshed)));
        // STEP-CAT-03 提交后失效 reco/products + MQ
        String slug = existing.getSlug();
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_PRODUCT_FLAGS_CHANGED, slug, null);
        });
        return assembleListItems(List.of(refreshed)).get(0);
    }

    // ==================== 内部装配/校验 ====================

    /**
     * V-CAT-023~038 统一入口（引用上下文预查后委托纯校验器；动态属性白名单按分类生效配置解析）。
     * 编辑场景额外放行该商品已持久化的存量 key（迁移遗留/属性集后续收缩不阻塞整单保存，仅新增 key 严格校验）。
     */
    private void validateUpsert(AdminProductUpsert req, Set<Long> ownedSkuIds, Long productId) {
        boolean categoryExists = req.categoryId() != null
                && categoryRepository.findById(req.categoryId()) != null;
        Set<Long> existingTagIds = new HashSet<>();
        if (req.tagIds() != null && !req.tagIds().isEmpty()) {
            for (Tag tag : tagRepository.listByIds(req.tagIds())) {
                existingTagIds.add(tag.getId());
            }
        }
        Map<String, AttributeDef> defsByKey = new HashMap<>();
        Map<Long, String> keyById = new HashMap<>();
        for (AttributeDef def : attributeDefRepository.listAll()) {
            defsByKey.put(def.getKey(), def);
            keyById.put(def.getId(), def.getKey());
        }
        // 分类无效时跳过 not_in_category 检查（category_id 错误已收集），传 null 白名单
        Set<String> allowedKeys = null;
        if (categoryExists) {
            allowedKeys = new HashSet<>();
            for (ResolvedAttr attr : attributeConfigService.visibleAttrs(req.categoryId())) {
                allowedKeys.add(attr.def().getKey());
            }
            if (productId != null) {
                for (ProductAttributeValue row : attributeValueRepository.listByProductId(productId)) {
                    String key = keyById.get(row.getAttributeId());
                    if (key != null) {
                        allowedKeys.add(key);
                    }
                }
            }
        }
        ProductUpsertValidator.validate(req, categoryExists, existingTagIds, ownedSkuIds, defsByKey, allowedKeys);
    }

    /** STEP-CAT-02 sku_code 全局唯一（409504 details.sku_codes） */
    private void checkSkuCodes(List<SkuDto> skus, Long exceptProductId) {
        if (skus == null || skus.isEmpty()) {
            return;
        }
        List<String> codes = skus.stream().map(SkuDto::skuCode).toList();
        List<String> taken = skuRepository.existsBySkuCodes(codes, exceptProductId);
        if (!taken.isEmpty()) {
            throw new CatalogException(CatalogErrorCode.SKU_CODE_EXISTS, Map.of("sku_codes", taken));
        }
    }

    /** STEP-CAT-03（E-CAT-11）updated_at 并发比对（毫秒截断；解析失败 → 422501） */
    private void checkUpdatedAtConflict(Product existing, String updatedAt) {
        LocalDateTime carried;
        try {
            carried = LocalDateTime.parse(updatedAt);
        } catch (DateTimeParseException ex) {
            throw CatalogException.fieldValidation("updated_at", "invalid_format");
        }
        LocalDateTime dbValue = existing.getUpdatedAt();
        if (dbValue != null
                && !dbValue.truncatedTo(ChronoUnit.MILLIS).equals(carried.truncatedTo(ChronoUnit.MILLIS))) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
        }
    }

    /** SKU 差异化处理（TX-CAT-002 STEP-CAT-03/05；CAS affected=0 → 409508 整体回滚） */
    private void applySkuDiff(Long productId, List<Sku> ownedSkus, List<SkuDto> incoming) {
        Map<Long, Sku> ownedById = new HashMap<>();
        for (Sku sku : ownedSkus) {
            ownedById.put(sku.getId(), sku);
        }
        Set<Long> keptIds = new HashSet<>();
        List<Sku> toInsert = new ArrayList<>();
        if (incoming != null) {
            for (SkuDto dto : incoming) {
                if (dto.id() != null) {
                    keptIds.add(dto.id());
                    Sku row = new Sku();
                    row.setId(dto.id());
                    row.setSkuCode(dto.skuCode());
                    row.setColor(dto.color());
                    row.setSize(dto.size());
                    row.setStock(dto.stock() == null ? 0 : dto.stock());
                    // RM-CAT-122 CAS：version 不匹配 → 409508（EC-CAT-001 编辑场景不重试）
                    int affected = skuRepository.casUpdate(row, dto.version());
                    if (affected == 0) {
                        throw new CatalogException(CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
                    }
                } else {
                    Sku row = new Sku();
                    row.setProductId(productId);
                    row.setSkuCode(dto.skuCode());
                    row.setColor(dto.color());
                    row.setSize(dto.size());
                    row.setStock(dto.stock() == null ? 0 : dto.stock());
                    row.setVersion(0L);
                    toInsert.add(row);
                }
            }
        }
        // 缺席的既有行 DELETE（订单快照不受影响）
        List<Long> toDelete = ownedById.keySet().stream().filter(skuId -> !keptIds.contains(skuId)).toList();
        skuRepository.deleteByIds(toDelete);
        skuRepository.insertBatch(toInsert);
    }

    /** Upsert → Entity 字段映射（冗余列绝不触碰） */
    private void applyUpsert(Product product, AdminProductUpsert req) {
        product.setName(req.name().trim());
        product.setSlug(req.slug());
        product.setSubtitle(req.subtitle());
        product.setCategoryId(req.categoryId());
        product.setProductType(req.productType());
        product.setDescription(req.description());
        product.setDesignerNote(req.designerNote());
        product.setPrice(req.price());
        product.setCompareAt(req.compareAt());
        product.setInstallment(req.installment() != null && req.installment());
        product.setMultiCurrencyPrices(req.multiCurrencyPrices());
        product.setStatus(ProductStatus.of(req.status()));
        product.setIsNew(req.isNew() != null && req.isNew());
        product.setIsBest(req.isBest() != null && req.isBest());
        product.setRecommend(req.recommend() != null && req.recommend());
        product.setSort(req.sort() == null ? 0 : req.sort());
        product.setLeadTimeDays(req.leadTimeDays());
        product.setRushAvailable(req.rushAvailable() != null && req.rushAvailable());
        product.setCustomSizeAvailable(req.customSizeAvailable() != null && req.customSizeAvailable());
        product.setFabricComposition(req.fabricComposition());
        product.setModelHeight(req.modelHeight());
        product.setModelSize(req.modelSize());
        product.setModelBodyType(req.modelBodyType());
        product.setCareInstructions(req.careInstructions());
        product.setCountryOfOrigin(req.countryOfOrigin());
        product.setStyleNo(req.styleNo());
        product.setSeoTitle(req.seoTitle());
        product.setSeoDesc(req.seoDesc());
    }

    /**
     * MAP-CAT-003 列表行批量装配（category_name / stock_total / image_url 单次 IN 批查）。
     * sales_total：admin-prototype-alignment RM-CAT-01b 取本页 product_ids 后一次聚合（避免 N+1）+
     * RM-CAT-01c 内存合并到 DTO（缺失 product_id → 0）；listAdminProducts / 导出 CSV 共用同一派生逻辑（API-CAT-03）。
     */
    private List<AdminProductListItem> assembleListItems(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Integer> stockTotals = skuRepository.sumStockByProductIds(ids);
        // RM-CAT-01b 本页 product_ids 一次聚合（trading 域端口下沉 SQL：order_line JOIN orders status IN 已支付后态）
        Map<Long, Integer> salesTotals = tradingQueryPort.sumSalesTotalByProductIds(ids);
        Map<Long, String> mainImages = new HashMap<>();
        for (ProductImage image : imageRepository.listByProductIds(ids)) {
            if (image.getKind() == ImageKind.GALLERY && image.getSort() != null && image.getSort() == 0) {
                mainImages.putIfAbsent(image.getProductId(), image.getUrl());
            }
        }
        Map<Long, String> categoryNames = new HashMap<>();
        for (Category category : categoryRepository.listAll()) {
            categoryNames.put(category.getId(), category.getName());
        }
        List<AdminProductListItem> items = new ArrayList<>(products.size());
        for (Product p : products) {
            items.add(new AdminProductListItem(p.getId(), p.getName(), p.getSlug(), p.getStyleNo(),
                    p.getCategoryId(), categoryNames.get(p.getCategoryId()), p.getPrice(), p.getCompareAt(),
                    p.getStatus() == null ? null : p.getStatus().getKey(), p.getIsNew(), p.getIsBest(),
                    p.getRecommend(), p.getSort(), stockTotals.getOrDefault(p.getId(), 0),
                    mainImages.get(p.getId()),
                    // RM-CAT-01c 缺失 product_id → sales_total = 0
                    salesTotals.getOrDefault(p.getId(), 0)));
        }
        return items;
    }

    /** MAP-CAT-004 全量回读（E-CAT-10 STEP-CAT-01~03；E-CAT-09/11 出参） */
    private AdminProductDetail loadDetail(Long id) {
        Product product = productRepository.findById(id);
        if (product == null) {
            return null;
        }
        List<ProductImageDto> images = imageRepository.listByProductId(id).stream()
                .map(i -> new ProductImageDto(i.getId(), i.getUrl(),
                        i.getKind() == null ? null : i.getKind().getKey(), i.getColorName(), i.getSort()))
                .toList();
        List<SkuDto> skus = skuRepository.listByProductId(id).stream()
                .map(s -> new SkuDto(s.getId(), s.getSkuCode(), s.getColor(), s.getSize(), s.getStock(),
                        s.getVersion()))
                .toList();
        List<SizeChartRowDto> sizeChart = sizeChartRepository.listByProductIdOrderById(id).stream()
                .map(r -> new SizeChartRowDto(r.getId(), r.getUs(), r.getUk(), r.getAu(),
                        r.getBust(), r.getWaist(), r.getHips(), r.getHollowToFloor()))
                .toList();
        List<Long> tagIds = productTagRepository.listTagIdsByProductId(id);
        List<ProductTranslationDto> translations = translationRepository.listByProductIds(List.of(id), null)
                .stream()
                .map(t -> new ProductTranslationDto(t.getLocale(), t.getName(), t.getSubtitle(),
                        t.getDescription(), t.getSeoTitle(), t.getSeoDescription()))
                .toList();
        return new AdminProductDetail(product.getId(), product.getName(), product.getSlug(),
                product.getSubtitle(), product.getCategoryId(), product.getProductType(),
                product.getDescription(), product.getDesignerNote(), product.getPrice(), product.getCompareAt(),
                product.getInstallment(), product.getMultiCurrencyPrices(),
                product.getStatus() == null ? null : product.getStatus().getKey(),
                product.getIsNew(), product.getIsBest(), product.getRecommend(), product.getSort(),
                product.getLeadTimeDays(), product.getRushAvailable(), product.getCustomSizeAvailable(),
                loadAttributeDtos(id), product.getFabricComposition(),
                product.getModelHeight(), product.getModelSize(), product.getModelBodyType(),
                product.getCareInstructions(), product.getCountryOfOrigin(), product.getStyleNo(),
                product.getSeoTitle(), product.getSeoDesc(), images, skus, sizeChart, tagIds, translations,
                product.getCreatedAt(), product.getUpdatedAt());
    }

    /** EAV 回读 → entries 数组（key 取字典；按属性首行 id 序分组，行序即写入序） */
    private List<AttributeValueDto> loadAttributeDtos(Long productId) {
        List<ProductAttributeValue> rows = attributeValueRepository.listByProductId(productId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> keyById = new HashMap<>();
        for (AttributeDef def : attributeDefRepository.listAll()) {
            keyById.put(def.getId(), def.getKey());
        }
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (ProductAttributeValue row : rows) {
            String key = keyById.get(row.getAttributeId());
            if (key != null) {
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row.getValue());
            }
        }
        List<AttributeValueDto> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            result.add(new AttributeValueDto(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** entries 数组 → EAV 行（key→def id；值去重保序；空值跳过——校验器已保证合法性） */
    private List<ProductAttributeValue> toAttributeRows(List<AttributeValueDto> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, Long> idByKey = new HashMap<>();
        for (AttributeDef def : attributeDefRepository.listAll()) {
            idByKey.put(def.getKey(), def.getId());
        }
        List<ProductAttributeValue> rows = new ArrayList<>();
        for (AttributeValueDto entry : entries) {
            Long attributeId = entry.key() == null ? null : idByKey.get(entry.key());
            if (attributeId == null || entry.values() == null) {
                continue;
            }
            for (String value : new LinkedHashSet<>(entry.values())) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                ProductAttributeValue row = new ProductAttributeValue();
                row.setAttributeId(attributeId);
                row.setValue(value);
                rows.add(row);
            }
        }
        return rows;
    }

    private List<ProductImage> toImageRows(List<ProductImageDto> dtos) {
        List<ProductImage> rows = new ArrayList<>();
        if (dtos != null) {
            for (ProductImageDto dto : dtos) {
                ProductImage row = new ProductImage();
                row.setUrl(dto.url());
                row.setKind(ImageKind.of(dto.kind()));
                row.setColorName(dto.colorName());
                row.setSort(dto.sort() == null ? 0 : dto.sort());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Sku> toSkuRows(Long productId, List<SkuDto> dtos) {
        List<Sku> rows = new ArrayList<>();
        if (dtos != null) {
            for (SkuDto dto : dtos) {
                Sku row = new Sku();
                row.setProductId(productId);
                row.setSkuCode(dto.skuCode());
                row.setColor(dto.color());
                row.setSize(dto.size());
                row.setStock(dto.stock() == null ? 0 : dto.stock());
                row.setVersion(0L);
                rows.add(row);
            }
        }
        return rows;
    }

    private List<SizeChartRow> toSizeChartRows(List<SizeChartRowDto> dtos) {
        List<SizeChartRow> rows = new ArrayList<>();
        if (dtos != null) {
            for (SizeChartRowDto dto : dtos) {
                SizeChartRow row = new SizeChartRow();
                row.setUs(dto.us());
                row.setUk(dto.uk());
                row.setAu(dto.au());
                row.setBust(dto.bust());
                row.setWaist(dto.waist());
                row.setHips(dto.hips());
                row.setHollowToFloor(dto.hollowToFloor());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<ProductTranslation> toTranslationRows(List<ProductTranslationDto> dtos) {
        List<ProductTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (ProductTranslationDto dto : dtos) {
                ProductTranslation row = new ProductTranslation();
                row.setLocale(dto.locale());
                row.setName(dto.name());
                row.setSubtitle(dto.subtitle());
                row.setDescription(dto.description());
                row.setSeoTitle(dto.seoTitle());
                row.setSeoDescription(dto.seoDescription());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Long> dedupe(List<Long> ids) {
        return ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private Map<String, Object> flagsSnapshot(Product p) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("is_new", p.getIsNew());
        snapshot.put("is_best", p.getIsBest());
        snapshot.put("recommend", p.getRecommend());
        snapshot.put("sort", p.getSort());
        return snapshot;
    }

    private String changesJson(Map<String, Object> before, Map<String, Object> after) {
        try {
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("before", before);
            changes.put("after", after);
            return objectMapper.writeValueAsString(changes);
        } catch (Exception ex) {
            return null;
        }
    }
}
