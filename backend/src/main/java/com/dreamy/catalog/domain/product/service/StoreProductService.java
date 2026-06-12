package com.dreamy.catalog.domain.product.service;

import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.entity.AttributeDefTranslation;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.attribute.service.ProductAttributeConfigService;
import com.dreamy.catalog.domain.attribute.service.ProductAttributeConfigService.ResolvedAttr;
import com.dreamy.catalog.domain.category.entity.Category;
import com.dreamy.catalog.domain.category.repository.CategoryRepository;
import com.dreamy.catalog.domain.category.service.CategoryTreeService;
import com.dreamy.catalog.domain.enums.AttributeType;
import com.dreamy.catalog.domain.enums.TagStatus;
import com.dreamy.catalog.domain.product.entity.Product;
import com.dreamy.catalog.domain.product.entity.ProductAttributeValue;
import com.dreamy.catalog.domain.product.entity.ProductTag;
import com.dreamy.catalog.domain.product.entity.ProductTranslation;
import com.dreamy.catalog.domain.product.entity.Sku;
import com.dreamy.catalog.domain.product.repository.ProductAttributeValueRepository;
import com.dreamy.catalog.domain.product.repository.ProductImageRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository;
import com.dreamy.catalog.domain.product.repository.ProductRepository.StoreFilter;
import com.dreamy.catalog.domain.product.repository.ProductTagRepository;
import com.dreamy.catalog.domain.product.repository.ProductTranslationRepository;
import com.dreamy.catalog.domain.product.repository.SizeChartRowRepository;
import com.dreamy.catalog.domain.product.repository.SkuRepository;
import com.dreamy.catalog.domain.tag.entity.Tag;
import com.dreamy.catalog.domain.tag.entity.TagTranslation;
import com.dreamy.catalog.domain.tag.repository.TagRepository;
import com.dreamy.catalog.dto.ProductImageDto;
import com.dreamy.catalog.dto.SizeChartRowDto;
import com.dreamy.catalog.dto.SkuDto;
import com.dreamy.catalog.dto.StoreAttributeDtos.OptionDto;
import com.dreamy.catalog.dto.StoreAttributeDtos.StoreAttributeDto;
import com.dreamy.catalog.dto.StoreAttributeDtos.StoreFilterDimDto;
import com.dreamy.catalog.dto.StoreProductCard;
import com.dreamy.catalog.dto.StoreProductDetail;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.infra.CatalogCacheService;
import com.dreamy.catalog.infra.CatalogCacheService.Family;
import com.dreamy.catalog.support.FieldErrors;
import com.dreamy.catalog.support.PaginatedSupport;
import com.dreamy.catalog.support.StoreParams;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 消费端商品服务（E-CAT-01 列表 / E-CAT-02 搜索 / E-CAT-04 PDP）。
 * L2 TRACE: FLOW-P01/P02 / CACHE-CAT-001~003 / MAP-CAT-001/002 / QP-CAT-001/002。
 */
