package com.dreamy.domain.site_builder.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementDto;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementUpsert;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnnouncementService {

    private final AnnouncementRepository repository;
    private final ObjectMapper objectMapper;
    private final SiteBuilderCacheService cacheService;

    public AnnouncementService(AnnouncementRepository repository, ObjectMapper objectMapper,
                               SiteBuilderCacheService cacheService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public IPage<Announcement> list(int page, int size, Boolean enabledOnly) {
        return repository.findAllOrderByPriorityId(page, size, enabledOnly);
    }

    public AnnouncementDto get(Long id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_NOT_FOUND)));
    }

    @Transactional
    public AnnouncementDto create(AnnouncementUpsert upsert) {
        validate(upsert);
        checkTimeWindowConflict(upsert.getPriority(), upsert.getStartAt(), upsert.getEndAt(), null);
        Announcement entity = new Announcement();
        applyUpsert(entity, upsert);
        entity.setVersion(0);
        repository.insert(entity);
        cacheService.invalidateAnnouncementFamily();
        return toDto(entity);
    }

    @Transactional
    public AnnouncementDto update(Long id, AnnouncementUpsert upsert) {
        Announcement entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_NOT_FOUND));
        validate(upsert);
        checkTimeWindowConflict(upsert.getPriority(), upsert.getStartAt(), upsert.getEndAt(), id);
        if (upsert.getVersion() == null || !upsert.getVersion().equals(entity.getVersion())) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        applyUpsert(entity, upsert);
        int rows = repository.updateByIdAndVersion(entity);
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        cacheService.invalidateAnnouncementFamily();
        return toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_NOT_FOUND));
        repository.deleteById(id);
        cacheService.invalidateAnnouncementFamily();
    }

    @Transactional
    public AnnouncementDto toggle(Long id, Boolean enabled) {
        Announcement entity = repository.findById(id)
                .orElseThrow(() -> SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_NOT_FOUND));
        int rows = repository.updateEnabled(id, enabled, entity.getVersion());
        if (rows == 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.HOME_SECTION_SORT_CONFLICT);
        }
        entity.setEnabled(enabled);
        entity.setVersion(entity.getVersion() + 1);
        cacheService.invalidateAnnouncementFamily();
        return toDto(entity);
    }

    private void validate(AnnouncementUpsert upsert) {
        if (upsert.getEnabled() == null) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "enabled"));
        }
        if (upsert.getPriority() == null || upsert.getPriority() < 0) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.SECTION_TYPE_DATA_MISMATCH,
                    Map.of("field", "priority"));
        }
        if (upsert.getStartAt() != null && upsert.getEndAt() != null
                && !upsert.getStartAt().isBefore(upsert.getEndAt())) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_TIME_WINDOW_INVALID);
        }
        if (upsert.getContentI18nJson() == null || !upsert.getContentI18nJson().isObject()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
        }
    }

    private void checkTimeWindowConflict(Integer priority, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (priority == null) return;
        LocalDateTime startSafe = start != null ? start : LocalDateTime.MIN;
        LocalDateTime endSafe = end != null ? end : LocalDateTime.MAX;
        List<Announcement> overlaps = repository.findOverlapByPriorityAndTime(priority, startSafe, endSafe);
        if (excludeId != null) {
            overlaps = overlaps.stream().filter(a -> !a.getId().equals(excludeId)).collect(Collectors.toList());
        }
        if (!overlaps.isEmpty()) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.ANNOUNCEMENT_TIME_WINDOW_CONFLICT,
                    Map.of("conflict_id", overlaps.get(0).getId()));
        }
    }

    private void applyUpsert(Announcement entity, AnnouncementUpsert upsert) {
        entity.setEnabled(upsert.getEnabled());
        entity.setPriority(upsert.getPriority());
        entity.setStartAt(upsert.getStartAt());
        entity.setEndAt(upsert.getEndAt());
        try {
            if (upsert.getContentI18nJson() != null) {
                entity.setContentI18nJson(objectMapper.writeValueAsString(upsert.getContentI18nJson()));
                JsonNode enNode = upsert.getContentI18nJson().get("en");
                if (enNode != null && enNode.has("content")) {
                    entity.setContent(enNode.get("content").asText());
                }
            }
            if (upsert.getI18nJson() != null) {
                entity.setI18nJson(objectMapper.writeValueAsString(upsert.getI18nJson()));
            }
        } catch (Exception e) {
            throw SiteBuilderException.of(SiteBuilderErrorCode.I18N_JSON_INVALID);
        }
    }

    private AnnouncementDto toDto(Announcement entity) {
        AnnouncementDto dto = new AnnouncementDto();
        dto.setId(entity.getId());
        dto.setEnabled(entity.getEnabled());
        dto.setPriority(entity.getPriority());
        dto.setStartAt(entity.getStartAt());
        dto.setEndAt(entity.getEndAt());
        dto.setContent(entity.getContent());
        dto.setContentI18nJson(entity.getContentI18nJson());
        dto.setI18nJson(entity.getI18nJson());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
