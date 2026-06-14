package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.attribute.entity.AttributeDef;
import com.dreamy.domain.attribute.entity.AttributeDefTranslation;
import com.dreamy.domain.attribute.entity.AttributeSet;
import com.dreamy.domain.attribute.entity.AttributeSetItem;
import com.dreamy.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.domain.attribute.repository.AttributeSetRepository;
import com.dreamy.domain.category.entity.Category;
import com.dreamy.domain.category.entity.CategoryTranslation;
import com.dreamy.domain.category.repository.CategoryRepository;
import com.dreamy.enums.AttributeType;
import com.dreamy.enums.AttributeVisibility;
import com.dreamy.enums.ImageKind;
import com.dreamy.enums.ProductStatus;
import com.dreamy.enums.TagStatus;
import com.dreamy.domain.product.entity.Product;
import com.dreamy.domain.product.entity.ProductAttributeValue;
import com.dreamy.domain.product.entity.ProductImage;
import com.dreamy.domain.product.entity.ProductTranslation;
import com.dreamy.domain.product.entity.SizeChartRow;
import com.dreamy.domain.product.entity.Sku;
import com.dreamy.domain.product.repository.ProductAttributeValueRepository;
import com.dreamy.domain.product.repository.ProductImageRepository;
import com.dreamy.domain.product.repository.ProductMapper;
import com.dreamy.domain.product.repository.ProductRepository;
import com.dreamy.domain.product.repository.ProductTagRepository;
import com.dreamy.domain.product.repository.ProductTranslationRepository;
import com.dreamy.domain.product.repository.SizeChartRowRepository;
import com.dreamy.domain.product.repository.SkuRepository;
import com.dreamy.domain.tag.entity.Tag;
import com.dreamy.domain.tag.entity.TagDimension;
import com.dreamy.domain.tag.entity.TagDimensionTranslation;
import com.dreamy.domain.tag.entity.TagTranslation;
import com.dreamy.domain.tag.repository.TagDimensionRepository;
import com.dreamy.domain.tag.repository.TagRepository;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.product.entity.CareInstructionDef;
import com.dreamy.domain.product.repository.CareInstructionDefRepository;
import com.dreamy.enums.CareCategory;
import com.dreamy.enums.CareStatus;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * catalog 域种子数据初始化（决策 21：从 frontend portal-store mock（data/products.ts）提炼
 * 商品/分类/属性集/标签样例 + 三语 translation）。幂等：product 表非空即跳过（按业务键查→缺则建，
 * 与 identity DataInitializer 同惯例——Long 自增主键无法硬编码 id）。
 * 同时登记 RBAC 权限点 /attribute-sets（BE-DIM-6 本域新增权限点之一；/products、/categories 已在 identity 种子）。
 */
