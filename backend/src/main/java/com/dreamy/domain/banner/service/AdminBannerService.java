package com.dreamy.domain.banner.service;

import com.dreamy.domain.banner.entity.Banner;
import java.time.LocalDateTime;
import com.dreamy.domain.banner.entity.BannerTranslation;
import com.dreamy.domain.banner.repository.BannerRepository;
import com.dreamy.enums.BannerPosition;
import com.dreamy.enums.ContentStatus;
import com.dreamy.dto.AdminMarketingDtos.BannerDto;
import com.dreamy.dto.AdminMarketingDtos.BannerUpsert;
import com.dreamy.dto.MarketingTranslationDtos.BannerTranslationDto;
import com.dreamy.domain.cache.service.CacheInvalidationPlans;
import com.dreamy.domain.cache.service.CacheInvalidationTaskService;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.support.ContentStateGuards;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 Banner 服务（E-MKT-21~25；TX-MKT-007~010；TASK-032 banner_lifecycle）。
 * 写操作与缓存任务同事务提交；时间窗开始/结束边界使用 UTC 计划任务。
 * L2 TRACE: V-MKT-038~047 / RM-MKT-001~012 / CACHE-MKT-001。
 */
@Service
public class AdminBannerService {

    private final BannerRepository bannerRepository;
    private final MarketingAuditRecorder audit;
    private final CacheInvalidationTaskService cacheTasks;

    public AdminBannerService(BannerRepository bannerRepository, MarketingAuditRecorder audit,
                              CacheInvalidationTaskService cacheTasks) {
        this.bannerRepository = bannerRepository;
        this.audit = audit;
        this.cacheTasks = cacheTasks;
    }

    /** E-MKT-21：列表（position 筛选 ORDER BY sort；「已过窗」标识前端按 end_time 自行派生——DEC-MKT-2） */
    public List<BannerDto> list(Integer position) {
        // V-MKT-038 position 可选枚举
        BannerPosition parsed = null;
        if (position != null) {
            parsed = BannerPosition.of(position);
            if (parsed == null) {
                throw MarketingException.fieldValidation("position", "invalid_enum");
            }
        }
        List<Banner> banners = bannerRepository.listAdmin(parsed);
        Map<Long, List<BannerTranslationDto>> translations = translationsByBanner(
                banners.stream().map(Banner::getId).toList());
        return banners.stream().map(b -> toDto(b, translations.getOrDefault(b.getId(), List.of()))).toList();
    }

