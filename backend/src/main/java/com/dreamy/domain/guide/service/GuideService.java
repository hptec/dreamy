package com.dreamy.domain.guide.service;

import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.entity.GuideTranslation;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.dto.AdminMarketingDtos.GuideDto;
import com.dreamy.dto.AdminMarketingDtos.GuideUpsert;
import com.dreamy.dto.MarketingTranslationDtos.GuideTranslationDto;
import com.dreamy.dto.StoreMarketingDtos.StoreGuide;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import com.dreamy.support.Translations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指南服务（store E-MKT-08 + admin E-MKT-42~46；TX-MKT-023~026；TASK-045 guide_publish）。
 * RBAC `/content/lookbook`（契约口径：与 Lookbook 同页同权限）。
 * L2 TRACE: V-MKT-074~082 / RM-MKT-080~089 / CACHE-MKT-008 / MAP-MKT-009。
 */
@Service
public class GuideService {


    private final GuideRepository guideRepository;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final MarketingAfterCommitRunner afterCommit;
    private final MarketingContentInvalidatedPublisher publisher;

    public GuideService(GuideRepository guideRepository, MarketingCacheService cache,
                        MarketingAuditRecorder audit, MarketingAfterCommitRunner afterCommit,
                        MarketingContentInvalidatedPublisher publisher) {
        this.guideRepository = guideRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.publisher = publisher;
    }

    /** E-MKT-08：消费端 published 列表（ORDER BY phase, id + locale 回退 + JetCache 300s） */
    @SuppressWarnings("unchecked")
    public List<StoreGuide> listStore(String locale) {
        MarketingCacheService.Lookup lookup = cache.lookup(Family.GUIDES, locale);
        Object cached = lookup.value();
        if (cached instanceof List<?> hit) {
            return (List<StoreGuide>) hit;
        }
        List<Guide> guides = guideRepository.listStorePublished();
        Map<Long, GuideTranslation> translations = storeTranslationsFor(
                guides.stream().map(Guide::getId).toList(), locale);
        List<StoreGuide> items = new ArrayList<>(guides.size());
        for (Guide g : guides) {
            GuideTranslation t = translations.get(g.getId());
            items.add(new StoreGuide(g.getId(), g.getPhase(), g.getTimeframe(),
                    Translations.coalesce(t == null ? null : t.getTitle(), g.getTitle()),
                    Translations.coalesce(t == null ? null : t.getBody(), g.getBody()),
                    g.getTasksCount()));
        }
        cache.put(lookup, items);
        return items;
    }