@Component
@Order(20)
public class CatalogSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedInitializer.class);
    private static final String REF = "/competitor-refs";

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final SkuRepository skuRepository;
    private final SizeChartRowRepository sizeChartRepository;
    private final ProductTagRepository productTagRepository;
    private final ProductTranslationRepository productTranslationRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final CategoryRepository categoryRepository;
    private final AttributeDefRepository attributeDefRepository;
    private final AttributeSetRepository attributeSetRepository;
    private final TagDimensionRepository tagDimensionRepository;
    private final TagRepository tagRepository;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final JdbcTemplate jdbcTemplate;
    private final CareInstructionDefRepository careInstructionDefRepository;

    public CatalogSeedInitializer(ProductMapper productMapper, ProductRepository productRepository,
                                  ProductImageRepository imageRepository, SkuRepository skuRepository,
                                  SizeChartRowRepository sizeChartRepository,
                                  ProductTagRepository productTagRepository,
                                  ProductTranslationRepository productTranslationRepository,
                                  ProductAttributeValueRepository attributeValueRepository,
                                  CategoryRepository categoryRepository,
                                  AttributeDefRepository attributeDefRepository,
                                  AttributeSetRepository attributeSetRepository,
                                  TagDimensionRepository tagDimensionRepository, TagRepository tagRepository,
                                  PermissionMapper permissionMapper, RoleMapper roleMapper,
                                  RolePermissionMapper rolePermissionMapper, JdbcTemplate jdbcTemplate,
                                  CareInstructionDefRepository careInstructionDefRepository) {
        this.productMapper = productMapper;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.skuRepository = skuRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.productTagRepository = productTagRepository;
        this.productTranslationRepository = productTranslationRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.categoryRepository = categoryRepository;
        this.attributeDefRepository = attributeDefRepository;
        this.attributeSetRepository = attributeSetRepository;
        this.tagDimensionRepository = tagDimensionRepository;
        this.tagRepository = tagRepository;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.careInstructionDefRepository = careInstructionDefRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        ensureAttributeSetsPermission();
        clearCatalogData();
        Map<String, Long> defs = seedAttributeDefs();
        Map<String, Long> sets = seedAttributeSets(defs);
        Map<String, Long> categories = seedCategories(sets);
        Map<String, Long> tags = seedTagsAndDimensions();
        seedProducts(defs, categories, tags);
        seedCareInstructions();
        log.info("[CatalogSeed] catalog 种子数据初始化完成");
    }

    /** 清空 catalog 相关表（FK 检查临时关闭，启动时全量重建） */
    private void clearCatalogData() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : List.of(
                "product_attribute_value", "product_tag", "product_image",
                "product_translation", "sku", "size_chart_row", "product",
                "attribute_set_item", "attribute_set", "attribute_def",
                "category_translation", "category",
                "tag_translation", "product_tag", "tag", "tag_dimension_translation", "tag_dimension",
                "product_care_instruction", "product_fabric_composition", "care_instruction_def")) {
            jdbcTemplate.execute("TRUNCATE TABLE `" + table + "`");
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        log.info("[CatalogSeed] catalog 数据已清空，准备重建");
    }

    /** RBAC 权限点 /attribute-sets（幂等按 perm_code）+ 绑定超管角色 */
    private void ensureAttributeSetsPermission() {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, "/attribute-sets"));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode("/attribute-sets");
            permission.setGroup("商品管理");
            permission.setLabel("属性集");
            permissionMapper.insert(permission);
            log.info("[CatalogSeed] 权限点 /attribute-sets 已登记");
        }
        Role superRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getName, "超级管理员"));
        if (superRole != null) {
            Long bound = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRoleId, superRole.getId())
                    .eq(RolePermission::getPermissionId, permission.getId()));
            if (bound == null || bound == 0) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(superRole.getId());
                rp.setPermissionId(permission.getId());
                rolePermissionMapper.insert(rp);
            }
        }
    }

    private Map<String, Long> seedAttributeDefs() {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put("silhouette", insertDef("silhouette", "Silhouette", AttributeType.SELECT,
                List.of("A-Line", "Mermaid", "Sheath", "Fit & Flare", "Bodycon"),
                List.of("Silueta", "Silhouette"),
                List.of(List.of("Línea A", "Sirena", "Recto", "Evasé", "Ajustado"),
                        List.of("Ligne A", "Sirène", "Fourreau", "Évasée", "Moulante"))));
        ids.put("neckline", insertDef("neckline", "Neckline", AttributeType.SELECT,
                List.of("One-Shoulder", "V-Neck", "Deep-V", "Strapless", "Halter", "Sweetheart"),
                List.of("Escote", "Encolure"),
                List.of(List.of("Un hombro", "Cuello V", "V profundo", "Palabra de honor", "Halter", "Corazón"),
                        List.of("Asymétrique", "Col V", "V plongeant", "Bustier", "Dos-nu", "Cœur"))));
        ids.put("sleeve", insertDef("sleeve", "Sleeve", AttributeType.SELECT,
                List.of("Sleeveless", "Long Sleeve", "Strap"), null, null));
        ids.put("back_style", insertDef("back_style", "Back Style", AttributeType.SELECT,
                List.of("Open Back", "Lace-Up", "Zipper", "Button", "Keyhole"), null, null));
        ids.put("waistline", insertDef("waistline", "Waistline", AttributeType.SELECT,
                List.of("Natural", "Empire", "Drop", "Basque"), null, null));
        ids.put("train", insertDef("train", "Train", AttributeType.SELECT,
                List.of("None", "Sweep", "Chapel", "Cathedral"), null, null));
        ids.put("length", insertDef("length", "Length", AttributeType.SELECT,
                List.of("Floor", "Short"), null, null));
        ids.put("fabric", insertDef("fabric", "Fabric", AttributeType.SELECT,
                List.of("Tulle", "Lace", "Chiffon", "Satin", "Luxe Knit", "Sequin"), null, null));
        ids.put("fabric_composition", insertDef("fabric_composition", "Fabric Composition", AttributeType.TEXT, null, null, null));
        ids.put("support", insertDef("support", "Support", AttributeType.SELECT,
                List.of("Built-in Bra", "Boning", "Padded Cups", "None"), null, null));
        ids.put("season", insertDef("season", "Season", AttributeType.SELECT,
                List.of("Spring", "Summer", "Fall", "Winter"), null, null));
        ids.put("embellishment", insertDef("embellishment", "Embellishments", AttributeType.MULTISELECT,
                List.of("Lace", "Beading", "Sequins", "Embroidery", "Appliqué", "Pearls"), null, null));
        ids.put("occasion", insertDef("occasion", "Occasions", AttributeType.MULTISELECT,
                List.of("Garden", "Beach", "Vineyard", "Forest"), null, null));
        ids.put("style_tag", insertDef("style_tag", "Style Tags", AttributeType.MULTISELECT,
                List.of("Boho", "Classic", "Modern", "Romantic", "Minimalist", "Glam"), null, null));
        // 护理和模特信息属性（迁移自 product 固定列）
        ids.put("care_instructions", insertDef("care_instructions", "Care Instructions", AttributeType.TEXT, null, null, null));
        ids.put("model_height", insertDef("model_height", "Model Height", AttributeType.TEXT, null, null, null));
        ids.put("model_size", insertDef("model_size", "Model Size", AttributeType.TEXT, null, null, null));
        ids.put("model_body_type", insertDef("model_body_type", "Model Body Type", AttributeType.TEXT, null, null, null));
        ids.put("country_of_origin", insertDef("country_of_origin", "Country of Origin", AttributeType.TEXT, null, null, null));
        return ids;
    }

    private Long insertDef(String key, String label, AttributeType type, List<String> options,
                           List<String> esFrLabels, List<List<String>> esFrOptions) {
        AttributeDef def = new AttributeDef();
        def.setKey(key);
        def.setLabel(label);
        def.setType(type);
        def.setOptions(options);
        attributeDefRepository.insert(def);
        if (esFrLabels != null) {
            List<AttributeDefTranslation> rows = new ArrayList<>();
            String[] locales = {"es", "fr"};
            for (int i = 0; i < 2; i++) {
                AttributeDefTranslation t = new AttributeDefTranslation();
                t.setLocale(locales[i]);
                t.setLabel(esFrLabels.get(i));
                t.setOptions(esFrOptions == null ? null : esFrOptions.get(i));
                rows.add(t);
            }
            attributeDefRepository.replaceTranslations(def.getId(), rows);
        }
        return def.getId();
    }

    private Map<String, Long> seedAttributeSets(Map<String, Long> defs) {
        Map<String, Long> ids = new LinkedHashMap<>();
        // LinkedHashMap 保持矩阵行序（set item 顺序 = 后台表单/PDP 属性展示顺序）
        Map<String, AttributeVisibility> bridal = new LinkedHashMap<>();
        bridal.put("silhouette", AttributeVisibility.VISIBLE);
        bridal.put("neckline", AttributeVisibility.VISIBLE);
        bridal.put("sleeve", AttributeVisibility.OPTIONAL);
        bridal.put("back_style", AttributeVisibility.OPTIONAL);
        bridal.put("waistline", AttributeVisibility.OPTIONAL);
        bridal.put("train", AttributeVisibility.OPTIONAL);
        bridal.put("length", AttributeVisibility.OPTIONAL);
        bridal.put("fabric", AttributeVisibility.VISIBLE);
        bridal.put("fabric_composition", AttributeVisibility.OPTIONAL);
        bridal.put("support", AttributeVisibility.OPTIONAL);
        bridal.put("season", AttributeVisibility.OPTIONAL);
        bridal.put("embellishment", AttributeVisibility.OPTIONAL);
        bridal.put("occasion", AttributeVisibility.OPTIONAL);
        bridal.put("style_tag", AttributeVisibility.OPTIONAL);
        bridal.put("care_instructions", AttributeVisibility.OPTIONAL);
        bridal.put("model_height", AttributeVisibility.OPTIONAL);
        bridal.put("model_size", AttributeVisibility.OPTIONAL);
        bridal.put("model_body_type", AttributeVisibility.OPTIONAL);
        bridal.put("country_of_origin", AttributeVisibility.OPTIONAL);
        ids.put("bridal", insertSet("Bridal Gown Attributes", defs, bridal));
        Map<String, AttributeVisibility> occasion = new LinkedHashMap<>();
        occasion.put("silhouette", AttributeVisibility.VISIBLE);
        occasion.put("neckline", AttributeVisibility.OPTIONAL);
        occasion.put("fabric", AttributeVisibility.VISIBLE);
        occasion.put("fabric_composition", AttributeVisibility.OPTIONAL);
        occasion.put("length", AttributeVisibility.VISIBLE);
        occasion.put("season", AttributeVisibility.OPTIONAL);
        occasion.put("occasion", AttributeVisibility.OPTIONAL);
        occasion.put("style_tag", AttributeVisibility.OPTIONAL);
        occasion.put("care_instructions", AttributeVisibility.HIDDEN);
        occasion.put("country_of_origin", AttributeVisibility.OPTIONAL);
        ids.put("occasion", insertSet("Occasion Dress Attributes", defs, occasion));
        Map<String, AttributeVisibility> accessory = new LinkedHashMap<>();
        accessory.put("fabric", AttributeVisibility.OPTIONAL);
        accessory.put("fabric_composition", AttributeVisibility.OPTIONAL);
        accessory.put("occasion", AttributeVisibility.OPTIONAL);
        accessory.put("care_instructions", AttributeVisibility.OPTIONAL);
        accessory.put("country_of_origin", AttributeVisibility.OPTIONAL);
        ids.put("accessory", insertSet("Accessory Attributes", defs, accessory));
        return ids;
    }

    private Long insertSet(String label, Map<String, Long> defs, Map<String, AttributeVisibility> matrix) {
        AttributeSet set = new AttributeSet();
        set.setLabel(label);
        attributeSetRepository.insert(set);
        List<AttributeSetItem> items = new ArrayList<>();
        int sortOrder = 0;
        for (Map.Entry<String, AttributeVisibility> entry : matrix.entrySet()) {
            AttributeSetItem item = new AttributeSetItem();
            item.setAttributeId(defs.get(entry.getKey()));
            item.setVisibility(entry.getValue());
            // 按矩阵 LinkedHashMap 声明顺序赋 sort_order（= 后台表单/PDP 展示顺序）；
            // 漏赋值会让全行 sort_order=0，读取平局时 MySQL 返回顺序未定义 → 重启抖动
            item.setSortOrder(sortOrder++);
            items.add(item);
        }
        attributeSetRepository.replaceItems(set.getId(), items);
        return set.getId();
    }

    private Map<String, Long> seedCategories(Map<String, Long> sets) {
        Map<String, Long> ids = new HashMap<>();
        Long wedding = insertCategory("Wedding Dresses", null, 1, sets.get("bridal"), 0,
                "Vestidos de Novia", "Robes de Mariée");
        Long occasion = insertCategory("Special Occasion", null, 1, sets.get("occasion"), 1,
                "Ocasiones Especiales", "Occasions Spéciales");
        Long accessories = insertCategory("Accessories", null, 1, sets.get("accessory"), 2,
                "Accesorios", "Accessoires");
        ids.put("wedding-aline", insertCategory("A-Line", wedding, 2, null, 0, "Línea A", "Ligne A"));
        ids.put("wedding-mermaid", insertCategory("Mermaid", wedding, 2, null, 1, "Sirena", "Sirène"));
        ids.put("wedding-short", insertCategory("Short", wedding, 2, null, 2, "Corto", "Courte"));
        ids.put("occasion-bridesmaid", insertCategory("Bridesmaid", occasion, 2, null, 0,
                "Dama de Honor", "Demoiselle d'Honneur"));
        ids.put("occasion-prom", insertCategory("Prom", occasion, 2, null, 1, null, null));
        ids.put("occasion-evening", insertCategory("Evening", occasion, 2, null, 2, "Noche", "Soirée"));
        ids.put("occasion-cocktail", insertCategory("Cocktail", occasion, 2, null, 3, null, null));
        ids.put("acc-veils", insertCategory("Veils", accessories, 2, null, 0, "Velos", "Voiles"));
        ids.put("acc-shoes", insertCategory("Shoes", accessories, 2, null, 1, "Zapatos", "Chaussures"));
        ids.put("acc-jewelry", insertCategory("Jewelry", accessories, 2, null, 2, "Joyería", "Bijoux"));
        ids.put("acc-headpieces", insertCategory("Headpieces", accessories, 2, null, 3,
                "Tocados", "Coiffes"));
        return ids;
    }

    private Long insertCategory(String name, Long parentId, int level, Long attributeSetId, int sort,
                                String esName, String frName) {
        Category category = new Category();
        category.setName(name);
        category.setParentId(parentId);
        category.setLevel(level);
        category.setAttributeSetId(attributeSetId);
        category.setSort(sort);
        categoryRepository.insert(category);
        List<CategoryTranslation> translations = new ArrayList<>();
        if (esName != null) {
            CategoryTranslation es = new CategoryTranslation();
            es.setLocale("es");
            es.setName(esName);
            translations.add(es);
        }
        if (frName != null) {
            CategoryTranslation fr = new CategoryTranslation();
            fr.setLocale("fr");
            fr.setName(frName);
            translations.add(fr);
        }
        if (!translations.isEmpty()) {
            categoryRepository.replaceTranslations(category.getId(), translations);
        }
        return category.getId();
    }

    private Map<String, Long> seedTagsAndDimensions() {
        Map<String, Long> tagIds = new HashMap<>();
        // Color 维度（Shop by Color 调色板，mock palette 8 色）
        Long colorDim = insertDimension("Color", "Shop by Color palette for outdoor weddings",
                "Color", "Couleur");
        String[][] colors = {
                {"Sage", "Salvia", "Sauge"}, {"Dusty Blue", "Azul Empolvado", "Bleu Poudré"},
                {"Blush", "Rubor", "Rose Poudré"}, {"Champagne", "Champán", "Champagne"},
                {"Lavender", "Lavanda", "Lavande"}, {"Terracotta", "Terracota", "Terracotta"},
                {"Ivory", "Marfil", "Ivoire"}, {"Espresso", "Café", "Expresso"}
        };
        for (String[] c : colors) {
            tagIds.put("color:" + c[0], insertTag(colorDim, c[0], c[1], c[2]));
        }
        // Theme 维度（婚礼场景主题）
        Long themeDim = insertDimension("Theme", "Outdoor wedding themes", "Tema", "Thème");
        String[][] themes = {
                {"Garden", "Jardín", "Jardin"}, {"Beach", "Playa", "Plage"},
                {"Vineyard", "Viñedo", "Vignoble"}, {"Forest", "Bosque", "Forêt"}
        };
        for (String[] t : themes) {
            tagIds.put("theme:" + t[0], insertTag(themeDim, t[0], t[1], t[2]));
        }
        return tagIds;
    }

    private Long insertDimension(String name, String description, String esName, String frName) {
        TagDimension dim = new TagDimension();
        dim.setName(name);
        dim.setDescription(description);
        tagDimensionRepository.insert(dim);
        List<TagDimensionTranslation> translations = new ArrayList<>();
        TagDimensionTranslation es = new TagDimensionTranslation();
        es.setLocale("es");
        es.setName(esName);
        translations.add(es);
        TagDimensionTranslation fr = new TagDimensionTranslation();
        fr.setLocale("fr");
        fr.setName(frName);
        translations.add(fr);
        tagDimensionRepository.replaceTranslations(dim.getId(), translations);
        return dim.getId();
    }

    private Long insertTag(Long dimensionId, String name, String esLabel, String frLabel) {
        Tag tag = new Tag();
        tag.setDimensionId(dimensionId);
        tag.setName(name);
        tag.setStatus(TagStatus.ENABLED);
        tagRepository.insert(tag);
        List<TagTranslation> translations = new ArrayList<>();
        TagTranslation es = new TagTranslation();
        es.setLocale("es");
        es.setLabel(esLabel);
        translations.add(es);
        TagTranslation fr = new TagTranslation();
        fr.setLocale("fr");
        fr.setLabel(frLabel);
        translations.add(fr);
        tagRepository.replaceTranslations(tag.getId(), translations);
        return tag.getId();
    }

    // ==================== 商品（mock data/products.ts 16 款全量提炼） ====================

    private record SeedProduct(String slug, String name, String subtitle, String categoryKey,
                               String price, String compareAt, boolean isNew, boolean isBest,
                               List<String> gallery, String lifestyle, List<String[]> colors,
                               List<String> sizes, boolean customSize, String silhouette, String fabric,
                               String neckline, String sleeve, String length, List<String> themes,
                               String ratingAvg, int ratingCount, String description, String care,
                               String esName, String frName) {
    }

    private void seedProducts(Map<String, Long> defs, Map<String, Long> categories, Map<String, Long> tags) {
        for (SeedProduct sp : seedProductData()) {
            insertProduct(sp, defs, categories, tags);
        }
    }

    private void insertProduct(SeedProduct sp, Map<String, Long> defs, Map<String, Long> categories,
                               Map<String, Long> tags) {
        Product p = new Product();
        p.setName(sp.name());
        p.setSlug(sp.slug());
        p.setCategoryId(categories.get(sp.categoryKey()));
        p.setPrice(new BigDecimal(sp.price()));
        p.setCompareAt(sp.compareAt() == null ? null : new BigDecimal(sp.compareAt()));
        p.setInstallment(p.getPrice().compareTo(new BigDecimal("200")) > 0);
        p.setStatus(ProductStatus.PUBLISHED);
        p.setIsNew(sp.isNew());
        p.setIsBest(sp.isBest());
        p.setRecommend(sp.isBest());
        p.setSort(0);
        boolean isAccessory = sp.categoryKey().startsWith("acc-");
        p.setLeadTimeDays(isAccessory ? 7 : 45);
        p.setRushAvailable(!isAccessory);
        p.setCustomSizeAvailable(sp.customSize());
        p.setDescription(sp.description());
        p.setStyleNo("D-" + sp.slug().toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0,
                Math.min(8, sp.slug().replaceAll("[^a-zA-Z0-9]", "").length())));
        p.setSeoTitle(sp.name() + " | Dreamy");
        // 评分冗余列种子值取自 mock rating/reviewCount（运行期由 EVT-CAT-002 覆盖写接管）
        p.setSales30d(0);
        p.setRatingAvg(new BigDecimal(sp.ratingAvg()));
        p.setRatingCount(sp.ratingCount());
        productRepository.insert(p);
        // 版型属性 → EAV（multiselect occasion 一值一行）
        List<ProductAttributeValue> attrRows = new ArrayList<>();
        addAttrRow(attrRows, defs, "silhouette", sp.silhouette());
        addAttrRow(attrRows, defs, "fabric", sp.fabric());
        addAttrRow(attrRows, defs, "neckline", sp.neckline());
        addAttrRow(attrRows, defs, "sleeve", sp.sleeve());
        addAttrRow(attrRows, defs, "length", sp.length());
        for (String theme : sp.themes()) {
            addAttrRow(attrRows, defs, "occasion", theme);
        }
        attributeValueRepository.replaceAll(p.getId(), attrRows);
        // images：gallery（sort 0..n 主图=0）+ lifestyle + 每色 swatch
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < sp.gallery().size(); i++) {
            images.add(image(sp.gallery().get(i), ImageKind.GALLERY, null, i));
        }
        if (sp.lifestyle() != null) {
            images.add(image(sp.lifestyle(), ImageKind.LIFESTYLE, null, 0));
        }
        for (String[] color : sp.colors()) {
            images.add(image(color[1], ImageKind.SWATCH, color[0], 0));
        }
        imageRepository.replaceAll(p.getId(), images);
        // skus：颜色 × 尺码矩阵（Custom 不建 SKU——决策 6 定制不扣库存）
        List<Sku> skus = new ArrayList<>();
        for (String[] color : sp.colors()) {
            for (String size : sp.sizes()) {
                Sku sku = new Sku();
                sku.setProductId(p.getId());
                sku.setSkuCode(skuCode(sp.slug(), color[0], size));
                sku.setColor(color[0]);
                sku.setSize(size);
                sku.setStock(8);
                sku.setVersion(0L);
                skus.add(sku);
            }
        }
        skuRepository.insertBatch(skus);
        // size chart（裙装类目标准 US0~US14 八档）
        if (!isAccessory) {
            sizeChartRepository.replaceAll(p.getId(), standardSizeChart());
        }
        // tags：颜色命中调色板 + 主题
        List<Long> tagIds = new ArrayList<>();
        for (String[] color : sp.colors()) {
            Long tagId = tags.get("color:" + color[0]);
            if (tagId != null && !tagIds.contains(tagId)) {
                tagIds.add(tagId);
            }
        }
        for (String theme : sp.themes()) {
            Long tagId = tags.get("theme:" + theme);
            if (tagId != null && !tagIds.contains(tagId)) {
                tagIds.add(tagId);
            }
        }
        productTagRepository.replaceAll(p.getId(), tagIds);
        // 三语 translation 样例（验证决策 13 回退合并）
        if (sp.esName() != null || sp.frName() != null) {
            List<ProductTranslation> translations = new ArrayList<>();
            if (sp.esName() != null) {
                ProductTranslation es = new ProductTranslation();
                es.setLocale("es");
                es.setName(sp.esName());
                translations.add(es);
            }
            if (sp.frName() != null) {
                ProductTranslation fr = new ProductTranslation();
                fr.setLocale("fr");
                fr.setName(sp.frName());
                translations.add(fr);
            }
            productTranslationRepository.replaceAll(p.getId(), translations);
        }
    }

    private ProductImage image(String url, ImageKind kind, String colorName, int sort) {
        ProductImage img = new ProductImage();
        img.setUrl(url);
        img.setKind(kind);
        img.setColorName(colorName);
        img.setSort(sort);
        return img;
    }

    private static void addAttrRow(List<ProductAttributeValue> rows, Map<String, Long> defs,
                                   String key, String value) {
        Long attributeId = defs.get(key);
        if (attributeId == null || value == null || value.isBlank()) {
            return;
        }
        ProductAttributeValue row = new ProductAttributeValue();
        row.setAttributeId(attributeId);
        row.setValue(value);
        rows.add(row);
    }

    /** SKU 码：^[A-Z0-9-]+$（slug 首段-颜色-尺码 全大写去非法字符） */
    private static String skuCode(String slug, String color, String size) {
        String base = slug.split("-")[0].toUpperCase().replaceAll("[^A-Z0-9]", "");
        String c = color.toUpperCase().replaceAll("[^A-Z0-9]", "");
        String s = size.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return base + "-" + c + "-" + s;
    }

    private List<SizeChartRow> standardSizeChart() {
        String[][] rows = {
                {"0", "4", "4", "31.5", "24.5", "34.5", "58.0"},
                {"2", "6", "6", "32.5", "25.5", "35.5", "58.5"},
                {"4", "8", "8", "33.5", "26.5", "36.5", "59.0"},
                {"6", "10", "10", "34.5", "27.5", "37.5", "59.5"},
                {"8", "12", "12", "35.5", "28.5", "38.5", "60.0"},
                {"10", "14", "14", "36.5", "29.5", "39.5", "60.5"},
                {"12", "16", "16", "38.0", "31.0", "41.0", "61.0"},
                {"14", "18", "18", "39.5", "32.5", "42.5", "61.5"}
        };
        List<SizeChartRow> chart = new ArrayList<>();
        for (String[] r : rows) {
            SizeChartRow row = new SizeChartRow();
            row.setUs(r[0]);
            row.setUk(r[1]);
            row.setAu(r[2]);
            row.setBust(new BigDecimal(r[3]));
            row.setWaist(new BigDecimal(r[4]));
            row.setHips(new BigDecimal(r[5]));
            row.setHollowToFloor(new BigDecimal(r[6]));
            chart.add(row);
        }
        return chart;
    }

    private List<SeedProduct> seedProductData() {
        List<SeedProduct> list = new ArrayList<>();
        list.add(new SeedProduct("aurelia-gown", "Aurelia A-Line Tulle Gown",
                "Romantic layered tulle for golden-hour ceremonies", "wedding-aline", "1280", "1480",
                false, true,
                List.of(REF + "/kissprom/wedding-aline-tulle-01.jpg", REF + "/kissprom/wedding-aline-lace-02.jpg",
                        REF + "/davidsbridal/wedding-dress-02.jpg"),
                REF + "/davidsbridal/wedding-dress-04.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/kissprom/wedding-aline-tulle-01.jpg"},
                        new String[]{"Champagne", REF + "/kissprom/wedding-aline-longsleeve-06.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8", "US 10", "US 12"), true,
                "A-Line", "Tulle", "One-Shoulder", "Sleeveless", "Floor",
                List.of("Garden", "Beach", "Vineyard"), "4.9", 142,
                "A romantic A-line silhouette in airy layered tulle, designed to catch the golden hour light.",
                "Shell: 100% nylon tulle; Professional dry clean only",
                "Vestido Aurelia de Tul Línea A", "Robe Aurelia en Tulle Ligne A"));
        list.add(new SeedProduct("celeste-lace-gown", "Celeste V-Neck Lace Gown",
                "Floral lace appliqué with a modern edge", "wedding-aline", "1490", null, true, false,
                List.of(REF + "/kissprom/wedding-aline-lace-02.jpg", REF + "/davidsbridal/wedding-dress-03.jpg"),
                REF + "/davidsbridal/wedding-dress-04.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/kissprom/wedding-aline-lace-02.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8", "US 10", "US 14"), true,
                "A-Line", "Lace", "V-Neck", "Sleeveless", "Floor",
                List.of("Garden", "Forest"), "4.8", 98,
                "Delicate floral lace appliqué cascades over a flattering V-neck bodice.",
                "Shell: corded lace over satin; Spot clean or dry clean", null, null));
        list.add(new SeedProduct("marina-mermaid-gown", "Marina Mermaid Chiffon Gown",
                "Figure-sculpting mermaid with dramatic train", "wedding-mermaid", "1620", null, false, false,
                List.of(REF + "/kissprom/wedding-mermaid-chiffon-03.jpg",
                        REF + "/kissprom/wedding-mermaid-lace-04.jpg"),
                REF + "/davidsbridal/wedding-dress-04.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/kissprom/wedding-mermaid-chiffon-03.jpg"}),
                List.of("US 2", "US 4", "US 6", "US 8", "US 10"), true,
                "Mermaid", "Chiffon", "Deep-V", "Sleeveless", "Floor",
                List.of("Beach", "Vineyard"), "4.7", 64,
                "A breathtaking mermaid silhouette in flowing chiffon with delicate appliqués.",
                "Shell: 100% chiffon; Dry clean only", null, null));
        list.add(new SeedProduct("willow-longsleeve-gown", "Willow Long-Sleeve Chiffon Gown",
                "Ethereal illusion sleeves for forest ceremonies", "wedding-aline", "1380", null, true, false,
                List.of(REF + "/kissprom/wedding-aline-longsleeve-06.jpg",
                        REF + "/davidsbridal/wedding-dress-06.jpg"),
                REF + "/davidsbridal/wedding-dress-04.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/kissprom/wedding-aline-longsleeve-06.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8"), true,
                "A-Line", "Chiffon", "V-Neck", "Long Sleeve", "Floor",
                List.of("Forest", "Garden"), "4.9", 77,
                "Ethereal long sleeves in sheer chiffon bring a touch of forest-fairytale romance.",
                "Shell: chiffon; Dry clean only", null, null));
        list.add(new SeedProduct("coraline-beach-gown", "Coraline Short Beach Gown",
                "Playful high-low hem for sand between your toes", "wedding-short", "890", "1020",
                false, false,
                List.of(REF + "/kissprom/wedding-beach-short-05.jpg",
                        REF + "/davidsbridal/wedding-dress-set-07.jpg"),
                REF + "/kissprom/prom-champagne-lace-05.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/kissprom/wedding-beach-short-05.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8"), false,
                "Sheath", "Satin", "Strapless", "Sleeveless", "Short",
                List.of("Beach"), "4.6", 53,
                "A playful high-low silhouette made for sand between your toes.",
                "Shell: stretch satin; Hand wash cold or dry clean", null, null));
        list.add(new SeedProduct("seabreeze-bridesmaid", "Seabreeze One-Shoulder Bridesmaid Dress",
                "The bridesmaid dress your whole party will re-wear", "occasion-bridesmaid", "168", null,
                false, true,
                List.of(REF + "/birdygrey/bridesmaid-pink-bella-01.jpg",
                        REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg"),
                REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg",
                List.<String[]>of(new String[]{"Blush", REF + "/birdygrey/bridesmaid-pink-bella-01.jpg"},
                        new String[]{"Espresso", REF + "/birdygrey/bridesmaid-espresso-mia-05.jpg"},
                        new String[]{"Black", REF + "/birdygrey/bridesmaid-black-mia-06.jpg"},
                        new String[]{"Lemon", REF + "/birdygrey/bridesmaid-lemon-bella-10.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8", "US 10", "US 12", "US 14", "Plus 16", "Plus 18"),
                true, "A-Line", "Luxe Knit", "One-Shoulder", "Sleeveless", "Floor",
                List.of("Garden", "Beach"), "4.8", 311,
                "A buttery luxe-knit one-shoulder style in 18+ shades, designed to flatter every body.",
                "Shell: 95% polyester, 5% spandex; Machine wash cold",
                "Vestido de Dama Seabreeze", "Robe Demoiselle Seabreeze"));
        list.add(new SeedProduct("meadow-bridesmaid", "Meadow Sage Bridesmaid Dress",
                "Flowing chiffon in coveted garden shades", "occasion-bridesmaid", "158", null, false, true,
                List.of(REF + "/davidsbridal/bridesmaid-sage-01.jpg",
                        REF + "/davidsbridal/bridesmaid-olive-07.jpg"),
                REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg",
                List.<String[]>of(new String[]{"Sage", REF + "/davidsbridal/bridesmaid-sage-01.jpg"},
                        new String[]{"Olive", REF + "/davidsbridal/bridesmaid-olive-07.jpg"},
                        new String[]{"Dusty Blue", REF + "/davidsbridal/bridesmaid-dustyblue-04.jpg"},
                        new String[]{"Steel Blue", REF + "/davidsbridal/bridesmaid-steelblue-02.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8", "US 10", "US 12", "Plus 16"), true,
                "A-Line", "Chiffon", "V-Neck", "Sleeveless", "Floor",
                List.of("Garden", "Forest", "Vineyard"), "4.9", 204,
                "Flowing chiffon in the most coveted garden shades for sage and earth-toned weddings.",
                "Shell: 100% chiffon; Dry clean recommended", null, null));
        list.add(new SeedProduct("petal-bridesmaid", "Petal Coral Bridesmaid Dress",
                "Warm sunset shades that glow against golden hour", "occasion-bridesmaid", "162", null,
                false, false,
                List.of(REF + "/davidsbridal/bridesmaid-coral-03.jpg",
                        REF + "/davidsbridal/bridesmaid-petal-06.jpg"),
                REF + "/birdygrey/bridesmaid-pink-bryten-02.jpg",
                List.<String[]>of(new String[]{"Coral", REF + "/davidsbridal/bridesmaid-coral-03.jpg"},
                        new String[]{"Petal", REF + "/davidsbridal/bridesmaid-petal-06.jpg"},
                        new String[]{"Ballet", REF + "/davidsbridal/bridesmaid-ballet-05.jpg"}),
                List.of("US 2", "US 4", "US 6", "US 8", "US 10"), true,
                "A-Line", "Chiffon", "Halter", "Sleeveless", "Floor",
                List.of("Beach", "Vineyard"), "4.7", 119,
                "A halter chiffon style with soft drape, perfect for terracotta and coral palettes.",
                "Shell: chiffon; Dry clean", null, null));
        list.add(new SeedProduct("aria-prom-dress", "Aria One-Shoulder Prom Gown",
                "Showstopping satin in eight dreamy shades", "occasion-prom", "248", null, true, false,
                List.of(REF + "/kissprom/prom-sage-oneshoulder-01.jpg",
                        REF + "/kissprom/prom-skyblue-oneshoulder-02.jpg"),
                null,
                List.<String[]>of(new String[]{"Sage", REF + "/kissprom/prom-sage-oneshoulder-01.jpg"},
                        new String[]{"Sky Blue", REF + "/kissprom/prom-skyblue-oneshoulder-02.jpg"},
                        new String[]{"Blush", REF + "/kissprom/prom-blush-oneshoulder-03.jpg"},
                        new String[]{"Lavender", REF + "/kissprom/prom-lavender-oneshoulder-04.jpg"}),
                List.of("US 00", "US 0", "US 2", "US 4", "US 6", "US 8", "US 10"), true,
                "A-Line", "Satin", "One-Shoulder", "Sleeveless", "Floor",
                List.of("Garden"), "4.6", 88,
                "A showstopping one-shoulder satin gown with a sculpted bodice and floor-sweeping skirt.",
                "Shell: stretch satin; Dry clean only", null, null));
        list.add(new SeedProduct("juliet-lace-gown", "Juliet Lace Evening Gown",
                "Floral lace over a flowing slip for evening elegance", "occasion-evening", "286", null,
                false, false,
                List.of(REF + "/kissprom/prom-champagne-lace-05.jpg",
                        REF + "/kissprom/prom-darkgreen-lace-06.jpg"),
                null,
                List.<String[]>of(new String[]{"Champagne", REF + "/kissprom/prom-champagne-lace-05.jpg"},
                        new String[]{"Dark Green", REF + "/kissprom/prom-darkgreen-lace-06.jpg"},
                        new String[]{"Lavender", REF + "/kissprom/prom-lavender-lace-09.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8"), true,
                "A-Line", "Lace", "Sweetheart", "Strap", "Floor",
                List.of("Vineyard", "Forest"), "4.8", 71,
                "Floral lace appliqué over a flowing slip, with delicate straps and a sweetheart neckline.",
                "Shell: corded lace; Dry clean", null, null));
        list.add(new SeedProduct("bloom-cocktail-dress", "Bloom Floral Cocktail Dress",
                "Flirty tiered tulle for garden-party guests", "occasion-cocktail", "198", null, false, false,
                List.of(REF + "/kissprom/prom-floral-sweetheart-08.jpg",
                        REF + "/kissprom/prom-offshoulder-tiered-07.jpg"),
                null,
                List.<String[]>of(new String[]{"Floral", REF + "/kissprom/prom-floral-sweetheart-08.jpg"},
                        new String[]{"Pink", REF + "/kissprom/homecoming-pink-short-01.jpg"}),
                List.of("US 0", "US 2", "US 4", "US 6", "US 8"), false,
                "Fit & Flare", "Tulle", "Sweetheart", "Sleeveless", "Short",
                List.of("Garden"), "4.5", 46,
                "A flirty short cocktail dress with a sweetheart neckline and playful tiered tulle.",
                "Shell: tulle over satin; Spot clean", null, null));
        list.add(new SeedProduct("luna-homecoming-dress", "Luna Sequin Homecoming Dress",
                "All-over sequins for the after-party", "occasion-cocktail", "142", null, false, false,
                List.of(REF + "/kissprom/homecoming-pink-sequin-03.jpg",
                        REF + "/kissprom/homecoming-darkgreen-short-02.jpg"),
                null,
                List.<String[]>of(new String[]{"Pink Sequin", REF + "/kissprom/homecoming-pink-sequin-03.jpg"},
                        new String[]{"Dark Green", REF + "/kissprom/homecoming-darkgreen-short-02.jpg"}),
                List.of("US 00", "US 0", "US 2", "US 4", "US 6"), false,
                "Bodycon", "Sequin", "One-Shoulder", "Sleeveless", "Short",
                List.of("Vineyard"), "4.4", 39,
                "All-over sequins and a sculpted one-shoulder bodice make this short style shine.",
                "Shell: sequin mesh; Spot clean only", null, null));
        list.add(new SeedProduct("cathedral-veil", "Aurelle Cathedral Veil",
                "Single-tier lace-trimmed cathedral veil", "acc-veils", "158", null, false, true,
                List.of(REF + "/birdygrey/accessory-jewelry-01.jpg"),
                REF + "/davidsbridal/wedding-dress-04.jpg",
                List.<String[]>of(new String[]{"Ivory", REF + "/birdygrey/accessory-jewelry-01.jpg"}),
                List.of("Cathedral 108", "Chapel 90", "Fingertip 36"), false,
                null, "Tulle", null, null, null,
                List.of("Garden", "Forest", "Vineyard"), "4.9", 87,
                "A breathtaking single-tier cathedral veil with a delicate lace trim.",
                "Tulle with corded lace trim; Steam to release wrinkles", null, null));
        list.add(new SeedProduct("pearl-heels", "Margaux Pearl Block Heels",
                "Comfortable pearl-adorned block heels", "acc-shoes", "128", null, false, false,
                List.of(REF + "/birdygrey/accessory-pjs-02.jpg"),
                null,
                List.<String[]>of(new String[]{"Ivory", REF + "/birdygrey/accessory-pjs-02.jpg"}),
                List.of("US 5", "US 6", "US 7", "US 8", "US 9", "US 10"), false,
                null, "Satin", null, null, null,
                List.of("Garden", "Beach"), "4.6", 52,
                "Comfortable block heels adorned with hand-placed pearls.",
                "Satin upper; Wipe clean", null, null));
        list.add(new SeedProduct("drop-earrings", "Estelle Crystal Drop Earrings",
                "Crystal drops on gold-fill hooks", "acc-jewelry", "68", null, false, true,
                List.of(REF + "/birdygrey/accessory-jewelry-01.jpg"),
                null,
                List.<String[]>of(new String[]{"Gold", REF + "/birdygrey/accessory-jewelry-01.jpg"}),
                List.of("One Size"), false,
                null, null, null, null, null,
                List.of("Vineyard", "Garden"), "4.8", 134,
                "Delicate crystal drops on a gold-fill hook. Catches the light beautifully in photos.",
                "Gold-fill & crystal; Store dry", null, null));
        list.add(new SeedProduct("hair-vine", "Fleur Gold Hair Vine",
                "Bendable gold vine with pearls and leaves", "acc-headpieces", "78", null, false, false,
                List.of(REF + "/birdygrey/accessory-jewelry-01.jpg"),
                REF + "/birdygrey/lifestyle-flowergirl-08.jpg",
                List.<String[]>of(new String[]{"Gold", REF + "/birdygrey/accessory-jewelry-01.jpg"}),
                List.of("One Size"), false,
                null, null, null, null, null,
                List.of("Garden", "Forest"), "4.7", 61,
                "A flexible gold hair vine scattered with leaves and pearls.",
                "Gold-tone wire; Handle gently", null, null));
        return list;
    }

    private void seedCareInstructions() {
        record C(String code, String sym, String en, String zh, CareCategory cat, int sort) {}
        List<C> data = List.of(
            new C("hand_wash_cold",  "🫧", "Hand wash cold",       "冷水手洗",   CareCategory.WASHING,     1),
            new C("machine_wash_30", "🌀", "Machine wash 30°C",    "30°C 机洗",  CareCategory.WASHING,     2),
            new C("dry_clean_only",  "⭕", "Dry clean only",       "仅限干洗",   CareCategory.DRY_CLEANING,1),
            new C("no_bleach",       "🚫", "Do not bleach",        "禁止漂白",   CareCategory.BLEACHING,   1),
            new C("tumble_dry_low",  "🌡", "Tumble dry low",       "低温烘干",   CareCategory.DRYING,      1),
            new C("hang_to_dry",     "🪝", "Hang to dry",          "悬挂晾干",   CareCategory.DRYING,      2),
            new C("low_iron",        "♨", "Iron on low heat",     "低温熨烫",   CareCategory.IRONING,     1),
            new C("steam_only",      "💨", "Steam only",           "仅蒸汽熨烫", CareCategory.IRONING,     2),
            new C("do_not_iron",     "❌", "Do not iron",          "禁止熨烫",   CareCategory.IRONING,     3)
        );
        for (C c : data) {
            CareInstructionDef def = new CareInstructionDef();
            def.setCode(c.code());
            def.setSymbolUnicode(c.sym());
            def.setLabelEn(c.en());
            def.setLabelZh(c.zh());
            def.setCategory(c.cat());
            def.setSortOrder(c.sort());
            def.setStatus(CareStatus.ACTIVE);
            careInstructionDefRepository.insert(def);
        }
        log.info("[CatalogSeed] 护理标签种子数据写入完成 ({} 条)", data.size());
    }
}
