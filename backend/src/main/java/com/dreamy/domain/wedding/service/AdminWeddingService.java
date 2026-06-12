package com.dreamy.domain.wedding.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.PublishStatus;
import com.dreamy.domain.wedding.entity.RealWedding;
import com.dreamy.domain.wedding.entity.RealWeddingTranslation;
import com.dreamy.domain.wedding.repository.RealWeddingRepository;
import com.dreamy.dto.AdminMarketingDtos.RealWeddingDto;
import com.dreamy.dto.AdminMarketingDtos.RealWeddingUpsert;
import com.dreamy.dto.MarketingTranslationDtos.RealWeddingTranslationDto;
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
import com.dreamy.support.MarketingPaginatedSupport;
import huihao.page.Paginated;
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
 * 后台婚礼案例服务（E-MKT-32~36；TX-MKT-015~018；TASK-046 real_wedding_publish 双向迁移无非法态）。
 * L2 TRACE: V-MKT-058~065 / CV-MKT-005（product_ids 经 catalogQueryPort 存在性校验）/ CACHE-MKT-004/005。
 */
@Service
public class AdminWeddingService {

    private static final List<String> STATUS_FILTER = List.of("all", "draft", "published");

    private final RealWeddingRepository weddingRepository;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final MarketingAfterCommitRunner afterCommit;
    private final MarketingContentInvalidatedPublisher publisher;
    private final CatalogQueryPort catalogQueryPort;

