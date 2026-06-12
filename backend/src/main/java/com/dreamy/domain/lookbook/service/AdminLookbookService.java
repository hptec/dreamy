package com.dreamy.domain.lookbook.service;

import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.lookbook.entity.Lookbook;
import com.dreamy.domain.lookbook.entity.LookbookTranslation;
import com.dreamy.domain.lookbook.repository.LookbookRepository;
import com.dreamy.dto.AdminMarketingDtos.LookbookDto;
import com.dreamy.dto.AdminMarketingDtos.LookbookUpsert;
import com.dreamy.dto.MarketingTranslationDtos.LookbookTranslationDto;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAfterCommitRunner;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.infra.MarketingCacheService;
import com.dreamy.infra.MarketingCacheService.Family;
import com.dreamy.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.port.CatalogQueryPort;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台 Lookbook 服务（E-MKT-37~41；TX-MKT-019~022；TASK-044 lookbook_publish 双向迁移）。
 * L2 TRACE: V-MKT-066~073 / CV-MKT-005 / CACHE-MKT-006/007。
 */
@Service
public class AdminLookbookService {

    private static final List<String> STATUS_FILTER = List.of("all", "draft", "published");

    private final LookbookRepository lookbookRepository;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final MarketingAfterCommitRunner afterCommit;
    private final MarketingContentInvalidatedPublisher publisher;
    private final CatalogQueryPort catalogQueryPort;

    public AdminLookbookService(LookbookRepository lookbookRepository, MarketingCacheService cache,
                                MarketingAuditRecorder audit, MarketingAfterCommitRunner afterCommit,
                                MarketingContentInvalidatedPublisher publisher, CatalogQueryPort catalogQueryPort) {
        this.lookbookRepository = lookbookRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.publisher = publisher;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-37：列表（status 筛选 + product_ids 件数派生 + translations） */
    public List<LookbookDto> list(String status) {
        // V-MKT-066 status ∈ {all, draft, published} 缺省 all
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!STATUS_FILTER.contains(statusFilter)) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        PublishStatus statusEnum = "all".equals(statusFilter) ? null : PublishStatus.of(statusFilter);
        List<Lookbook> lookbooks = lookbookRepository.listAdmin(statusEnum);
        List<Long> ids = lookbooks.stream().map(Lookbook::getId).toList();
        Map<Long, List<Long>> productIds = lookbookRepository.listProductIdsByLookbookIds(ids);
        Map<Long, List<LookbookTranslationDto>> translations = translationsByLookbook(ids);
        return lookbooks.stream().map(lb -> toDto(lb, productIds.getOrDefault(lb.getId(), List.of()),
                translations.getOrDefault(lb.getId(), List.of()))).toList();
    }

