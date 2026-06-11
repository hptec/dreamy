package com.dreamy.catalog.domain.tag.service;

import com.dreamy.catalog.domain.enums.TagStatus;
import com.dreamy.catalog.domain.product.repository.ProductTagRepository;
import com.dreamy.catalog.domain.tag.entity.Tag;
import com.dreamy.catalog.domain.tag.entity.TagDimension;
import com.dreamy.catalog.domain.tag.entity.TagDimensionTranslation;
import com.dreamy.catalog.domain.tag.entity.TagTranslation;
import com.dreamy.catalog.domain.tag.repository.TagDimensionRepository;
import com.dreamy.catalog.domain.tag.repository.TagRepository;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDimensionDto;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDimensionUpsert;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagDto;
import com.dreamy.catalog.dto.AdminCatalogDtos.TagUpsert;
import com.dreamy.catalog.dto.TranslationDtos.TagDimensionTranslationDto;
import com.dreamy.catalog.dto.TranslationDtos.TagTranslationDto;
import com.dreamy.catalog.error.CatalogErrorCode;
import com.dreamy.catalog.error.CatalogException;
import com.dreamy.catalog.event.ContentInvalidatedPublisher;
import com.dreamy.catalog.infra.AfterCommitRunner;
import com.dreamy.catalog.infra.CatalogAuditRecorder;
import com.dreamy.catalog.infra.CatalogCacheService;
import com.dreamy.catalog.infra.CatalogCacheService.Family;
import com.dreamy.catalog.support.FieldErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台标签维度/标签服务（E-CAT-27~34；TX-CAT-015~020；TASK-035/036 lifecycle guard）。
 * L2 TRACE: V-CAT-059~068 / CV-CAT-006 / CACHE-CAT-006 失效链 / MAP-CAT-010。
 */
@Service
public class TagAdminService {

    private static final Set<String> TRANSLATION_LOCALES = Set.of("es", "fr");

    private final TagDimensionRepository dimensionRepository;
    private final TagRepository tagRepository;
    private final ProductTagRepository productTagRepository;
    private final CatalogCacheService cache;
    private final CatalogAuditRecorder audit;
    private final AfterCommitRunner afterCommit;
    private final ContentInvalidatedPublisher invalidatedPublisher;
    private final ObjectMapper objectMapper;

    public TagAdminService(TagDimensionRepository dimensionRepository, TagRepository tagRepository,
                           ProductTagRepository productTagRepository, CatalogCacheService cache,
                           CatalogAuditRecorder audit, AfterCommitRunner afterCommit,
                           ContentInvalidatedPublisher invalidatedPublisher, ObjectMapper objectMapper) {
        this.dimensionRepository = dimensionRepository;
        this.tagRepository = tagRepository;
        this.productTagRepository = productTagRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.invalidatedPublisher = invalidatedPublisher;
        this.objectMapper = objectMapper;
    }

    // ==================== 标签维度 E-CAT-27~30 ====================

    /** E-CAT-27：维度列表（tag_count 派生 + 三语 translations） */
    public List<TagDimensionDto> listDimensions() {
        List<TagDimension> dims = dimensionRepository.listAll();
        Map<Long, Long> tagCounts = dimensionRepository.countTagsGroupByDimension();
        Map<Long, List<TagDimensionTranslationDto>> translations = dimensionTranslations(
                dims.stream().map(TagDimension::getId).toList());
        return dims.stream().map(d -> new TagDimensionDto(d.getId(), d.getName(), d.getDescription(),
                tagCounts.getOrDefault(d.getId(), 0L).intValue(),
                translations.getOrDefault(d.getId(), List.of()))).toList();
    }