@Service
public class StoreProductService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final ProductRepository productRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductImageRepository imageRepository;
    private final SkuRepository skuRepository;
    private final SizeChartRowRepository sizeChartRepository;
    private final ProductTagRepository productTagRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryTreeService treeService;
    private final ProductCardAssembler cardAssembler;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final AttributeDefRepository attributeDefRepository;
    private final ProductAttributeConfigService attributeConfigService;
    private final CatalogCacheService cache;

    public StoreProductService(ProductRepository productRepository,
                               ProductTranslationRepository translationRepository,
                               ProductImageRepository imageRepository, SkuRepository skuRepository,
                               SizeChartRowRepository sizeChartRepository,
                               ProductTagRepository productTagRepository, TagRepository tagRepository,
                               CategoryRepository categoryRepository, CategoryTreeService treeService,
                               ProductCardAssembler cardAssembler,
                               ProductAttributeValueRepository attributeValueRepository,
                               AttributeDefRepository attributeDefRepository,
                               ProductAttributeConfigService attributeConfigService,
                               CatalogCacheService cache) {
        this.productRepository = productRepository;
        this.translationRepository = translationRepository;
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.productTagRepository = productTagRepository;
        this.tagRepository = tagRepository;
        this.categoryRepository = categoryRepository;
        this.treeService = treeService;
        this.cardAssembler = cardAssembler;
        this.attributeValueRepository = attributeValueRepository;
        this.attributeDefRepository = attributeDefRepository;
        this.attributeConfigService = attributeConfigService;
        this.cache = cache;
    }

    /** E-CAT-01 商品列表查询参数（V-CAT-001~005 已解析；attrs 已规范化排序——key/值均字典序） */
    public record ListQuery(String locale, int page, int pageSize, Long categoryId, Long tagId,
                            String color, String size, BigDecimal priceMin, BigDecimal priceMax, String sort,
                            Map<String, Set<String>> attrs) {
    }

    /** E-CAT-01：列表（FLOW-P01；缓存 key=catalog:products:{filtersHash}:{locale} TTL 300s） */
    @SuppressWarnings("unchecked")
    public Paginated<StoreProductCard> listProducts(ListQuery q) {
        // STEP-CAT-00 attrs key→attribute_id 解析（未知 key 忽略——防爬虫 422 噪音；
        // 先于缓存 key 解析：?attr=junk:x 与无 attr 共享同一缓存条目，防 key 碎片化）
        Map<String, Set<String>> validAttrs = new TreeMap<>();
        Map<Long, Set<String>> attrFilters = resolveAttrFilters(q.attrs(), validAttrs);
        // STEP-CAT-01 缓存 key（filtersHash=全部筛选参数规范化序列化，空页同样缓存防穿透）
        String cacheKey = filtersHash(q, validAttrs) + ":" + q.locale();
        Object cached = cache.get(Family.PRODUCTS, cacheKey);
        if (cached instanceof Paginated<?> hit) {
            return (Paginated<StoreProductCard>) hit;
        }
        // STEP-CAT-02 分类子树解析（含自身；不存在 → 空集 → 空页）
        List<Long> categoryIds = treeService.subtreeIds(q.categoryId());
        // STEP-CAT-03 查询 + 排序 + 分页（RM-CAT-083）
        Page<Product> page = productRepository.pageStoreList(
                new StoreFilter(categoryIds, q.tagId(), q.color(), q.size(), q.priceMin(), q.priceMax(), q.sort(),
                        attrFilters),
                q.page(), q.pageSize());
        // STEP-CAT-04/05 批量装配卡片 + 翻译合并（NP-CAT-001）
        List<StoreProductCard> cards = cardAssembler.assemble(page.getRecords(), q.locale());
        Paginated<StoreProductCard> result = PaginatedSupport.of(cards, page.getTotal(), q.page(), q.pageSize());
        // STEP-CAT-06 写缓存（TTL 300s）
        cache.put(Family.PRODUCTS, cacheKey, result);
        return result;
    }

    /** E-CAT-02：全文搜索（FLOW-P02 决策 17；缓存 key=catalog:search:{qNorm}:{locale}:{page} TTL 60s） */
    @SuppressWarnings("unchecked")
    public Paginated<StoreProductCard> search(String q, String locale, int page, int pageSize) {
        // V-CAT-006 q 必填 trim 1..80
        FieldErrors errors = new FieldErrors();
        String qNorm = q == null ? "" : q.trim().toLowerCase();
        if (qNorm.isEmpty() || qNorm.length() > 80) {
            errors.reject("q", qNorm.isEmpty() ? "required" : "too_long");
        }
        errors.throwIfAny();
        // STEP-CAT-01 查缓存（key 追加 page_size 防止同页不同页宽误命中——CACHE-CAT-003 模板 {page} 段实现口径）
        String cacheKey = qNorm + ":" + locale + ":" + page + "-" + pageSize;
        Object cached = cache.get(Family.SEARCH, cacheKey);
        if (cached instanceof Paginated<?> hit) {
            return (Paginated<StoreProductCard>) hit;
        }
        // STEP-CAT-02 EN 主检索（RM-CAT-084 FULLTEXT ngram，相关度序）
        Set<Long> merged = new LinkedHashSet<>(productRepository.fulltextSearchMain(qNorm));
        // STEP-CAT-03 locale=es/fr → 附表检索 UNION（RM-CAT-103）
        if ("es".equals(locale) || "fr".equals(locale)) {
            merged.addAll(translationRepository.fulltextSearch(qNorm, locale));
        }
        // STEP-CAT-04 标签命中（RM-CAT-064/146）：tag.name/label LIKE → published 商品并入
        for (Long tagId : tagRepository.searchEnabledByName(qNorm, locale)) {
            for (Long productId : productTagRepository.listProductIdsByTagId(tagId, 200)) {
                merged.add(productId);
            }
        }
        // STEP-CAT-05 合并去重（QP-CAT-002 Service 内存完成）→ 分页 → 装配
        List<Long> ids = new ArrayList<>(merged);
        int from = Math.min((page - 1) * pageSize, ids.size());
        int to = Math.min(from + pageSize, ids.size());
        List<Long> pageIds = ids.subList(from, to);
        // 标签命中路径可能引入未发布商品 id——按 published 批查后按序回排
        Map<Long, Product> byId = new HashMap<>();
        for (Product p : productRepository.listByIds(pageIds)) {
            if (p.getStatus() != null && "published".equals(p.getStatus().getKey())) {
                byId.put(p.getId(), p);
            }
        }
        List<Product> ordered = pageIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        List<StoreProductCard> cards = cardAssembler.assemble(ordered, locale);
        Paginated<StoreProductCard> result = PaginatedSupport.of(cards, ids.size(), page, pageSize);
        // STEP-CAT-06 写缓存 TTL 60s（短 TTL 自然过期兜底，无主动失效；CDN 不缓存）
        cache.put(Family.SEARCH, cacheKey, result);
        return result;
    }

    /** E-CAT-04：PDP（FLOW-P01；null 缓存穿透保护——CACHE-CAT-002） */
    public StoreProductDetail getProduct(String slug, String locale) {
        // V-CAT-012 slug pattern/长度（不匹配 → 404501 同口径防探测）
        if (slug == null || slug.length() > 128 || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        // STEP-CAT-01 查缓存（null 标记 → 404501）
        String cacheKey = slug + ":" + locale;
        Object cached = cache.get(Family.PRODUCT, cacheKey);
        if (cache.isNullMarker(cached)) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        if (cached instanceof StoreProductDetail hit) {
            return hit;
        }
        // STEP-CAT-02 点查（RM-CAT-080）；不存在/未发布 → null 缓存 60s → 404501
        Product product = productRepository.findBySlugPublished(slug);
        if (product == null) {
            cache.putNullMarker(Family.PRODUCT, cacheKey);
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }
        StoreProductDetail detail = assembleDetail(product, locale);
        // STEP-CAT-06 写缓存 TTL 300s
        cache.put(Family.PRODUCT, cacheKey, detail);
        return detail;
    }

    /** STEP-CAT-03~06 装配（各一次 IN/点查，MAP-CAT-002） */
    private StoreProductDetail assembleDetail(Product product, String locale) {
        Long id = product.getId();
        // STEP-CAT-03 子资源
        List<ProductImageDto> images = imageRepository.listByProductId(id).stream()
                .map(i -> new ProductImageDto(i.getId(), i.getUrl(),
                        i.getKind() == null ? null : i.getKind().getKey(), i.getColorName(), i.getSort()))
                .toList();
        List<SkuDto> skus = skuRepository.listByProductId(id).stream()
                .map(s -> new SkuDto(s.getId(), s.getSkuCode(), s.getColor(), s.getSize(), s.getStock(), s.getVersion()))
                .toList();
        List<SizeChartRowDto> sizeChart = sizeChartRepository.listByProductIdOrderById(id).stream()
                .map(StoreProductService::toRowDto)
                .toList();
        // 标签（仅 enabled）+ locale 翻译
        List<Long> tagIds = productTagRepository.listTagIdsByProductId(id);
        List<Tag> tags = tagRepository.listByIds(tagIds).stream()
                .filter(t -> t.getStatus() == TagStatus.ENABLED)
                .toList();
        Map<Long, String> tagNames = new HashMap<>();
        if (("es".equals(locale) || "fr".equals(locale)) && !tags.isEmpty()) {
            for (TagTranslation t : tagRepository.listTranslationsByTagIds(tags.stream().map(Tag::getId).toList())) {
                if (locale.equals(t.getLocale()) && t.getLabel() != null && !t.getLabel().isBlank()) {
                    tagNames.put(t.getTagId(), t.getLabel());
                }
            }
        }
        List<StoreProductDetail.TagRef> tagRefs = tags.stream()
                .map(t -> new StoreProductDetail.TagRef(t.getId(), t.getDimensionId(),
                        tagNames.getOrDefault(t.getId(), t.getName())))
                .toList();
        // STEP-CAT-04 分类名派生（es/fr 经 category_translation 解析）
        String categoryName = resolveCategoryName(product.getCategoryId(), locale);
        // STEP-CAT-05 翻译覆盖（缺翻译回退 EN）
        ProductTranslation tr = cardAssembler.translationsFor(List.of(id), locale).get(id);
        return new StoreProductDetail(
                product.getId(), product.getSlug(),
                ProductCardAssembler.pick(tr == null ? null : tr.getName(), product.getName()),
                ProductCardAssembler.pick(tr == null ? null : tr.getSubtitle(), product.getSubtitle()),
                product.getCategoryId(), categoryName, product.getProductType(),
                ProductCardAssembler.pick(tr == null ? null : tr.getDescription(), product.getDescription()),
                product.getDesignerNote(), product.getPrice(), product.getCompareAt(),
                product.getMultiCurrencyPrices(), product.getInstallment(), product.getIsNew(), product.getIsBest(),
                product.getLeadTimeDays(), product.getRushAvailable(), product.getCustomSizeAvailable(),
                buildStoreAttributes(product, locale),
                product.getFabricComposition(),
                product.getModelHeight(), product.getModelSize(), product.getModelBodyType(),
                product.getCareInstructions(), product.getCountryOfOrigin(), product.getStyleNo(),
                ProductCardAssembler.pick(tr == null ? null : tr.getSeoTitle(), product.getSeoTitle()),
                ProductCardAssembler.pick(tr == null ? null : tr.getSeoDescription(), product.getSeoDesc()),
                images, skus, sizeChart, tagRefs, product.getRatingAvg(), product.getRatingCount());
    }

    /**
     * PDP attributes 装配：按商品分类生效属性集顺序输出（hidden 排除——审查意见 ④），
     * 无值属性省略；label/值译文经 attribute_def_translation 同序映射，toggle 值本地化 Yes/No。
     */
    private List<StoreAttributeDto> buildStoreAttributes(Product product, String locale) {
        List<ProductAttributeValue> rows = attributeValueRepository.listByProductId(product.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> valuesByAttrId = new HashMap<>();
        for (ProductAttributeValue row : rows) {
            valuesByAttrId.computeIfAbsent(row.getAttributeId(), k -> new ArrayList<>()).add(row.getValue());
        }
        List<ResolvedAttr> config = attributeConfigService.visibleAttrs(product.getCategoryId());
        Map<Long, AttributeDefTranslation> translations = translationsForLocale(
                config.stream().map(a -> a.def().getId()).toList(), locale);
        List<StoreAttributeDto> result = new ArrayList<>();
        for (ResolvedAttr attr : config) {
            AttributeDef def = attr.def();
            List<String> values = valuesByAttrId.get(def.getId());
            if (values == null || values.isEmpty()) {
                continue;
            }
            AttributeDefTranslation tr = translations.get(def.getId());
            String label = tr != null && tr.getLabel() != null && !tr.getLabel().isBlank()
                    ? tr.getLabel() : def.getLabel();
            List<OptionDto> valueDtos = values.stream()
                    .map(v -> new OptionDto(v, localizeValue(def, tr, v, locale)))
                    .toList();
            result.add(new StoreAttributeDto(def.getKey(),
                    label, def.getType() == null ? null : def.getType().getKey(), valueDtos));
        }
        return result;
    }

    /** es/fr 译文批查（en → 空 map 直接回退主表） */
    private Map<Long, AttributeDefTranslation> translationsForLocale(List<Long> defIds, String locale) {
        Map<Long, AttributeDefTranslation> result = new HashMap<>();
        if (("es".equals(locale) || "fr".equals(locale)) && !defIds.isEmpty()) {
            for (AttributeDefTranslation t : attributeDefRepository.listTranslationsByDefIds(defIds)) {
                if (locale.equals(t.getLocale())) {
                    result.put(t.getAttributeDefId(), t);
                }
            }
        }
        return result;
    }

    /** 值译文：options 同序映射（CV-CAT-007 等长保证）；toggle 本地化 Yes/No；text 原样 */
    private static String localizeValue(AttributeDef def, AttributeDefTranslation tr, String value, String locale) {
        if (def.getType() == AttributeType.TOGGLE) {
            boolean yes = "true".equals(value);
            return switch (locale == null ? "en" : locale) {
                case "es" -> yes ? "Sí" : "No";
                case "fr" -> yes ? "Oui" : "Non";
                default -> yes ? "Yes" : "No";
            };
        }
        if (tr != null && tr.getOptions() != null && def.getOptions() != null) {
            int idx = def.getOptions().indexOf(value);
            if (idx >= 0 && idx < tr.getOptions().size()) {
                String translated = tr.getOptions().get(idx);
                if (translated != null && !translated.isBlank()) {
                    return translated;
                }
            }
        }
        return value;
    }

    /** attrs key→attribute_id（未知 key 忽略，validAttrsOut 仅收已知 key——缓存 key 数据源）；返回 attribute_id → 候选值集（OR/IN 语义） */
    private Map<Long, Set<String>> resolveAttrFilters(Map<String, Set<String>> attrs,
                                                      Map<String, Set<String>> validAttrsOut) {
        if (attrs == null || attrs.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> idByKey = new HashMap<>();
        for (AttributeDef def : attributeDefRepository.listAll()) {
            idByKey.put(def.getKey(), def.getId());
        }
        Map<Long, Set<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : attrs.entrySet()) {
            Long attributeId = idByKey.get(entry.getKey());
            if (attributeId != null && !entry.getValue().isEmpty()) {
                result.put(attributeId, entry.getValue());
                validAttrsOut.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * E-CAT-27 listStoreProductFilters：分类可筛属性维度（生效属性集中非 hidden 的 select/multiselect，
     * set 顺序；option=value+locale 译文）。缓存 key=filters:{categoryId}:{locale}（Family.PRODUCTS，
     * 属性字典/属性集/分类变更随族失效）。分类不存在 → 空列表。
     */
    @SuppressWarnings("unchecked")
    public List<StoreFilterDimDto> listFilters(Long categoryId, String locale) {
        String cacheKey = "filters:" + categoryId + ":" + locale;
        Object cached = cache.get(Family.PRODUCTS, cacheKey);
        if (cached instanceof List<?> hit) {
            return (List<StoreFilterDimDto>) hit;
        }
        List<ResolvedAttr> config = attributeConfigService.visibleAttrs(categoryId);
        Map<Long, AttributeDefTranslation> translations = translationsForLocale(
                config.stream().map(a -> a.def().getId()).toList(), locale);
        List<StoreFilterDimDto> result = new ArrayList<>();
        for (ResolvedAttr attr : config) {
            AttributeDef def = attr.def();
            if (def.getType() == null || !def.getType().optionsAllowed()
                    || def.getOptions() == null || def.getOptions().isEmpty()) {
                continue;
            }
            AttributeDefTranslation tr = translations.get(def.getId());
            String label = tr != null && tr.getLabel() != null && !tr.getLabel().isBlank()
                    ? tr.getLabel() : def.getLabel();
            List<OptionDto> options = def.getOptions().stream()
                    .map(v -> new OptionDto(v, localizeValue(def, tr, v, locale)))
                    .toList();
            result.add(new StoreFilterDimDto(def.getKey(), label, def.getType().getKey(), options));
        }
        cache.put(Family.PRODUCTS, cacheKey, result);
        return result;
    }

    private String resolveCategoryName(Long categoryId, String locale) {
        Category category = categoryRepository.findById(categoryId);
        if (category == null) {
            return null;
        }
        if ("es".equals(locale) || "fr".equals(locale)) {
            var translations = categoryRepository.listTranslationsByCategoryIds(List.of(categoryId));
            for (var t : translations) {
                if (locale.equals(t.getLocale()) && t.getName() != null && !t.getName().isBlank()) {
                    return t.getName();
                }
            }
        }
        return category.getName();
    }

    static SizeChartRowDto toRowDto(com.dreamy.catalog.domain.product.entity.SizeChartRow row) {
        return new SizeChartRowDto(row.getId(), row.getUs(), row.getUk(), row.getAu(),
                row.getBust(), row.getWaist(), row.getHips(), row.getHollowToFloor());
    }

    /** 筛选参数规范化序列化（STEP-CAT-01 filtersHash；attrs 已过滤未知 key 并按 key/值字典序——审查意见 ⑤/CACHE-KEY-01） */
    static String filtersHash(ListQuery q, Map<String, Set<String>> validAttrs) {
        StringBuilder attrs = new StringBuilder();
        if (validAttrs != null) {
            for (Map.Entry<String, Set<String>> entry : validAttrs.entrySet()) {
                if (attrs.length() > 0) {
                    attrs.append(';');
                }
                attrs.append(entry.getKey()).append(':').append(String.join(",", entry.getValue()));
            }
        }
        return "c=" + (q.categoryId() == null ? "-" : q.categoryId())
                + "|t=" + (q.tagId() == null ? "-" : q.tagId())
                + "|co=" + (q.color() == null ? "-" : q.color())
                + "|s=" + (q.size() == null ? "-" : q.size())
                + "|pm=" + (q.priceMin() == null ? "-" : q.priceMin().stripTrailingZeros().toPlainString())
                + "|px=" + (q.priceMax() == null ? "-" : q.priceMax().stripTrailingZeros().toPlainString())
                + "|so=" + q.sort()
                + "|a=" + (attrs.length() == 0 ? "-" : attrs)
                + "|p=" + q.page() + "|ps=" + q.pageSize();
    }

    /**
     * controller 共用入参解析（V-CAT-001~005）。
     * attr 重复参数（?attr=key:value&attr=key:value2）：每项首个 ':' 分隔 key/value（值内允许 ':'）；
     * 同 key 多值 = OR；跨 key = AND。格式非法/值超 255 → 422；TreeMap/TreeSet 规范化排序（缓存 key 稳定）。
     */
    public ListQuery parseListQuery(String locale, Integer page, Integer pageSize, Long categoryId, Long tagId,
                                    String color, String size, BigDecimal priceMin, BigDecimal priceMax,
                                    String sort, List<String> attrParams) {
        FieldErrors errors = new FieldErrors();
        String parsedLocale = StoreParams.parseLocale(locale, errors);
        int parsedPage = StoreParams.parsePage(page, errors);
        int parsedSize = StoreParams.parsePageSize(pageSize, errors);
        StoreParams.validatePriceRange(priceMin, priceMax, errors);
        String parsedSort = StoreParams.parseSort(sort, errors);
        Long parsedCategoryId = StoreParams.parsePositiveId(categoryId, "category_id", errors);
        Long parsedTagId = StoreParams.parsePositiveId(tagId, "tag_id", errors);
        String parsedColor = StoreParams.checkMaxLength(color, 32, "color", errors);
        String parsedSizeFilter = StoreParams.checkMaxLength(size, 16, "size", errors);
        Map<String, Set<String>> attrs = parseAttrParams(attrParams, errors);
        errors.throwIfAny();
        return new ListQuery(parsedLocale, parsedPage, parsedSize, parsedCategoryId, parsedTagId,
                parsedColor, parsedSizeFilter, priceMin, priceMax, parsedSort, attrs);
    }

    /** attr 参数解析（最多 20 项防滥用） */
    private static Map<String, Set<String>> parseAttrParams(List<String> attrParams, FieldErrors errors) {
        Map<String, Set<String>> attrs = new TreeMap<>();
        if (attrParams == null || attrParams.isEmpty()) {
            return attrs;
        }
        if (attrParams.size() > 20) {
            errors.reject("attr", "too_many");
            return attrs;
        }
        for (String raw : attrParams) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int idx = raw.indexOf(':');
            if (idx <= 0 || idx == raw.length() - 1) {
                errors.reject("attr", "invalid_format");
                return attrs;
            }
            String key = raw.substring(0, idx).trim();
            String value = raw.substring(idx + 1).trim();
            if (key.isEmpty() || value.isEmpty() || key.length() > 64 || value.length() > 255) {
                errors.reject("attr", "invalid_format");
                return attrs;
            }
            attrs.computeIfAbsent(key, k -> new TreeSet<>()).add(value);
        }
        return attrs;
    }
}