    public AdminWeddingService(RealWeddingRepository weddingRepository, MarketingCacheService cache,
                               MarketingAuditRecorder audit, MarketingAfterCommitRunner afterCommit,
                               MarketingContentInvalidatedPublisher publisher, CatalogQueryPort catalogQueryPort) {
        this.weddingRepository = weddingRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.publisher = publisher;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-32：分页列表（product_ids 件数派生批查防 N+1——RM-MKT-052） */
    public Paginated<RealWeddingDto> page(Integer page, Integer pageSize, String status) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        // V-MKT-058 status ∈ {all, draft, published} 缺省 all
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!STATUS_FILTER.contains(statusFilter)) {
            errors.reject("status", "invalid_enum");
        }
        errors.throwIfAny();
        PublishStatus statusEnum = "all".equals(statusFilter) ? null : PublishStatus.of(statusFilter);
        Page<RealWedding> result = weddingRepository.pageAdmin(statusEnum, parsedPage, parsedPageSize);
        List<Long> ids = result.getRecords().stream().map(RealWedding::getId).toList();
        Map<Long, List<Long>> productIds = weddingRepository.listProductIdsByWeddingIds(ids);
        Map<Long, List<RealWeddingTranslationDto>> translations = translationsByWedding(ids);
        return MarketingPaginatedSupport.of(result, w -> toDto(w, productIds.getOrDefault(w.getId(), List.of()),
                translations.getOrDefault(w.getId(), List.of())));
    }

    /** E-MKT-33：创建（TX-MKT-015） */
    @Transactional
    public RealWeddingDto create(RealWeddingUpsert req) {
        Normalized n = validateUpsert(req);
        // STEP-MKT-01 INSERT 三表批插
        RealWedding wedding = new RealWedding();
        applyUpsert(wedding, n, req);
        weddingRepository.insert(wedding);
        weddingRepository.replaceProducts(wedding.getId(), n.productIds());
        weddingRepository.replaceTranslations(wedding.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-02 审计
        audit.record("创建婚礼案例", n.couple(), null);
        // STEP-MKT-03 提交后（published）失效 + MQ
        if (n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(wedding.getId());
        }
        return toDto(weddingRepository.findById(wedding.getId()), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-34：编辑（TX-MKT-016；status 变更等价 publish/unpublish，双向均合法） */
    @Transactional
    public RealWeddingDto update(Long id, RealWeddingUpsert req) {
        // STEP-MKT-01 不存在 → 404701
        RealWedding existing = weddingRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        Normalized n = validateUpsert(req);
        boolean wasPublished = existing.getStatus() == PublishStatus.PUBLISHED;
        // STEP-MKT-02 UPDATE + 子表整单覆盖
        applyUpsert(existing, n, req);
        weddingRepository.update(existing);
        weddingRepository.replaceProducts(id, n.productIds());
        weddingRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-03 审计
        audit.record("编辑婚礼案例", n.couple(), null);
        // STEP-MKT-04 提交后失效 + MQ（draft 间编辑不发）
        if (wasPublished || n.status() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(id);
        }
        return toDto(weddingRepository.findById(id), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-35：删除（TX-MKT-017） */
    @Transactional
    public void delete(Long id) {
        RealWedding existing = weddingRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        // STEP-MKT-02 物理删除三表 + 审计
        weddingRepository.deleteById(id);
        weddingRepository.deleteProductsByWeddingId(id);
        weddingRepository.deleteTranslationsByWeddingId(id);
        audit.record("删除婚礼案例", existing.getCouple(), null);
        // STEP-MKT-03 提交后（原 published）失效 + MQ
        if (existing.getStatus() == PublishStatus.PUBLISHED) {
            invalidateAfterCommit(id);
        }
    }

    /** E-MKT-36：发布状态变更（TX-MKT-018；real_wedding_publish 双向合法 + 同态幂等短路） */
    @Transactional
    public RealWeddingDto patchStatus(Long id, String statusRaw) {
        // V-MKT-065 status 必填 ∈ {draft, published}
        PublishStatus target = PublishStatus.of(statusRaw);
        if (target == null) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        RealWedding existing = weddingRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.CONTENT_NOT_FOUND);
        }
        List<Long> productIds = weddingRepository.listProductIdsByWeddingId(id);
        Map<Long, List<RealWeddingTranslationDto>> translations = translationsByWedding(List.of(id));
        // STEP-MKT-02 同态幂等短路（bs-888/889 仅一次副作用）
        if (existing.getStatus() == target) {
            return toDto(existing, productIds, translations.getOrDefault(id, List.of()));
        }
        // STEP-MKT-03/04 双向合法 → UPDATE + 审计
        weddingRepository.updateStatus(id, target);
        audit.record("案例发布状态变更", existing.getCouple(),
                "{\"from\":\"" + existing.getStatus().getKey() + "\",\"to\":\"" + target.getKey() + "\"}");
        // STEP-MKT-05 提交后失效 + MQ + revalidate + purge
        invalidateAfterCommit(id);
        existing.setStatus(target);
        return toDto(existing, productIds, translations.getOrDefault(id, List.of()));
    }

    private void invalidateAfterCommit(Long id) {
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.WEDDINGS);
            cache.invalidateFamily(Family.WEDDING);
            publisher.publishWedding(id);
        });
    }

    private record Normalized(String couple, PublishStatus status, String title, String story,
                              List<Long> productIds) {
    }

    /** V-MKT-059~063 */
    private Normalized validateUpsert(RealWeddingUpsert req) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        // V-MKT-059 couple 必填 trim 非空 ≤64
        String couple = MarketingParams.trimToNull(req.couple());
        if (couple == null) {
            errors.reject("couple", "required");
        } else if (couple.length() > 64) {
            errors.reject("couple", "too_long");
        }
        // V-MKT-060 location ≤128 / theme ≤32 / wedding_date ≤16 / cover ≤512 可选
        MarketingParams.checkMaxLength(req.location(), 128, "location", errors);
        MarketingParams.checkMaxLength(req.theme(), 32, "theme", errors);
        MarketingParams.checkMaxLength(req.weddingDate(), 16, "wedding_date", errors);
        MarketingParams.checkMaxLength(req.cover(), 512, "cover", errors);
        // V-MKT-061 status 必填 ∈ {draft, published}
        PublishStatus status = PublishStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        }
        // DEC-MKT-1 EN title ≤200 / story TEXT
        String title = MarketingParams.checkMaxLength(req.title(), 200, "title", errors);
        // V-MKT-062 product_ids 去重 + catalogQueryPort 存在性校验（CV-MKT-005）
        List<Long> productIds = validateProductIds(req.productIds(), errors);
        // V-MKT-063 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(couple, status, title, req.story(), productIds);
    }

    private List<Long> validateProductIds(List<Long> raw, MarketingFieldErrors errors) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> deduped = new ArrayList<>(new LinkedHashSet<>(raw));
        List<Long> existing = catalogQueryPort.listExistingIds(deduped);
        if (existing.size() != deduped.size()) {
            errors.reject("product_ids", "not_exists");
        }
        return deduped;
    }

    /** V-MKT-063 translations locale ∈ {es,fr} 不重复；title ≤200 / story TEXT */
    private void validateTranslations(List<RealWeddingTranslationDto> translations, MarketingFieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (RealWeddingTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.title() != null && t.title().length() > 200) {
                errors.reject("translations", "title_too_long");
            }
        }
    }

    private void applyUpsert(RealWedding wedding, Normalized n, RealWeddingUpsert req) {
        wedding.setCouple(n.couple());
        wedding.setLocation(MarketingParams.trimToNull(req.location()));
        wedding.setTheme(MarketingParams.trimToNull(req.theme()));
        wedding.setWeddingDate(MarketingParams.trimToNull(req.weddingDate()));
        wedding.setCover(MarketingParams.trimToNull(req.cover()));
        wedding.setStatus(n.status());
        wedding.setTitle(n.title());
        wedding.setStory(n.story());
    }

    private List<RealWeddingTranslation> toTranslationRows(List<RealWeddingTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<RealWeddingTranslation> rows = new ArrayList<>(dtos.size());
        for (RealWeddingTranslationDto dto : dtos) {
            RealWeddingTranslation row = new RealWeddingTranslation();
            row.setLocale(dto.locale());
            row.setTitle(dto.title());
            row.setStory(dto.story());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<RealWeddingTranslationDto>> translationsByWedding(List<Long> ids) {
        Map<Long, List<RealWeddingTranslationDto>> map = new HashMap<>();
        for (RealWeddingTranslation row : weddingRepository.listTranslationsByWeddingIds(ids)) {
            map.computeIfAbsent(row.getRealWeddingId(), k -> new ArrayList<>())
                    .add(new RealWeddingTranslationDto(row.getLocale(), row.getTitle(), row.getStory()));
        }
        return map;
    }

    private List<RealWeddingTranslationDto> nonNull(List<RealWeddingTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private RealWeddingDto toDto(RealWedding w, List<Long> productIds,
                                 List<RealWeddingTranslationDto> translations) {
        return new RealWeddingDto(w.getId(), w.getCouple(), w.getLocation(), w.getTheme(), w.getWeddingDate(),
                w.getCover(), w.getStatus().getKey(), w.getTitle(), w.getStory(), productIds, translations);
    }
}