    /** E-CAT-28：新增维度（TX-CAT-015） */
    @Transactional
    public TagDimensionDto createDimension(TagDimensionUpsert req) {
        validateDimension(req);
        TagDimension dim = new TagDimension();
        dim.setName(req.name().trim());
        dim.setDescription(req.description());
        dimensionRepository.insert(dim);
        dimensionRepository.replaceTranslations(dim.getId(), toDimensionTranslationRows(req.translations()));
        audit.record("创建标签维度", dim.getName(), null);
        return new TagDimensionDto(dim.getId(), dim.getName(), dim.getDescription(), 0,
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-29：编辑维度（TX-CAT-016） */
    @Transactional
    public TagDimensionDto updateDimension(Long id, TagDimensionUpsert req) {
        TagDimension existing = dimensionRepository.findById(id);
        if (existing == null) {
            // V-CAT-061
            throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
        }
        validateDimension(req);
        existing.setName(req.name().trim());
        existing.setDescription(req.description());
        dimensionRepository.update(existing);
        dimensionRepository.replaceTranslations(id, toDimensionTranslationRows(req.translations()));
        audit.record("编辑标签维度", existing.getName(), null);
        // STEP-CAT-02 提交后失效 catalog:tags:* → MQ tag_changed
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.TAGS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_TAG_CHANGED);
        });
        int tagCount = (int) tagRepository.countByDimensionId(id);
        return new TagDimensionDto(id, existing.getName(), existing.getDescription(), tagCount,
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-30：删除维度（TX-CAT-017；guard 409506——真实端收紧为先清空标签防误删营销数据） */
    @Transactional
    public void deleteDimension(Long id) {
        TagDimension existing = dimensionRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
        }
        long tagCount = tagRepository.countByDimensionId(id);
        if (tagCount > 0) {
            throw new CatalogException(CatalogErrorCode.TAG_DIMENSION_IN_USE, Map.of("tag_count", tagCount));
        }
        dimensionRepository.deleteById(id);
        audit.record("删除标签维度", existing.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.TAGS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_TAG_CHANGED);
        });
    }

    // ==================== 标签 E-CAT-31~34 ====================

    /** E-CAT-31：标签列表（product_count 全量口径 + translations——V-CAT-062） */
    public List<TagDto> listTags(Long dimensionId) {
        List<Tag> tags = tagRepository.listByDimensionId(dimensionId);
        Map<Long, Integer> counts = productTagRepository.countByTags(false);
        Map<Long, List<TagTranslationDto>> translations = tagTranslations(tags.stream().map(Tag::getId).toList());
        return tags.stream().map(t -> toTagDto(t, counts.getOrDefault(t.getId(), 0),
                translations.getOrDefault(t.getId(), List.of()))).toList();
    }

    /** E-CAT-32：新增标签（TX-CAT-018；tag_lifecycle →enabled） */
    @Transactional
    public TagDto createTag(TagUpsert req) {
        validateTag(req);
        Tag tag = new Tag();
        tag.setDimensionId(req.dimensionId());
        tag.setName(req.name().trim());
        tag.setCover(req.cover());
        tag.setStatus(TagStatus.of(req.status()));
        tagRepository.insert(tag);
        tagRepository.replaceTranslations(tag.getId(), toTagTranslationRows(req.translations()));
        audit.record("创建标签", tag.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.TAGS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_TAG_CHANGED);
        });
        return toTagDto(tag, 0, req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-33：编辑标签（TX-CAT-019；Toggle enabled 映射 status；dimension_id 可改=移动维度） */
    @Transactional
    public TagDto updateTag(Long id, TagUpsert req) {
        Tag existing = tagRepository.findById(id);
        if (existing == null) {
            // V-CAT-067
            throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
        }
        validateTag(req);
        TagStatus oldStatus = existing.getStatus();
        existing.setDimensionId(req.dimensionId());
        existing.setName(req.name().trim());
        existing.setCover(req.cover());
        existing.setStatus(TagStatus.of(req.status()));
        tagRepository.update(existing);
        tagRepository.replaceTranslations(id, toTagTranslationRows(req.translations()));
        // 审计 changes 含 status 流转（BE-DIM-7）
        audit.record("编辑标签", existing.getName(), statusChangesJson(oldStatus, existing.getStatus()));
        // STEP-CAT-02 提交后失效 tags/reco（shop_by_color）/products（tag_id 筛选）→ MQ
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.TAGS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_TAG_CHANGED);
        });
        Map<Long, Integer> counts = productTagRepository.countByTags(false);
        return toTagDto(existing, counts.getOrDefault(id, 0),
                req.translations() == null ? List.of() : req.translations());
    }

    /** E-CAT-34：删除标签（TX-CAT-020；无前置 guard；product_tag 级联摘除——RM-CAT-144） */
    @Transactional
    public void deleteTag(Long id) {
        // V-CAT-068：不存在（含非法 id）→ 404505
        Tag existing = tagRepository.findById(id);
        if (existing == null) {
            throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
        }
        tagRepository.deleteById(id);
        tagRepository.deleteTranslationsByTagId(id);
        productTagRepository.deleteByTagId(id);
        audit.record("删除标签", existing.getName(), null);
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.TAGS);
            cache.invalidateFamily(Family.RECO);
            cache.invalidateFamily(Family.PRODUCTS);
            invalidatedPublisher.publish(ContentInvalidatedPublisher.TYPE_TAG_CHANGED);
        });
    }

    // ==================== 校验/装配 ====================

    /** V-CAT-059/060 */
    private void validateDimension(TagDimensionUpsert req) {
        FieldErrors errors = new FieldErrors();
        if (req.name() == null || req.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (req.name().trim().length() > 64) {
            errors.reject("name", "too_long");
        }
        if (req.description() != null && req.description().length() > 255) {
            errors.reject("description", "too_long");
        }
        if (req.translations() != null) {
            Set<String> seen = new HashSet<>();
            for (TagDimensionTranslationDto t : req.translations()) {
                if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                    errors.reject("translations", "invalid_locale");
                    break;
                }
                if (t.name() != null && t.name().length() > 64) {
                    errors.reject("translations", "too_long");
                    break;
                }
            }
        }
        errors.throwIfAny();
    }

    /** V-CAT-063~066 */
    private void validateTag(TagUpsert req) {
        FieldErrors errors = new FieldErrors();
        // V-CAT-063 dimension_id 必填且维度存在（不存在 → 404505）
        if (req.dimensionId() == null) {
            errors.reject("dimension_id", "required");
            errors.throwIfAny();
        }
        if (dimensionRepository.findById(req.dimensionId()) == null) {
            throw new CatalogException(CatalogErrorCode.TAG_NOT_FOUND);
        }
        // V-CAT-064 name 必填 trim 非空 ≤64（js_guard 后端兜底）
        if (req.name() == null || req.name().trim().isEmpty()) {
            errors.reject("name", "required");
        } else if (req.name().trim().length() > 64) {
            errors.reject("name", "too_long");
        }
        // V-CAT-065 status 必填枚举
        if (TagStatus.of(req.status()) == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-CAT-066 cover ≤512 可空
        if (req.cover() != null && req.cover().length() > 512) {
            errors.reject("cover", "too_long");
        }
        if (req.translations() != null) {
            Set<String> seen = new HashSet<>();
            for (TagTranslationDto t : req.translations()) {
                if (t.locale() == null || !TRANSLATION_LOCALES.contains(t.locale()) || !seen.add(t.locale())) {
                    errors.reject("translations", "invalid_locale");
                    break;
                }
                if (t.label() != null && t.label().length() > 64) {
                    errors.reject("translations", "too_long");
                    break;
                }
            }
        }
        errors.throwIfAny();
    }

    private String statusChangesJson(TagStatus from, TagStatus to) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "before", Map.of("status", from == null ? null : from.getKey()),
                    "after", Map.of("status", to == null ? null : to.getKey())));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<TagDimensionTranslation> toDimensionTranslationRows(List<TagDimensionTranslationDto> dtos) {
        List<TagDimensionTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (TagDimensionTranslationDto dto : dtos) {
                TagDimensionTranslation row = new TagDimensionTranslation();
                row.setLocale(dto.locale());
                row.setName(dto.name());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<TagTranslation> toTagTranslationRows(List<TagTranslationDto> dtos) {
        List<TagTranslation> rows = new ArrayList<>();
        if (dtos != null) {
            for (TagTranslationDto dto : dtos) {
                TagTranslation row = new TagTranslation();
                row.setLocale(dto.locale());
                row.setLabel(dto.label());
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<Long, List<TagDimensionTranslationDto>> dimensionTranslations(List<Long> ids) {
        Map<Long, List<TagDimensionTranslationDto>> result = new HashMap<>();
        for (TagDimensionTranslation t : dimensionRepository.listTranslationsByDimensionIds(ids)) {
            result.computeIfAbsent(t.getTagDimensionId(), k -> new ArrayList<>())
                    .add(new TagDimensionTranslationDto(t.getLocale(), t.getName()));
        }
        return result;
    }

    private Map<Long, List<TagTranslationDto>> tagTranslations(List<Long> tagIds) {
        Map<Long, List<TagTranslationDto>> result = new HashMap<>();
        for (TagTranslation t : tagRepository.listTranslationsByTagIds(tagIds)) {
            result.computeIfAbsent(t.getTagId(), k -> new ArrayList<>())
                    .add(new TagTranslationDto(t.getLocale(), t.getLabel()));
        }
        return result;
    }

    private TagDto toTagDto(Tag tag, int productCount, List<TagTranslationDto> translations) {
        return new TagDto(tag.getId(), tag.getDimensionId(), tag.getName(), tag.getCover(),
                tag.getStatus() == null ? null : tag.getStatus().getKey(), productCount, translations);
    }
}
