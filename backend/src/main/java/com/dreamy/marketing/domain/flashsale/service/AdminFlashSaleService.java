package com.dreamy.marketing.domain.flashsale.service;

import com.dreamy.marketing.domain.enums.FlashSaleStatus;
import com.dreamy.marketing.domain.flashsale.entity.FlashSale;
import com.dreamy.marketing.domain.flashsale.entity.FlashSaleTranslation;
import com.dreamy.marketing.domain.flashsale.repository.FlashSaleRepository;
import com.dreamy.marketing.dto.AdminMarketingDtos.FlashSaleDto;
import com.dreamy.marketing.dto.AdminMarketingDtos.FlashSaleUpsert;
import com.dreamy.marketing.dto.MarketingTranslationDtos.FlashSaleTranslationDto;
import com.dreamy.marketing.error.MarketingErrorCode;
import com.dreamy.marketing.error.MarketingException;
import com.dreamy.marketing.infra.MarketingAfterCommitRunner;
import com.dreamy.marketing.infra.MarketingAuditRecorder;
import com.dreamy.marketing.infra.MarketingCacheService;
import com.dreamy.marketing.infra.MarketingCacheService.Family;
import com.dreamy.marketing.mq.MarketingContentInvalidatedPublisher;
import com.dreamy.marketing.port.CatalogQueryPort;
import com.dreamy.marketing.support.FieldErrors;
import com.dreamy.marketing.support.MarketingParams;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 后台闪购服务（E-MKT-17~20；TX-MKT-004~006；TASK-043 flash_sale_lifecycle）。
 * 写操作（含 active 面）提交后：失效 `marketing:flash:*` → MQ flash_sale_changed（CP-031）。
 * L2 TRACE: V-MKT-030~037 / CV-MKT-005/011 / CACHE-MKT-009。
 */
@Service
public class AdminFlashSaleService {

    private static final List<String> STATUS_FILTER = List.of("all", "draft", "scheduled", "active", "ended");

    private final FlashSaleRepository flashSaleRepository;
    private final MarketingCacheService cache;
    private final MarketingAuditRecorder audit;
    private final MarketingAfterCommitRunner afterCommit;
    private final MarketingContentInvalidatedPublisher publisher;
    private final CatalogQueryPort catalogQueryPort;

    public AdminFlashSaleService(FlashSaleRepository flashSaleRepository, MarketingCacheService cache,
                                 MarketingAuditRecorder audit, MarketingAfterCommitRunner afterCommit,
                                 MarketingContentInvalidatedPublisher publisher, CatalogQueryPort catalogQueryPort) {
        this.flashSaleRepository = flashSaleRepository;
        this.cache = cache;
        this.audit = audit;
        this.afterCommit = afterCommit;
        this.publisher = publisher;
        this.catalogQueryPort = catalogQueryPort;
    }

    /** E-MKT-17：列表（status 筛选 + product_ids/translations 批查防 N+1） */
    public List<FlashSaleDto> list(String status) {
        // V-MKT-030 status ∈ {all, draft, scheduled, active, ended} 缺省 all
        String statusFilter = (status == null || status.isBlank()) ? "all" : status;
        if (!STATUS_FILTER.contains(statusFilter)) {
            throw MarketingException.fieldValidation("status", "invalid_enum");
        }
        FlashSaleStatus statusEnum = "all".equals(statusFilter) ? null : FlashSaleStatus.of(statusFilter);
        List<FlashSale> sales = flashSaleRepository.listAdmin(statusEnum);
        List<Long> ids = sales.stream().map(FlashSale::getId).toList();
        Map<Long, List<Long>> productIds = flashSaleRepository.listProductIdsByFlashIds(ids);
        Map<Long, List<FlashSaleTranslationDto>> translations = translationsByFlash(ids);
        return sales.stream().map(s -> toDto(s, productIds.getOrDefault(s.getId(), List.of()),
                translations.getOrDefault(s.getId(), List.of()))).toList();
    }

