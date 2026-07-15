package com.dreamy.domain.site_builder.service;

import com.dreamy.aspect.HomePageSectionWrite;
import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionDto;
import com.dreamy.dto.SiteBuilderDtos.HomePageSaveItem;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionUpsert;
import com.dreamy.dto.SiteBuilderDtos.SortItem;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页区块服务（FLOW-SB01）。消费端实时读取，保存后直接对外生效。
 */
@Service
public class HomePageSectionService {

    private static final Logger log = LoggerFactory.getLogger(HomePageSectionService.class);
    private static final List<String> VALID_TYPES = List.of(
            "hero", "theme_cards", "product_rail", "editorial_feature", "newsletter", "custom");

    private final HomePageSectionRepository repository;
    private final ObjectMapper objectMapper;
    private final CacheInvalidationTaskService cacheTasks;
    public HomePageSectionService(HomePageSectionRepository repository,
                                  ObjectMapper objectMapper, CacheInvalidationTaskService cacheTasks) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheTasks = cacheTasks;
    }

    @Transactional
    @HomePageSectionWrite
    public HomePageSectionDto create(HomePageSectionUpsert upsert) {
        validate(upsert, null);
        validateHeroSingleton(upsert.getSectionType(), null);
        HomePageSection entity = new HomePageSection();
        entity.setSectionType(upsert.getSectionType());
        entity.setEnabled(upsert.getEnabled());
        entity.setSortOrder(upsert.getSortOrder());
        entity.setLabel(upsert.getLabel());
        entity.setVersion(0);
        applyJsonFields(entity, upsert);
        repository.insert(entity);
        enqueue("site_home.section.create", entity.getId(), entity.getLabel(), sectionDetails(entity));
        return toDto(entity);
    }

    @Transactional
    @HomePageSectionWrite
    public HomePageSectionDto update(Long id, HomePageSectionUpsert upsert) {
        HomePageSection entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        validate(upsert, entity.getSectionType());
        String targetType = upsert.getSectionType() == null ? entity.getSectionType() : upsert.getSectionType();
        validateHeroSingleton(targetType, id);
        if (upsert.getVersion() == null || !upsert.getVersion().equals(entity.getVersion())) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        entity.setSectionType(targetType);
        if (upsert.getEnabled() != null) entity.setEnabled(upsert.getEnabled());
        if (upsert.getSortOrder() != null) entity.setSortOrder(upsert.getSortOrder());
        if (upsert.getLabel() != null) entity.setLabel(upsert.getLabel());
        applyJsonFields(entity, upsert);
        int rows = repository.updateByIdAndVersion(entity);
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        enqueue("site_home.section.update", id, entity.getLabel(), sectionDetails(entity));
        return toDto(entity);
    }

    @Transactional
    @HomePageSectionWrite
    public List<HomePageSectionDto> saveAll(List<HomePageSaveItem> items) {
        if (items == null || items.isEmpty()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID,
                    Map.of("reason", "homepage must contain at least one section"));
        }
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID,
                    Map.of("reason", "homepage section item must not be null"));
        }
        if (items.stream().map(HomePageSaveItem::getId).distinct().count() != items.size()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID,
                    Map.of("reason", "duplicate section id"));
        }
        if (items.stream().filter(item -> "hero".equals(item.getSectionType())).count() > 1) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID,
                    Map.of("reason", "homepage can contain only one hero section"));
        }
        Map<Long, HomePageSection> existingById = repository.findAllOrderById().stream()
                .collect(Collectors.toMap(HomePageSection::getId, section -> section));
        for (HomePageSaveItem item : items) {
            if (item.getId() == null) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND);
            }
            HomePageSection entity = existingById.get(item.getId());
            if (entity == null) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND);
            }
            validate(item, entity.getSectionType());
            if (item.getVersion() == null || !item.getVersion().equals(entity.getVersion())) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
            }
        }
        Map<Long, HomePageSaveItem> requestedById = items.stream()
                .collect(Collectors.toMap(HomePageSaveItem::getId, item -> item));
        long finalHeroCount = existingById.values().stream()
                .filter(section -> {
                    HomePageSaveItem requested = requestedById.get(section.getId());
                    String finalType = requested == null || requested.getSectionType() == null
                            ? section.getSectionType()
                            : requested.getSectionType();
                    return "hero".equals(finalType);
                })
                .count();
        if (finalHeroCount > 1) {
            throw heroSingletonConflict();
        }

        // Stable ordering keeps multi-row updates deterministic and remains compatible during rolling upgrades.
        List<HomePageSaveItem> writeOrder = items.stream()
                .sorted(java.util.Comparator
                        .comparing((HomePageSaveItem item) -> "hero".equals(item.getSectionType()))
                        .thenComparing(HomePageSaveItem::getId))
                .toList();
        for (HomePageSaveItem item : writeOrder) {
            HomePageSection entity = existingById.get(item.getId());
            if (item.getSectionType() != null) entity.setSectionType(item.getSectionType());
            entity.setEnabled(item.getEnabled());
            entity.setSortOrder(item.getSortOrder());
            if (item.getLabel() != null) entity.setLabel(item.getLabel());
            applyJsonFields(entity, item);
            int rows = repository.updateByIdAndVersion(entity);
            if (rows == 0) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
            }
        }
        enqueue("site_home.save_all", "homepage", "首页装修",
                Map.of("section_count", items.size()));
        return list(false);
    }

    @Transactional
    @HomePageSectionWrite
    public void delete(Long id) {
        HomePageSection existing = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        repository.deleteById(id);
        enqueue("site_home.section.delete", id, existing.getLabel(), sectionDetails(existing));
    }

    @Transactional
    @HomePageSectionWrite
    public void batchSort(List<SortItem> items) {
        if (items == null || items.isEmpty()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "items"));
        }
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "items"));
        }
        if (items.stream().map(SortItem::getId).filter(java.util.Objects::nonNull).distinct().count()
                != items.size()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "items", "reason", "duplicate or missing section id"));
        }
        for (SortItem item : items) {
            if (item.getSortOrder() == null || item.getSortOrder() < 0) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                        Map.of("field", "sort_order"));
            }
        }

        Map<Long, HomePageSection> existingById = repository.findAllOrderById().stream()
                .collect(Collectors.toMap(HomePageSection::getId, section -> section));
        if (items.stream().map(SortItem::getId).anyMatch(id -> !existingById.containsKey(id))) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND);
        }
        List<long[]> pairs = items.stream()
                .map(i -> new long[]{i.getId(), i.getSortOrder()})
                .collect(Collectors.toList());
        if (repository.batchUpdateSort(pairs) != items.size()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        enqueue("site_home.sort", "homepage", "首页区块排序", Map.of("section_count", items.size()));
    }

    @Transactional
    @HomePageSectionWrite
    public HomePageSectionDto toggle(Long id, Boolean enabled) {
        HomePageSection entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        int rows = repository.updateEnabled(id, enabled, entity.getVersion());
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        entity.setEnabled(enabled);
        entity.setVersion(entity.getVersion() + 1);
        enqueue("site_home.section.toggle", id, entity.getLabel(), sectionDetails(entity));
        return toDto(entity);
    }

    public List<HomePageSectionDto> list(Boolean enabledOnly) {
        List<HomePageSection> entities = Boolean.TRUE.equals(enabledOnly)
                ? repository.findEnabledOrderBySort()
                : repository.findAllOrderBySort();
        return entities.stream().map(this::toDto).collect(Collectors.toList());
    }

    public HomePageSectionDto get(Long id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND)));
    }

    private void enqueue(String triggerPoint, Object resourceId, String label, Map<String, Object> details) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "site_home", resourceId, label, CacheInvalidationPlans.SITE_HOME_PLAN,
                null, details, null);
    }

    private Map<String, Object> sectionDetails(HomePageSection section) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (section.getSectionType() != null) details.put("section_type", section.getSectionType());
        if (section.getEnabled() != null) details.put("enabled", section.getEnabled());
        return details;
    }

    private void validate(HomePageSectionUpsert upsert, String currentType) {
        String type = upsert.getSectionType() != null ? upsert.getSectionType() : currentType;
        if (type == null || !VALID_TYPES.contains(type)) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_TYPE_INVALID);
        }
        if (upsert.getEnabled() == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "enabled"));
        }
        if (upsert.getSortOrder() != null && upsert.getSortOrder() < 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "sort_order"));
        }
        validateJsGuard(type, upsert.getDataJson(), upsert.getI18nJson());
        validateI18nJson(upsert.getI18nJson());
    }

    private void validateHeroSingleton(String sectionType, Long excludeId) {
        if ("hero".equals(sectionType) && repository.countByTypeExcludingId("hero", excludeId) > 0) {
            throw heroSingletonConflict();
        }
    }

    private SiteBuilderException heroSingletonConflict() {
        return SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID,
                Map.of("reason", "homepage can contain only one hero section"));
    }

    private void validateJsGuard(String type, JsonNode dataJson, JsonNode i18nJson) {
        switch (type) {
            case "hero":
                if (dataJson != null && !dataJson.isNull() && dataJson.size() > 0) {
                    throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                            Map.of("reason", "hero data_json must be empty (derived from Banner)"));
                }
                break;
            case "newsletter":
                if (i18nJson == null || i18nJson.isNull() || i18nJson.size() == 0) {
                    throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                            Map.of("reason", "newsletter i18n_json must be non-empty"));
                }
                break;
            case "product_rail":
                if (dataJson != null && !dataJson.isNull()) {
                    JsonNode source = dataJson.get("source");
                    JsonNode limit = dataJson.get("limit");
                    String sourceValue = source == null ? "new_arrival" : source.asText();
                    if (!List.of("new_arrival", "best_seller", "recommend", "category").contains(sourceValue)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                    if (limit != null && (limit.asInt() < 1 || limit.asInt() > 12)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                    if ("recommend".equals(sourceValue)) {
                        JsonNode productIds = dataJson.get("product_ids");
                        if (productIds == null || !productIds.isArray() || productIds.size() == 0) {
                            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                        }
                    }
                    if ("category".equals(sourceValue)) {
                        JsonNode categoryId = dataJson.get("category_id");
                        if (categoryId == null || !categoryId.canConvertToLong() || categoryId.asLong() <= 0) {
                            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                        }
                    }
                    JsonNode sort = dataJson.get("sort");
                    if (sort != null && !List.of("newest", "price_asc", "price_desc", "recommended", "new", "best")
                            .contains(sort.asText())) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                }
                break;
            case "theme_cards":
                if (dataJson != null && !dataJson.isNull()) {
                    JsonNode limit = dataJson.has("limit") ? dataJson.get("limit") : dataJson.get("count");
                    if (limit != null && (limit.asInt() < 1 || limit.asInt() > 8)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                    String mode = dataJson.has("mode") ? dataJson.get("mode").asText() : "auto";
                    if (!List.of("auto", "manual").contains(mode)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                    if ("manual".equals(mode)) {
                        JsonNode collectionIds = dataJson.get("collection_ids");
                        if (collectionIds == null || !collectionIds.isArray() || collectionIds.size() == 0) {
                            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                        }
                    }
                }
                break;
            case "editorial_feature":
                if (dataJson != null && !dataJson.isNull()) {
                    JsonNode limit = dataJson.get("limit");
                    if (limit != null && (limit.asInt() < 1 || limit.asInt() > 6)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void validateI18nJson(JsonNode i18nJson) {
        if (i18nJson == null || i18nJson.isNull()) return;
        if (!i18nJson.isObject()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
        }
        for (String localeKey : (Iterable<String>) i18nJson::fieldNames) {
            if (!List.of("en", "es", "fr").contains(localeKey)) {
                throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID,
                        Map.of("invalid_locale", localeKey));
            }
        }
    }

    private void applyJsonFields(HomePageSection entity, HomePageSectionUpsert upsert) {
        try {
            if ("hero".equals(entity.getSectionType())) {
                entity.setDataJson(null);
                entity.setI18nJson(null);
                return;
            }
            if (upsert.getDataJson() != null) {
                entity.setDataJson(objectMapper.writeValueAsString(upsert.getDataJson()));
            }
            if (upsert.getI18nJson() != null) {
                entity.setI18nJson(objectMapper.writeValueAsString(upsert.getI18nJson()));
                JsonNode enNode = upsert.getI18nJson().get("en");
                if (enNode != null && enNode.has("label")) {
                    entity.setLabel(enNode.get("label").asText());
                }
            }
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
        }
    }

    private HomePageSectionDto toDto(HomePageSection entity) {
        HomePageSectionDto dto = new HomePageSectionDto();
        dto.setId(entity.getId());
        dto.setSectionType(entity.getSectionType());
        dto.setEnabled(entity.getEnabled());
        dto.setSortOrder(entity.getSortOrder());
        dto.setDataJson(entity.getDataJson());
        dto.setI18nJson(entity.getI18nJson());
        dto.setLabel(entity.getLabel());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
