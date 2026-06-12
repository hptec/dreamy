package com.dreamy.catalog.config;

import com.dreamy.catalog.domain.attribute.entity.AttributeDef;
import com.dreamy.catalog.domain.attribute.repository.AttributeDefRepository;
import com.dreamy.catalog.domain.enums.AttributeType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * product 旧版型属性固定列 → product_attribute_value EAV 一次性迁移（@Order(30)：在
 * CatalogSeedInitializer(@Order(20)) 之后执行，保证 def 先于值——审查意见 ②）。
 * 逐行幂等（审查意见 ④）：INSERT IGNORE 依赖 uk_pav 去重，部分执行后可安全重跑；
 * 触发条件仅看 information_schema 旧列是否存在（huihao auto-DDL 不删列，全新库无旧列自动跳过）。
 * 同时：缺失 def 自动补建（options=已有 ∪ 现网 distinct 值）；category.attr_overrides 遗留
 * camelCase key 规范化为 snake_case。旧列手工清理见 scripts/sql/drop-legacy-attr-columns.sql。
 */
@Component
@Order(30)
public class CatalogAttrEavMigrationInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogAttrEavMigrationInitializer.class);

    /** 规范 key 注册表：legacy 列名 → (def key, label, type)；multiselect 列存 JSON 数组 */
    private record LegacyColumn(String column, String key, String label, AttributeType type) {
    }

    private static final List<LegacyColumn> LEGACY_COLUMNS = List.of(
            new LegacyColumn("silhouette", "silhouette", "Silhouette", AttributeType.SELECT),
            new LegacyColumn("neckline", "neckline", "Neckline", AttributeType.SELECT),
            new LegacyColumn("sleeve", "sleeve", "Sleeve", AttributeType.SELECT),
            new LegacyColumn("back_style", "back_style", "Back Style", AttributeType.SELECT),
            new LegacyColumn("waistline", "waistline", "Waistline", AttributeType.SELECT),
            new LegacyColumn("train", "train", "Train", AttributeType.SELECT),
            new LegacyColumn("length", "length", "Length", AttributeType.SELECT),
            new LegacyColumn("fabric", "fabric", "Fabric", AttributeType.SELECT),
            new LegacyColumn("support", "support", "Support", AttributeType.SELECT),
            new LegacyColumn("season", "season", "Season", AttributeType.SELECT),
            new LegacyColumn("embellishments", "embellishment", "Embellishments", AttributeType.MULTISELECT),
            new LegacyColumn("occasions", "occasion", "Occasions", AttributeType.MULTISELECT),
            new LegacyColumn("style_tags", "style_tag", "Style Tags", AttributeType.MULTISELECT));

    /** attr_overrides 遗留 camelCase key → 规范 snake_case */
    private static final Map<String, String> OVERRIDE_KEY_RENAMES = Map.of(
            "backStyle", "back_style",
            "styleTag", "style_tag",
            "careInstructions", "care_instructions",
            "customSize", "custom_size",
            "modelInfo", "model_info");

    private final JdbcTemplate jdbcTemplate;
    private final AttributeDefRepository defRepository;
    private final ObjectMapper objectMapper;

    public CatalogAttrEavMigrationInitializer(JdbcTemplate jdbcTemplate, AttributeDefRepository defRepository,
                                              ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.defRepository = defRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        Set<String> existingColumns = legacyColumnsPresent();
        normalizeOverrideKeys();
        if (existingColumns.isEmpty()) {
            return;
        }
        int migratedRows = 0;
        for (LegacyColumn legacy : LEGACY_COLUMNS) {
            if (!existingColumns.contains(legacy.column())) {
                continue;
            }
            Map<Long, List<String>> valuesByProduct = readLegacyValues(legacy);
            if (valuesByProduct.isEmpty()) {
                continue;
            }
            Set<String> distinctValues = new LinkedHashSet<>();
            valuesByProduct.values().forEach(distinctValues::addAll);
            Long attributeId = ensureDef(legacy, distinctValues);
            for (Map.Entry<Long, List<String>> entry : valuesByProduct.entrySet()) {
                for (String value : entry.getValue()) {
                    migratedRows += jdbcTemplate.update(
                            "INSERT IGNORE INTO product_attribute_value "
                                    + "(product_id, attribute_id, `value`, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, NOW(3), NOW(3))",
                            entry.getKey(), attributeId, value);
                }
            }
        }
        if (migratedRows > 0) {
            log.info("[CatalogAttrEavMigration] 旧属性列迁移完成：新增 {} 行 EAV（重复行已被 uk_pav 幂等忽略）",
                    migratedRows);
        }
    }

    /** information_schema 探测旧列（全新库实体不再建这些列 → 空集跳过） */
    private Set<String> legacyColumnsPresent() {
        List<String> columns = LEGACY_COLUMNS.stream().map(LegacyColumn::column).toList();
        String in = String.join(",", columns.stream().map(c -> "?").toList());
        List<String> present = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME IN (" + in + ")",
                String.class, columns.toArray());
        return new LinkedHashSet<>(present);
    }

    /** 旧列值读取：select 列单值；multiselect 列 JSON 数组逐元素拆行（≤255 截断丢弃保护索引） */
    private Map<Long, List<String>> readLegacyValues(LegacyColumn legacy) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, `" + legacy.column() + "` AS v FROM product "
                        + "WHERE `" + legacy.column() + "` IS NOT NULL AND `" + legacy.column() + "` <> ''",
                rs -> {
                    long productId = rs.getLong("id");
                    String raw = rs.getString("v");
                    List<String> values = legacy.type() == AttributeType.MULTISELECT
                            ? parseJsonArray(raw) : List.of(raw.trim());
                    List<String> cleaned = values.stream()
                            .filter(v -> v != null && !v.isBlank() && v.length() <= 255)
                            .map(String::trim)
                            .toList();
                    if (!cleaned.isEmpty()) {
                        result.put(productId, cleaned);
                    }
                });
        return result;
    }

    private List<String> parseJsonArray(String raw) {
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return parsed == null ? List.of() : parsed;
        } catch (Exception ex) {
            log.warn("[CatalogAttrEavMigration] multiselect JSON 解析失败，跳过该行：{}", raw);
            return List.of();
        }
    }

    /** def 幂等补建（按 key），并将现网 distinct 值并入 options（防后续编辑 invalid_option） */
    private Long ensureDef(LegacyColumn legacy, Set<String> distinctValues) {
        AttributeDef def = defRepository.findByKey(legacy.key());
        if (def == null) {
            def = new AttributeDef();
            def.setKey(legacy.key());
            def.setLabel(legacy.label());
            def.setType(legacy.type());
            def.setOptions(legacy.type().optionsAllowed() ? new ArrayList<>(distinctValues) : null);
            defRepository.insert(def);
            log.info("[CatalogAttrEavMigration] 补建属性定义 key={} options={}", legacy.key(), def.getOptions());
            return def.getId();
        }
        if (def.getType() != null && def.getType().optionsAllowed()) {
            LinkedHashSet<String> merged = new LinkedHashSet<>(
                    def.getOptions() == null ? List.of() : def.getOptions());
            if (merged.addAll(distinctValues)) {
                def.setOptions(new ArrayList<>(merged));
                defRepository.update(def);
                log.info("[CatalogAttrEavMigration] 属性 key={} options 并入现网值 → {}", legacy.key(), merged);
            }
        }
        return def.getId();
    }

    /** category.attr_overrides 遗留 camelCase key 规范化（幂等：无命中不更新） */
    private void normalizeOverrideKeys() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, attr_overrides FROM category WHERE attr_overrides IS NOT NULL");
        for (Map<String, Object> row : rows) {
            Object raw = row.get("attr_overrides");
            if (raw == null) {
                continue;
            }
            try {
                Map<String, String> overrides = objectMapper.readValue(raw.toString(), new TypeReference<>() {
                });
                Map<String, String> normalized = new LinkedHashMap<>();
                boolean changed = false;
                for (Map.Entry<String, String> entry : overrides.entrySet()) {
                    String key = OVERRIDE_KEY_RENAMES.getOrDefault(entry.getKey(), entry.getKey());
                    changed |= !key.equals(entry.getKey());
                    normalized.put(key, entry.getValue());
                }
                if (changed) {
                    jdbcTemplate.update("UPDATE category SET attr_overrides = ? WHERE id = ?",
                            objectMapper.writeValueAsString(normalized), row.get("id"));
                    log.info("[CatalogAttrEavMigration] category {} attr_overrides key 规范化完成", row.get("id"));
                }
            } catch (Exception ex) {
                log.warn("[CatalogAttrEavMigration] attr_overrides 解析失败 category={}，跳过", row.get("id"));
            }
        }
    }
}
