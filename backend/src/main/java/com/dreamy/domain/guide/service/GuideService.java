package com.dreamy.domain.guide.service;

import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.guide.entity.Guide;
import com.dreamy.domain.guide.entity.GuideTranslation;
import com.dreamy.domain.guide.repository.GuideRepository;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.dto.AdminMarketingDtos.GuideDto;
import com.dreamy.dto.AdminMarketingDtos.GuideUpsert;
import com.dreamy.dto.AdminMarketingDtos.GuideTask;
import com.dreamy.dto.MarketingTranslationDtos.GuideTranslationDto;
import com.dreamy.dto.StoreMarketingDtos.StoreGuide;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
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
    private final GuideTaskService guideTasks;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public GuideService(GuideRepository guideRepository, MarketingCacheService cache,
                        MarketingAuditRecorder audit, CacheInvalidationTaskService cacheTasks, GuideTaskService guideTasks) {
        this.guideRepository = guideRepository;
        this.cache = cache;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
        this.guideTasks = guideTasks;
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
        Map<Long, List<GuideTask>> tasksByGuide = guideTasks.listByGuideIds(guides.stream().map(Guide::getId).toList(), locale);
        for (Guide g : guides) {
            GuideTranslation t = translations.get(g.getId());
            items.add(new StoreGuide(g.getId(), g.getPhase(), g.getTimeframe(),
                    Translations.coalesce(t == null ? null : t.getTitle(), g.getTitle()),
                    Translations.coalesce(t == null ? null : t.getBody(), g.getBody()),
                    tasksByGuide.getOrDefault(g.getId(), List.of()).size(), tasksByGuide.getOrDefault(g.getId(), List.of())));
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

    /** Returns the persisted, stable IDs for tasks a shopper can currently complete. */
    public Set<Long> publishedTaskIds(Long guideId) {
        Guide guide = guideRepository.findById(guideId);
        if (guide == null || guide.getStatus() != PublishStatus.PUBLISHED) {
            return Set.of();
        }
        return guideTasks.ids(guideId);
    }

    @Transactional
    public List<GuideDto> reorder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return listAdmin(null);
        List<Guide> current = guideRepository.listAdmin(null);
        Set<Long> valid = current.stream().map(Guide::getId).collect(java.util.stream.Collectors.toSet());
        if (ids.size() != valid.size() || !valid.containsAll(ids) || new HashSet<>(ids).size() != ids.size())
            throw MarketingException.fieldValidation("ids", "invalid_order");
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            guideRepository.updateSortOrder(id, i);
            Guide guide = current.stream().filter(item -> item.getId().equals(id)).findFirst().orElse(null);
            if (guide != null && guide.getStatus() == PublishStatus.PUBLISHED) enqueue("guide.reorder", guide);
        }
        return listAdmin(null);
    }

    /** E-MKT-43：创建（TX-MKT-023） */
    @Transactional
    public GuideDto create(GuideUpsert req) {
        Normalized n = validateUpsert(req);
        // STEP-MKT-01 INSERT guide + translation 批插
        Guide guide = new Guide();
        applyUpsert(guide, n);
        guide.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        guideRepository.insert(guide);
        guideTasks.replace(guide.getId(), n.tasks());
        guideRepository.replaceTranslations(guide.getId(), toTranslationRows(req.translations()));
        guideTasks.replaceTranslations(guide.getId(), req.translations());
        // STEP-MKT-02 审计
        audit.record("创建指南", n.title(), null);
        // STEP-MKT-03 提交后（published）失效 + MQ → revalidate /wedding-guides ×3 + purge
        if (n.status() == PublishStatus.PUBLISHED) {
            enqueue("guide.create", guide);
        }
        return toDto(guideRepository.findById(guide.getId()), translationsByGuide(List.of(guide.getId())).getOrDefault(guide.getId(), List.of()));
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
        if (req.sortOrder() != null) existing.setSortOrder(req.sortOrder());
        guideRepository.update(existing);
        guideTasks.replace(id, n.tasks());
        guideRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        guideTasks.replaceTranslations(id, req.translations());
        audit.record("编辑指南", n.title(), null);
        // STEP-MKT-03 提交后失效 + MQ（同 E-MKT-43 口径）
        if (wasPublished || n.status() == PublishStatus.PUBLISHED) {
            enqueue("guide.update", existing);
        }
        return toDto(guideRepository.findById(id), translationsByGuide(List.of(id)).getOrDefault(id, List.of()));
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
        guideTasks.deleteByGuideId(id);
        guideRepository.deleteById(id);
        audit.record("删除指南", existing.getTitle(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ
        if (existing.getStatus() == PublishStatus.PUBLISHED) {
            enqueue("guide.delete", existing);
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
        enqueue("guide.status", existing);
        existing.setStatus(target);
        return toDto(existing, translations.getOrDefault(id, List.of()));
    }

    private void enqueue(String triggerPoint, Guide guide) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "guide", guide.getId(), guide.getTitle(), CacheInvalidationPlans.GUIDE,
                null, java.util.Map.of(), null);
    }

    private record Normalized(String phase, String timeframe, String title, Integer tasksCount,
                              PublishStatus status, String body, List<GuideTask> tasks) {
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
        List<GuideTask> tasks = normalizeTaskInput(req.tasks(), errors);
        Integer tasksCount = tasks.size();
        // V-MKT-079 status 必填 ∈ {draft, published}
        PublishStatus status = PublishStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-080 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(phase, timeframe, title, tasksCount, status, req.body(), tasks);
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
            if (t.tasks() != null) {
                for (var task : t.tasks()) {
                    if (task != null && task.label() != null && task.label().length() > 256) {
                        errors.reject("translations", "task_label_too_long");
                    }
                }
            }
        }
    }

    private void applyUpsert(Guide guide, Normalized n) {
        guide.setPhase(n.phase());
        guide.setTimeframe(n.timeframe());
        guide.setTitle(n.title());
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
                    .add(new GuideTranslationDto(row.getLocale(), row.getTitle(), row.getBody(),
                            guideTasks.translations(row.getGuideId()).getOrDefault(row.getLocale(), List.of())));
        }
        return map;
    }

    private List<GuideTranslationDto> nonNull(List<GuideTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private GuideDto toDto(Guide g, List<GuideTranslationDto> translations) {
        List<GuideTask> tasks = guideTasks.list(g.getId());
        return new GuideDto(g.getId(), g.getPhase(), g.getTimeframe(), g.getTitle(), tasks.size(), tasks,
                g.getStatus().getKey(), g.getSortOrder(), g.getBody(), translations);
    }
    private List<GuideTask> normalizeTaskInput(List<GuideTask> input, MarketingFieldErrors errors) {
        if (input == null) return List.of();
        if (input.size() > 50) errors.reject("tasks", "too_many");
        List<GuideTask> tasks = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            GuideTask raw = input.get(i);
            String label = raw == null ? null : MarketingParams.trimToNull(raw.label());
            if (label == null) {
                errors.reject("tasks", "item_required");
            } else if (label.length() > 256) {
                errors.reject("tasks", "item_too_long");
            } else {
                tasks.add(new GuideTask(raw.taskId(), label));
            }
        }
        return tasks;
    }
}