    /** E-MKT-18：创建（TX-MKT-004；flash_sale_lifecycle 初态 draft/scheduled/active） */
    @Transactional
    public FlashSaleDto create(FlashSaleUpsert req) {
        Normalized n = validateUpsert(req, null);
        // STEP-MKT-01 INSERT 三表批插
        FlashSale sale = new FlashSale();
        applyUpsert(sale, n, req);
        flashSaleRepository.insert(sale);
        flashSaleRepository.replaceProducts(sale.getId(), n.productIds());
        flashSaleRepository.replaceTranslations(sale.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-02 审计
        audit.record("创建闪购", n.name(), null);
        // STEP-MKT-03 提交后（active 时）失效 + MQ（draft/scheduled 无消费端可见性变化，不发——SCHED 翻转时再发）
        if (n.status() == FlashSaleStatus.ACTIVE) {
            invalidateAfterCommit();
        }
        return toDto(flashSaleRepository.findById(sale.getId()), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-19：编辑（TX-MKT-005；ended 不可编辑） */
    @Transactional
    public FlashSaleDto update(Long id, FlashSaleUpsert req) {
        // STEP-MKT-01 不存在 → 404703（V-MKT-037 非法 id 同口径由 controller parseId 承载）
        FlashSale existing = flashSaleRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.FLASH_SALE_NOT_FOUND);
        }
        // STEP-MKT-02 状态机 guard：DB ended → 409703（ended 为终态，契约「ended 活动不可编辑」）
        if (existing.getStatus() == FlashSaleStatus.ENDED) {
            throw MarketingException.stateInvalid("ended_not_editable");
        }
        Normalized n = validateUpsert(req, existing);
        boolean hadActiveFace = existing.getStatus() == FlashSaleStatus.ACTIVE;
        // STEP-MKT-03 UPDATE + 子表整单覆盖
        applyUpsert(existing, n, req);
        flashSaleRepository.update(existing);
        flashSaleRepository.replaceProducts(id, n.productIds());
        flashSaleRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-04 审计
        audit.record("编辑闪购", n.name(), null);
        // STEP-MKT-05 提交后（DB 或目标 status 含 active）失效 + MQ
        if (hadActiveFace || n.status() == FlashSaleStatus.ACTIVE) {
            invalidateAfterCommit();
        }
        return toDto(flashSaleRepository.findById(id), n.productIds(), nonNull(req.translations()));
    }

    /** E-MKT-20：删除（TX-MKT-006；仅 draft 可删） */
    @Transactional
    public void delete(Long id) {
        FlashSale existing = flashSaleRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.FLASH_SALE_NOT_FOUND);
        }
        // STEP-MKT-02 guard：仅 draft 可删（scheduled/active/ended 保留运营痕迹）→ 409703
        if (existing.getStatus() != FlashSaleStatus.DRAFT) {
            throw MarketingException.stateInvalid("only_draft_deletable");
        }
        // STEP-MKT-03 物理删除三表 + 审计
        flashSaleRepository.deleteById(id);
        flashSaleRepository.deleteProductsByFlashId(id);
        flashSaleRepository.deleteTranslationsByFlashId(id);
        audit.record("删除闪购", existing.getName(), null);
        // STEP-MKT-04 draft 无消费端可见性，不失效不发 MQ
    }

    private void invalidateAfterCommit() {
        afterCommit.run(() -> {
            cache.invalidateFamily(Family.FLASH);
            publisher.publish(MarketingContentInvalidatedPublisher.TYPE_FLASH_SALE_CHANGED);
        });
    }

    private record Normalized(String name, String discount, FlashSaleStatus status, List<Long> productIds) {
    }

    /** V-MKT-031~036（existing 非空=编辑：不可改入 ended，ended 由 SCHED 专有——V-MKT-037 放宽口径） */
    private Normalized validateUpsert(FlashSaleUpsert req, FlashSale existing) {
        FieldErrors errors = new FieldErrors();
        LocalDateTime now = LocalDateTime.now();
        // V-MKT-031 name 必填 trim 非空 ≤64
        String name = MarketingParams.trimToNull(req.name());
        if (name == null) {
            errors.reject("name", "required");
        } else if (name.length() > 64) {
            errors.reject("name", "too_long");
        }
        // V-MKT-032 discount 必填 ≤32
        String discount = MarketingParams.trimToNull(req.discount());
        if (discount == null) {
            errors.reject("discount", "required");
        } else if (discount.length() > 32) {
            errors.reject("discount", "too_long");
        }
        // V-MKT-033 start_at/end_at 必填且 end_at > start_at（CV-MKT-004）
        if (req.startAt() == null) {
            errors.reject("start_at", "required");
        }
        if (req.endAt() == null) {
            errors.reject("end_at", "required");
        }
        if (req.startAt() != null && req.endAt() != null && !req.endAt().isAfter(req.startAt())) {
            errors.reject("end_at", "before_start");
        }
        // V-MKT-034 status 必填枚举 + 时间窗一致性（CV-MKT-011）；ended 不可作为创建态/改入态
        FlashSaleStatus status = FlashSaleStatus.of(req.status());
        if (status == null) {
            errors.reject("status", "invalid_enum");
        } else {
            switch (status) {
                case SCHEDULED -> {
                    if (req.startAt() == null || !req.startAt().isAfter(now)) {
                        errors.reject("status", "inconsistent_with_window");
                    }
                }
                case ACTIVE -> {
                    boolean inWindow = req.startAt() != null && !req.startAt().isAfter(now)
                            && req.endAt() != null && req.endAt().isAfter(now);
                    if (!inWindow) {
                        errors.reject("status", "inconsistent_with_window");
                    }
                }
                case ENDED -> errors.reject("status", "inconsistent_with_window");
                default -> {
                    // draft 无窗口约束
                }
            }
        }
        // V-MKT-035 product_ids 去重 + catalogQueryPort 存在性校验（不存在 → 422704 fields.product_ids=not_exists）
        List<Long> productIds = List.of();
        if (req.productIds() != null && !req.productIds().isEmpty()) {
            productIds = new ArrayList<>(new LinkedHashSet<>(req.productIds()));
            if (catalogQueryPort.listExistingIds(productIds).size() != productIds.size()) {
                errors.reject("product_ids", "not_exists");
            }
        }
        // V-MKT-036 translations
        validateTranslations(req.translations(), errors);
        errors.throwIfAny();
        return new Normalized(name, discount, status, productIds);
    }

    /** V-MKT-036 translations locale ∈ {es,fr} 不重复；name ≤64 */
    private void validateTranslations(List<FlashSaleTranslationDto> translations, FieldErrors errors) {
        if (translations == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (FlashSaleTranslationDto t : translations) {
            if (t.locale() == null || !MarketingParams.TRANSLATION_LOCALES.contains(t.locale())) {
                errors.reject("translations", "invalid_locale");
            } else if (!seen.add(t.locale())) {
                errors.reject("translations", "duplicate_locale");
            }
            if (t.name() != null && t.name().length() > 64) {
                errors.reject("translations", "name_too_long");
            }
        }
    }

    private void applyUpsert(FlashSale sale, Normalized n, FlashSaleUpsert req) {
        sale.setName(n.name());
        sale.setDiscount(n.discount());
        sale.setStartAt(req.startAt());
        sale.setEndAt(req.endAt());
        sale.setStatus(n.status());
    }

    private List<FlashSaleTranslation> toTranslationRows(List<FlashSaleTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<FlashSaleTranslation> rows = new ArrayList<>(dtos.size());
        for (FlashSaleTranslationDto dto : dtos) {
            FlashSaleTranslation row = new FlashSaleTranslation();
            row.setLocale(dto.locale());
            row.setName(dto.name());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<FlashSaleTranslationDto>> translationsByFlash(List<Long> ids) {
        Map<Long, List<FlashSaleTranslationDto>> map = new HashMap<>();
        for (FlashSaleTranslation row : flashSaleRepository.listTranslationsByFlashIds(ids)) {
            map.computeIfAbsent(row.getFlashSaleId(), k -> new ArrayList<>())
                    .add(new FlashSaleTranslationDto(row.getLocale(), row.getName()));
        }
        return map;
    }

    private List<FlashSaleTranslationDto> nonNull(List<FlashSaleTranslationDto> translations) {
        return translations == null ? List.of() : translations;
    }

    private FlashSaleDto toDto(FlashSale s, List<Long> productIds, List<FlashSaleTranslationDto> translations) {
        return new FlashSaleDto(s.getId(), s.getName(), s.getDiscount(), s.getStartAt(), s.getEndAt(),
                s.getStatus().getKey(), productIds, translations);
    }
}
