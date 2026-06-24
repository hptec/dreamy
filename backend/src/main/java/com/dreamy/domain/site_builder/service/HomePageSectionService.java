package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.HomePageSection;
import com.dreamy.domain.site_builder.repository.HomePageSectionRepository;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionDto;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 首页区块服务（FLOW-SB01）。
 * KD-4 保存即发布：写操作同事务内触发 cache.invalidateFamily + 事务外 publisher.publish。
 */
@Service
public class HomePageSectionService {

    private static final Logger log = LoggerFactory.getLogger(HomePageSectionService.class);
    private static final List<String> VALID_TYPES = List.of(
            "hero", "theme_cards", "product_rail", "editorial_feature", "newsletter", "custom");

    private final HomePageSectionRepository repository;
    private final ObjectMapper objectMapper;
    private final SiteBuilderCacheService cacheService;

    public HomePageSectionService(HomePageSectionRepository repository,
                                  ObjectMapper objectMapper,
                                  SiteBuilderCacheService cacheService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    @Transactional
    public HomePageSectionDto create(HomePageSectionUpsert upsert) {
        validate(upsert, null);
        HomePageSection entity = new HomePageSection();
        entity.setSectionType(upsert.getSectionType());
        entity.setEnabled(upsert.getEnabled());
        entity.setSortOrder(upsert.getSortOrder());
        entity.setLabel(upsert.getLabel());
        entity.setVersion(0);
        applyJsonFields(entity, upsert);
        repository.insert(entity);
        cacheService.invalidateHomeSectionFamily();
        return toDto(entity);
    }

    @Transactional
    public HomePageSectionDto update(Long id, HomePageSectionUpsert upsert) {
        HomePageSection entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        validate(upsert, entity.getSectionType());
        if (upsert.getVersion() == null || !upsert.getVersion().equals(entity.getVersion())) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        entity.setSectionType(upsert.getSectionType());
        if (upsert.getEnabled() != null) entity.setEnabled(upsert.getEnabled());
        if (upsert.getSortOrder() != null) entity.setSortOrder(upsert.getSortOrder());
        if (upsert.getLabel() != null) entity.setLabel(upsert.getLabel());
        applyJsonFields(entity, upsert);
        int rows = repository.updateByIdAndVersion(entity);
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        cacheService.invalidateHomeSectionFamily();
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        repository.deleteById(id);
        cacheService.invalidateHomeSectionFamily();
    }

    @Transactional
    public void batchSort(List<SortItem> items) {
        List<long[]> pairs = items.stream()
                .map(i -> new long[]{i.getId(), i.getSortOrder()})
                .collect(Collectors.toList());
        repository.batchUpdateSort(pairs);
        cacheService.invalidateHomeSectionFamily();
    }

    @Transactional
    public HomePageSectionDto toggle(Long id, Boolean enabled) {
        HomePageSection entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_NOT_FOUND));
        int rows = repository.updateEnabled(id, enabled, entity.getVersion());
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        entity.setEnabled(enabled);
        entity.setVersion(entity.getVersion() + 1);
        cacheService.invalidateHomeSectionFamily();
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
                    if (limit != null && (limit.asInt() < 1 || limit.asInt() > 12)) {
                        throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                    }
                    if ("recommend".equals(source != null ? source.asText() : null)) {
                        JsonNode productIds = dataJson.get("product_ids");
                        if (productIds == null || !productIds.isArray() || productIds.size() == 0) {
                            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                        }
                    }
                }
                break;
            case "theme_cards":
                if (dataJson != null && !dataJson.isNull()) {
                    JsonNode count = dataJson.get("count");
                    if (count != null && (count.asInt() < 1 || count.asInt() > 8)) {
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