    /** E-MKT-22：创建（TX-MKT-007；banner_lifecycle 初态 draft/published） */
    @Transactional
    public BannerDto create(BannerUpsert req) {
        Normalized n = validateUpsert(req, true);
        Banner banner = new Banner();
        applyUpsert(banner, n, req);
        // STEP-MKT-01 INSERT banner + translation 批插
        bannerRepository.insert(banner);
        bannerRepository.replaceTranslations(banner.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-02 审计
        audit.record("创建Banner", banner.getName(), null);
        // STEP-MKT-03 published 内容创建可追踪的即时失效任务
        if (banner.getStatus() == ContentStatus.PUBLISHED) {
            enqueueImmediate("banner.create", banner);
        }
        replaceWindowTasks(banner);
        return toDto(bannerRepository.findById(banner.getId()), nonNull(req.translations()));
    }

    /** E-MKT-23：编辑（TX-MKT-008；整单保存，status 迁移合法性同 E-MKT-25 guard） */
    @Transactional
    public BannerDto update(Long id, BannerUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        Banner existing = bannerRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Normalized n = validateUpsert(req, false);
        // STEP-MKT-02 status 变更时校验迁移合法性（banner_lifecycle）→ 409703
        if (existing.getStatus() != n.status()
                && !ContentStateGuards.transitionAllowed(existing.getStatus(), n.status())) {
            throw MarketingException.stateInvalid("illegal_transition");
        }
        // STEP-MKT-03 UPDATE + translation 整单覆盖
        applyUpsert(existing, n, req);
        bannerRepository.update(existing);
        bannerRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-04 审计
        audit.record("编辑Banner", existing.getName(), null);
        // STEP-MKT-05 写成功触发失效任务，draft 间编辑同样覆盖潜在投放清单变化
        enqueueImmediate("banner.update", existing);
        replaceWindowTasks(existing);
        return toDto(bannerRepository.findById(id), nonNull(req.translations()));
    }

    /** E-MKT-24：删除（TX-MKT-009；banner_lifecycle 全态可删） */
    @Transactional
    public void delete(Long id) {
        Banner existing = bannerRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-02 物理删除双表 + 审计（先清译文，再删主表）
        bannerRepository.deleteTranslationsByBannerId(id);
        bannerRepository.deleteById(id);
        audit.record("删除Banner", existing.getName(), null);
        // STEP-MKT-03 创建失效任务并取消未到期窗口任务
        enqueueImmediate("banner.delete", existing);
        cacheTasks.cancelFuture("banner", id, "banner.window.");
    }

    /** E-MKT-25：行内 Toggle（TX-MKT-010；publish/take_offline/republish；幂等短路不开事务语义由同态直返承载） */
    @Transactional
    public BannerDto toggleStatus(Long id, Integer statusRaw) {
        // V-MKT-047 status 必填枚举
        ContentStatus target = ContentStatus.of(statusRaw);
        if (target == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        Banner existing = bannerRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Map<Long, List<BannerTranslationDto>> translations = translationsByBanner(List.of(id));
        // STEP-MKT-02 幂等：目标态=当前态 → 直接返回（不写审计不发事件）
        if (existing.getStatus() == target) {
            return toDto(existing, translations.getOrDefault(id, List.of()));
        }
        // STEP-MKT-03 迁移 guard → 409703
        if (!ContentStateGuards.transitionAllowed(existing.getStatus(), target)) {
            throw MarketingException.stateInvalid("illegal_transition");
        }
        // STEP-MKT-04 UPDATE status + 审计（Toggle 归入 编辑Banner，changes 记 status before/after）
        String changes = "{\"status\":{\"before\":\"" + existing.getStatus().getKey()
                + "\",\"after\":\"" + target.getKey() + "\"}}";
        bannerRepository.updateStatus(id, target);
        audit.record("编辑Banner", existing.getName(), changes);
        // STEP-MKT-05 创建即时失效任务并重建窗口边界任务
        existing.setStatus(target);
        enqueueImmediate("banner.status", existing);
        replaceWindowTasks(existing);
        return toDto(existing, translations.getOrDefault(id, List.of()));
    }

    private void enqueueImmediate(String triggerPoint, Banner banner) {
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_BUSINESS_WRITE, triggerPoint,
                "banner", banner.getId(), banner.getName(), CacheInvalidationPlans.BANNER,
                null, Map.of("position", banner.getPosition().getKey()), null);
    }

    private void replaceWindowTasks(Banner banner) {
        cacheTasks.cancelFuture("banner", banner.getId(), "banner.window.");
        if (banner.getStatus() != ContentStatus.PUBLISHED) return;
        scheduleBoundary(banner, "start", banner.getStartTime());
        scheduleBoundary(banner, "end", banner.getEndTime());
    }

    private void scheduleBoundary(Banner banner, String boundary, LocalDateTime executeAt) {
        if (executeAt == null || !executeAt.isAfter(cacheTasks.now())) return;
        cacheTasks.enqueue(CacheInvalidationTaskService.MODE_SCHEDULED, "banner.window." + boundary,
                "banner", banner.getId(), banner.getName(), CacheInvalidationPlans.BANNER,
                executeAt, Map.of("boundary", boundary, "position", banner.getPosition().getKey()), null);
    }

    private record Normalized(String name, String imageUrl, BannerPosition position, ContentStatus status,
                              Integer sort, String title, String subtitle, String ctaText, String ctaLink,
                              String ctaTextSecondary, String ctaLinkSecondary) {
    }

    /** V-MKT-039~044（create=true 时 archived 禁作初态——V-MKT-042） */
    private Normalized validateUpsert(BannerUpsert req, boolean create) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-039 name 必填 ≤128；image_url 必填 ≤512（catalog E-CAT-38 presign，scope=banner）
        String name = MarketingParams.trimToNull(req.name());
        if (name == null) {
            errors.reject("name", "required");
        } else if (name.length() > 128) {
            errors.reject("name", "too_long");
        }
        String imageUrl = MarketingParams.trimToNull(req.imageUrl());
        if (imageUrl == null) {
            errors.reject("image_url", "required");
        } else if (imageUrl.length() > 512) {
            errors.reject("image_url", "too_long");
        }
        // V-MKT-040 position 必填枚举
        BannerPosition position = BannerPosition.of(req.position());
        if (position == null) {
            errors.reject("position", "invalid_enum");
        }
        // V-MKT-041 均给定时 end_time > start_time（CV-MKT-004）
        if (req.startTime() != null && req.endTime() != null && !req.endTime().isAfter(req.startTime())) {
            errors.reject("end_time", "before_start");
        }
        // V-MKT-042 status 必填枚举；创建态仅 draft/published
        ContentStatus status = ContentStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        } else if (create && status == ContentStatus.ARCHIVED) {
            errors.reject("status", "invalid_initial");
        }
        // V-MKT-043 sort 必填 int ≥0（CV-MKT-003）
        if (req.sort() == null) {
            errors.reject("sort", "required");
        } else if (req.sort() < 0) {
            errors.reject("sort", "range_invalid");
        }
        // V-MKT-044 EN 文案列长度（DEC-MKT-1）+ translations 校验
        String title = MarketingParams.checkMaxLength(req.title(), 255, "title", errors);
        String subtitle = MarketingParams.checkMaxLength(req.subtitle(), 255, "subtitle", errors);
        String ctaText = MarketingParams.checkMaxLength(req.ctaText(), 64, "cta_text", errors);
        String ctaLink = MarketingParams.checkMaxLength(req.ctaLink(), 512, "cta_link", errors);
        String ctaTextSecondary = MarketingParams.checkMaxLength(
                req.ctaTextSecondary(), 64, "cta_text_secondary", errors);
        String ctaLinkSecondary = MarketingParams.checkMaxLength(
                req.ctaLinkSecondary(), 512, "cta_link_secondary", errors);
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(name, imageUrl, position, status, req.sort(), title, subtitle, ctaText,
                ctaLink, ctaTextSecondary, ctaLinkSecondary);
    }

    /** V-MKT-044 translations locale ∈ {es,fr} 不重复；图片 ≤512，title/subtitle ≤255、cta_text ≤64（CV-MKT-007） */
    private void validateTranslations(List<BannerTranslationDto> translations, MarketingFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (BannerTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.imageUrl() != null && t.imageUrl().length() > 512) {
                errors.reject("translations", "image_url_too_long");
            }
            if (t.title() != null && t.title().length() > 255) {
                errors.reject("translations", "title_too_long");
            }
            if (t.subtitle() != null && t.subtitle().length() > 255) {
                errors.reject("translations", "subtitle_too_long");
            }
            if (t.ctaText() != null && t.ctaText().length() > 64) {
                errors.reject("translations", "cta_text_too_long");
            }
            if (t.ctaTextSecondary() != null && t.ctaTextSecondary().length() > 64) {
                errors.reject("translations", "cta_text_secondary_too_long");
            }
        }
    }

    private void applyUpsert(Banner banner, Normalized n, BannerUpsert req) {
        banner.setName(n.name());
        banner.setImageUrl(n.imageUrl());
        banner.setPosition(n.position());
        banner.setStartTime(req.startTime());
        banner.setEndTime(req.endTime());
        banner.setStatus(n.status());
        banner.setSort(n.sort());
        banner.setTitle(n.title());
        banner.setSubtitle(n.subtitle());
        banner.setCtaText(n.ctaText());
        banner.setCtaLink(n.ctaLink());
        banner.setCtaTextSecondary(n.ctaTextSecondary());
        banner.setCtaLinkSecondary(n.ctaLinkSecondary());
    }

    private List<BannerTranslation> toTranslationRows(List<BannerTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<BannerTranslation> rows = new ArrayList<>(dtos.size());
        for (BannerTranslationDto dto : dtos) {
            BannerTranslation row = new BannerTranslation();
            row.setLocale(dto.locale());
            row.setImageUrl(dto.imageUrl());
            row.setTitle(dto.title());
            row.setSubtitle(dto.subtitle());
            row.setCtaText(dto.ctaText());
            row.setCtaTextSecondary(dto.ctaTextSecondary());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<BannerTranslationDto>> translationsByBanner(List<Long> ids) {
        Map<Long, List<BannerTranslationDto>> map = new HashMap<>();
        for (BannerTranslation row : bannerRepository.listTranslationsByBannerIds(ids)) {
            map.computeIfAbsent(row.getBannerId(), k -> new ArrayList<>())
                    .add(new BannerTranslationDto(row.getLocale(), row.getImageUrl(), row.getTitle(), row.getSubtitle(),
                            row.getCtaText(), row.getCtaTextSecondary()));
        }
        return map;
    }

    private List<BannerTranslationDto> nonNull(List<BannerTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private BannerDto toDto(Banner b, List<BannerTranslationDto> translations) {
        return new BannerDto(b.getId(), b.getName(), b.getImageUrl(), b.getPosition().getKey(), b.getStartTime(),
                b.getEndTime(), b.getStatus().getKey(), b.getSort(), b.getTitle(), b.getSubtitle(),
                b.getCtaText(), b.getCtaLink(), b.getCtaTextSecondary(), b.getCtaLinkSecondary(), translations);
    }
}