    /** E-MKT-42：后台列表（status 筛选 + translations 原样） */
    public List<GuideDto> listAdmin(Integer status) {
        // V-MKT-074 status ∈ {all, draft, published} 缺省 all
        Integer statusFilter = status;
        if (statusFilter != null && PublishStatus.of(statusFilter) == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        PublishStatus statusEnum = statusFilter == null ? null : PublishStatus.of(statusFilter);
        List<Guide> guides = guideRepository.listAdmin(statusEnum);
        Map<Long, List<GuideTranslationDto>> translations = translationsByGuide(
                guides.stream().map(Guide::getId).toList());
        return guides.stream().map(g -> toDto(g, translations.getOrDefault(g.getId(), List.of()))).toList();
    }

    /** E-MKT-43：创建（TX-MKT-023） */
    @Transactional
    public GuideDto create(GuideUpsert req) {
        Normalized n = validateUpsert(req);
        // STEP-MKT-01 INSERT guide + translation 批插
        Guide guide = new Guide();
        applyUpsert(guide, n);
        guideRepository.insert(guide);
        guideRepository.replaceTranslations(guide.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-02 审计
        audit.record("创建指南", n.title(), null);
        // STEP-MKT-03 提交后（published）失效 + MQ → revalidate /wedding-guides ×3 + purge
        if (n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit();
        }
        return toDto(guideRepository.findById(guide.getId()), nonNull(req.translations()));
    }

    /** E-MKT-44：编辑（TX-MKT-024） */
    @Transactional
    public GuideDto update(Long id, GuideUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        Guide existing = guideRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Normalized n = validateUpsert(req);
        boolean wasPublished = existing.getStatus() == PublishStatus.PUBLISHED;
        // STEP-MKT-02 UPDATE + translation 整单覆盖 + 审计
        applyUpsert(existing, n);
        guideRepository.update(existing);
        guideRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        audit.record("编辑指南", n.title(), null);
        // STEP-MKT-03 提交后失效 + MQ（同 E-MKT-43 口径）
        if (wasPublished || n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit();
        }
        return toDto(guideRepository.findById(id), nonNull(req.translations()));
    }

    /** E-MKT-45：删除（TX-MKT-025） */
    @Transactional
    public void delete(Long id) {
        Guide existing = guideRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-02 物理删除双表 + 审计（先清译文，再删主表）
        guideRepository.deleteTranslationsByGuideId(id);
        guideRepository.deleteById(id);
        audit.record("删除指南", existing.getTitle(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ
        if (existing.getStatus() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit();
        }
    }

    /** E-MKT-46：发布状态变更（TX-MKT-026；双向合法 + 同态幂等短路 bs-887） */
    @Transactional
    public GuideDto patchStatus(Long id, Integer statusRaw) {
        // V-MKT-082 status 必填 ∈ {draft, published}
        PublishStatus target = PublishStatus.of(statusRaw);
        if (target == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        Guide existing = guideRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Map<Long, List<GuideTranslationDto>> translations = translationsByGuide(List.of(id));
        // STEP-MKT-01 同态幂等短路
        if (existing.getStatus() == target) {
            return toDto(existing, translations.getOrDefault(id, List.of()));
        }
        // STEP-MKT-02 UPDATE + 审计
        guideRepository.updateStatus(id, target);
        audit.record("指南发布状态变更", existing.getTitle(),
                "{\"from\":\"" + existing.getStatus().getKey() + "\",\"to\":\"" + target.getKey() + "\"}");
        // STEP-MKT-03 提交后失效 + MQ + revalidate /wedding-guides ×3 + purge
        invalidateAfterCommit();
        existing.setStatus(target);
        return toDto(existing, translations.getOrDefault(id, List.of()));
    }

    private void invalidateAfterCommit() {
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.GUIDES);
            publisher.publish(MarketingContentInvalidatedPublisher.TYPE_GUIDE_CHANGED);
        });
    }

    private record Normalized(String phase, String timeframe, String title, Integer tasksCount,
                              PublishStatus status, String body) {
    }

    /** V-MKT-075~080 */
    private Normalized validateUpsert(GuideUpsert req) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-075 phase 必填 trim 非空 ≤32
        String phase = MarketingParams.trimToNull(req.phase());
        if (phase == null) {
            errors.reject("phase", "required");
        } else if (phase.length() > 32) {
            errors.reject("phase", "too_long");
        }
        // V-MKT-076 timeframe ≤64 可选；body EN TEXT 可选（DEC-MKT-1）
        String timeframe = MarketingParams.checkMaxLength(req.timeframe(), 64, "timeframe", errors);
        // V-MKT-077 title 必填 trim 非空 ≤128
        String title = MarketingParams.trimToNull(req.title());
        if (title == null) {
            errors.reject("title", "required");
        } else if (title.length() > 128) {
            errors.reject("title", "too_long");
        }
        // V-MKT-078 tasks_count 可选 int ≥0 缺省 0（CV-MKT-003）
        Integer tasksCount = req.tasksCount() == null ? 0 : req.tasksCount();
        if (tasksCount < 0) {
            errors.reject("tasks_count", "range_invalid");
            tasksCount = 0;
        }
        // V-MKT-079 status 必填 ∈ {draft, published}
        PublishStatus status = PublishStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-080 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(phase, timeframe, title, tasksCount, status, req.body());
    }

    /** V-MKT-080 translations locale ∈ {es,fr} 不重复；title ≤128 / body TEXT */
    private void validateTranslations(List<GuideTranslationDto> translations, MarketingFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (GuideTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.title() != null && t.title().length() > 128) {
                errors.reject("translations", "title_too_long");
            }
        }
    }

    private void applyUpsert(Guide guide, Normalized n) {
        guide.setPhase(n.phase());
        guide.setTimeframe(n.timeframe());
        guide.setTitle(n.title());
        guide.setTasksCount(n.tasksCount());
        guide.setStatus(n.status());
        guide.setBody(n.body());
    }

    private List<GuideTranslation> toTranslationRows(List<GuideTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<GuideTranslation> rows = new ArrayList<>(dtos.size());
        for (GuideTranslationDto dto : dtos) {
            GuideTranslation row = new GuideTranslation();
            row.setLocale(dto.locale());
            row.setTitle(dto.title());
            row.setBody(dto.body());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, GuideTranslation> storeTranslationsFor(List<Long> ids, String locale) {
        Map<Long, GuideTranslation> map = new HashMap<>();
        if (!Translations.needsTranslation(locale) || ids.isEmpty()) {
            return map;
        }
        for (GuideTranslation row : guideRepository.listTranslationsByGuideIds(ids)) {
            if (locale.equals(row.getLocale())) {
                map.put(row.getGuideId(), row);
            }
        }
        return map;
    }

    private Map<Long, List<GuideTranslationDto>> translationsByGuide(List<Long> ids) {
        Map<Long, List<GuideTranslationDto>> map = new HashMap<>();
        for (GuideTranslation row : guideRepository.listTranslationsByGuideIds(ids)) {
            map.computeIfAbsent(row.getGuideId(), k -> new ArrayList<>())
                    .add(new GuideTranslationDto(row.getLocale(), row.getTitle(), row.getBody()));
        }
        return map;
    }

    private List<GuideTranslationDto> nonNull(List<GuideTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private GuideDto toDto(Guide g, List<GuideTranslationDto> translations) {
        return new GuideDto(g.getId(), g.getPhase(), g.getTimeframe(), g.getTitle(), g.getTasksCount(),
                g.getStatus().getKey(), g.getBody(), translations);
    }
}