    /** E-MKT-38：创建（TX-MKT-019） */
    @Transactional
    public LookbookDto create(LookbookUpsert req) {
        Normalized n = validateUpsert(req);
        // STEP-MKT-01 INSERT 三表批插
        Lookbook lookbook = new Lookbook();
        applyUpsert(lookbook, n);
        lookbookRepository.insert(lookbook);
        lookbookRepository.replaceProducts(lookbook.getId(), n.productIds());
        lookbookRepository.replaceTranslations(lookbook.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-02 审计
        audit.record("创建Lookbook", n.title(), null);
        // STEP-MKT-03 提交后（published）失效 + MQ → revalidate /inspiration ×3 + purge
        if (n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(lookbook.getId());
        }
        return toDto(lookbookRepository.findById(lookbook.getId()), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-39：编辑（TX-MKT-020） */
    @Transactional
    public LookbookDto update(Long id, LookbookUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        Lookbook existing = lookbookRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Normalized n = validateUpsert(req);
        boolean wasPublished = existing.getStatus() == PublishStatus.PUBLISHED;
        // STEP-MKT-02 UPDATE + 子表整单覆盖 + 审计
        applyUpsert(existing, n);
        existing.setId(id);
        lookbookRepository.update(existing);
        lookbookRepository.replaceProducts(id, n.productIds());
        lookbookRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        audit.record("编辑Lookbook", n.title(), null);
        // STEP-MKT-03 提交后失效 + MQ（同 E-MKT-38 口径，draft 间编辑不发）
        if (wasPublished || n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(id);
        }
        return toDto(lookbookRepository.findById(id), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-40：删除（TX-MKT-021） */
    @Transactional
    public void delete(Long id) {
        Lookbook existing = lookbookRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-02 物理删除三表 + 审计
        lookbookRepository.deleteById(id);
        lookbookRepository.deleteProductsByLookbookId(id);
        lookbookRepository.deleteTranslationsByLookbookId(id);
        audit.record("删除Lookbook", existing.getTitle(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ
        if (existing.getStatus() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(id);
        }
    }

    /** E-MKT-41：发布状态变更（TX-MKT-022；publish/unpublish 双向合法 + 同态幂等短路 bs-885/886） */
    @Transactional
    public LookbookDto patchStatus(Long id, String statusRaw) {
        // V-MKT-073 status 必填 ∈ {draft, published}
        PublishStatus target = PublishStatus.of(statusRaw);
        if (target == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        Lookbook existing = lookbookRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        List<Long> productIds = lookbookRepository.listProductIdsByLookbookId(id);
        Map<Long, List<LookbookTranslationDto>> translations = translationsByLookbook(List.of(id));
        // STEP-MKT-01 同态幂等短路
        if (existing.getStatus() == target) {
            return toDto(existing, productIds, translations.getOrDefault(id, List.of()));
        }
        // STEP-MKT-02 UPDATE + 审计
        lookbookRepository.updateStatus(id, target);
        audit.record("Lookbook发布状态变更", existing.getTitle(),
                "{\"from\":\"" + existing.getStatus().getKey() + "\",\"to\":\"" + target.getKey() + "\"}");
        // STEP-MKT-03 提交后失效 + MQ + revalidate /inspiration ×3 + purge
        invalidateAfterCommit(id);
        existing.setStatus(target);
        return toDto(existing, productIds, translations.getOrDefault(id, List.of()));
    }

    private void invalidateAfterCommit(Long id) {
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.LOOKBOOKS);
            cache.invalidateFamily(Family.LOOKBOOK);
            publisher.publish(MarketingContentInvalidatedPublisher.TYPE_LOOKBOOK_CHANGED, null, null, id);
        });
    }

    private record Normalized(String title, String theme, PublishStatus status, String description,
                              List<Long> productIds) {
    }

    /** V-MKT-067~071 */
    private Normalized validateUpsert(LookbookUpsert req) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-067 title 必填 trim 非空 ≤128
        String title = MarketingParams.trimToNull(req.title());
        if (title == null) {
            errors.reject("title", "required");
        } else if (title.length() > 128) {
            errors.reject("title", "too_long");
        }
        // V-MKT-068 theme ≤32 可选；description EN ≤500 可选（DEC-MKT-1）
        String theme = MarketingParams.checkMaxLength(req.theme(), 32, "theme", errors);
        String description = MarketingParams.checkMaxLength(req.description(), 500, "description", errors);
        // V-MKT-069 status 必填 ∈ {draft, published}
        PublishStatus status = PublishStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-070 product_ids 去重 + 存在性校验（CV-MKT-005）
        List<Long> productIds = List.of();
        if (req.productIds() != null && !req.productIds().isEmpty()) {
            productIds = new ArrayList<>(new LinkedHashSet<>(req.productIds()));
            if (catalogQueryPort.listExistingIds(productIds).size() != productIds.size()) {
                errors.reject("product_ids", "not_exists");
            }
        }
        // V-MKT-071 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(title, theme, status, description, productIds);
    }

    /** V-MKT-071 translations locale ∈ {es,fr} 不重复；title ≤128 / description ≤500 */
    private void validateTranslations(List<LookbookTranslationDto> translations, MarketingFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (LookbookTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.title() != null && t.title().length() > 128) {
                errors.reject("translations", "title_too_long");
            }
            if (t.description() != null && t.description().length() > 500) {
                errors.reject("translations", "description_too_long");
            }
        }
    }

    private void applyUpsert(Lookbook lookbook, Normalized n) {
        lookbook.setTitle(n.title());
        lookbook.setTheme(n.theme());
        lookbook.setStatus(n.status());
        lookbook.setDescription(n.description());
    }

    private List<LookbookTranslation> toTranslationRows(List<LookbookTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<LookbookTranslation> rows = new ArrayList<>(dtos.size());
        for (LookbookTranslationDto dto : dtos) {
            LookbookTranslation row = new LookbookTranslation();
            row.setLocale(dto.locale());
            row.setTitle(dto.title());
            row.setDescription(dto.description());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<LookbookTranslationDto>> translationsByLookbook(List<Long> ids) {
        Map<Long, List<LookbookTranslationDto>> map = new HashMap<>();
        for (LookbookTranslation row : lookbookRepository.listTranslationsByLookbookIds(ids)) {
            map.computeIfAbsent(row.getLookbookId(), k -> new ArrayList<>())
                    .add(new LookbookTranslationDto(row.getLocale(), row.getTitle(), row.getDescription()));
        }
        return map;
    }

    private List<LookbookTranslationDto> nonNull(List<LookbookTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private LookbookDto toDto(Lookbook lb, List<Long> productIds, List<LookbookTranslationDto> translations) {
        return new LookbookDto(lb.getId(), lb.getTitle(), lb.getTheme(), lb.getStatus().getKey(),
                lb.getDescription(), productIds, translations);
    }
}
