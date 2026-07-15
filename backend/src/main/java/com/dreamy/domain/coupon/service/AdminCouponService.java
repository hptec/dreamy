package com.dreamy.domain.coupon.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.coupon.entity.Coupon;
import com.dreamy.domain.coupon.entity.CouponTranslation;
import com.dreamy.domain.coupon.repository.CouponRepository;
import com.dreamy.enums.CouponStatus;
import com.dreamy.dto.AdminMarketingDtos.CouponDto;
import com.dreamy.dto.AdminMarketingDtos.CouponUpsert;
import com.dreamy.dto.MarketingTranslationDtos.CouponTranslationDto;
import com.dreamy.error.MarketingErrorCode;
import com.dreamy.error.MarketingException;
import com.dreamy.infra.MarketingAuditRecorder;
import com.dreamy.support.MarketingFieldErrors;
import com.dreamy.support.MarketingParams;
import com.dreamy.support.MarketingPaginatedSupport;
import huihao.page.Paginated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台优惠券服务（E-MKT-13~16；TX-MKT-001~003；TASK-042 coupon_lifecycle guard）。
 * coupon 无消费端缓存面，因此写操作不创建缓存任务（E-MKT-14 STEP-MKT-04 归因）。
 * L2 TRACE: V-MKT-016~029 / RM-MKT-101~106/110~112。
 */
@Service
public class AdminCouponService {


    private final CouponRepository couponRepository;
    private final MarketingAuditRecorder audit;
    private final Clock clock;

    public AdminCouponService(CouponRepository couponRepository, MarketingAuditRecorder audit, Clock clock) {
        this.couponRepository = couponRepository;
        this.audit = audit;
        this.clock = clock;
    }

    /** E-MKT-13：分页列表（status/search 筛选 + translations 三语 tab 原样） */
    public Paginated<CouponDto> page(Integer page, Integer pageSize, Integer status, String search) {
        MarketingFieldErrors errors = new MarketingFieldErrors();
        int parsedPage = MarketingParams.parsePage(page, errors);
        int parsedPageSize = MarketingParams.parsePageSize(pageSize, errors);
        // V-MKT-017 status ∈ {all,...} 缺省 all
        Integer statusFilter = status;
        if (statusFilter != null && CouponStatus.of(statusFilter) == null) {
            errors.reject("status", "invalid_enum");
        }
        // V-MKT-018 search ≤64（trim 后空视为未提供）
        String parsedSearch = MarketingParams.checkMaxLength(search, 64, "search", errors);
        errors.throwIfAny();

        CouponStatus statusEnum = statusFilter == null ? null : CouponStatus.of(statusFilter);
        Page<Coupon> result = couponRepository.pageAdmin(statusEnum, parsedSearch, parsedPage, parsedPageSize);
        Map<Long, List<CouponTranslationDto>> translations = translationsByCoupon(
                result.getRecords().stream().map(Coupon::getId).toList());
        return MarketingPaginatedSupport.of(result, c -> toDto(c, translations.getOrDefault(c.getId(), List.of())));
    }

    /** E-MKT-14：创建优惠券（TX-MKT-001） */
    @Transactional
    public CouponDto create(CouponUpsert req) {
        CouponUpsertValidator.Normalized n = CouponUpsertValidator.validate(req, null, LocalDateTime.now(clock));
        // STEP-MKT-01 code 唯一性（uk_coupon_code 兜底）→ 409701
        if (couponRepository.existsByCodeExcept(n.code(), null)) {
            throw new MarketingException(MarketingErrorCode.COUPON_CODE_EXISTS);
        }
        // STEP-MKT-02 INSERT coupon（used_count=0 初始化）+ translation 批插
        Coupon coupon = new Coupon();
        applyUpsert(coupon, n, req);
        coupon.setUsedCount(0);
        couponRepository.insert(coupon);
        couponRepository.replaceTranslations(coupon.getId(), toTranslationRows(req.translations()));
        // STEP-MKT-03 审计（事务内）
        audit.record("创建优惠券", n.code(), null);
        // STEP-MKT-04 无缓存失效、无 MQ
        return toDto(coupon, req.translations() == null ? List.of() : req.translations());
    }

    /** E-MKT-15：编辑优惠券（TX-MKT-002） */
    @Transactional
    public CouponDto update(Long id, CouponUpsert req) {
        // STEP-MKT-01 不存在 → 404702
        Coupon existing = couponRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.COUPON_NOT_FOUND);
        }
        CouponUpsertValidator.Normalized n = CouponUpsertValidator.validate(req, existing, LocalDateTime.now(clock));
        // STEP-MKT-02 状态机 guard：active/expiring 改 code → 409703（已上线券改码使用户手中券码失效）
        if ((existing.getStatus() == CouponStatus.ACTIVE || existing.getStatus() == CouponStatus.EXPIRING)
                && !existing.getCode().equals(n.code())) {
            throw MarketingException.stateInvalid("code_change_on_live_coupon");
        }
        // STEP-MKT-03 code 变更时查重（排除自身）→ 409701
        if (!existing.getCode().equals(n.code()) && couponRepository.existsByCodeExcept(n.code(), id)) {
            throw new MarketingException(MarketingErrorCode.COUPON_CODE_EXISTS);
        }
        // STEP-MKT-04 UPDATE 主表（SET 不含 used_count——V-MKT-029）+ translation 整单覆盖
        String before = snapshot(existing);
        applyUpsert(existing, n, req);
        couponRepository.update(existing);
        couponRepository.replaceTranslations(id, toTranslationRows(req.translations()));
        // STEP-MKT-05 审计（changes before/after）
        audit.record("编辑优惠券", n.code(), "{\"before\":" + before + ",\"after\":" + snapshot(existing) + "}");
        Coupon reloaded = couponRepository.findById(id);
        return toDto(reloaded, req.translations() == null ? List.of() : req.translations());
    }

    /** E-MKT-16：删除优惠券（TX-MKT-003；coupon_lifecycle draft|expired→deleted） */
    @Transactional
    public void delete(Long id) {
        Coupon existing = couponRepository.findById(id);
        if (existing == null) {
            throw new MarketingException(MarketingErrorCode.COUPON_NOT_FOUND);
        }
        // STEP-MKT-02 guard①：仅 draft/expired 可删 → 409703
        if (existing.getStatus() != CouponStatus.DRAFT && existing.getStatus() != CouponStatus.EXPIRED) {
            throw MarketingException.stateInvalid("status_not_deletable");
        }
        // STEP-MKT-03 guard②：used_count>0 保留对账依据 → 409703
        if (existing.getUsedCount() != null && existing.getUsedCount() > 0) {
            throw MarketingException.stateInvalid("has_redemptions");
        }
        // STEP-MKT-04 物理删除双表 + 审计（先清译文，再删主表）
        couponRepository.deleteTranslationsByCouponId(id);
        couponRepository.deleteById(id);
        audit.record("删除优惠券", existing.getCode(), null);
    }

    private void applyUpsert(Coupon coupon, CouponUpsertValidator.Normalized n, CouponUpsert req) {
        coupon.setCode(n.code());
        coupon.setName(n.name());
        coupon.setType(n.type());
        coupon.setValue(n.value());
        coupon.setMinAmount(n.minAmount());
        coupon.setTotalLimit(n.totalLimit());
        coupon.setStartAt(req.startAt());
        coupon.setEndAt(req.endAt());
        coupon.setStatus(n.status());
        coupon.setDescription(n.description());
    }

    private List<CouponTranslation> toTranslationRows(List<CouponTranslationDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<CouponTranslation> rows = new ArrayList<>(dtos.size());
        for (CouponTranslationDto dto : dtos) {
            CouponTranslation row = new CouponTranslation();
            row.setLocale(dto.locale());
            row.setName(dto.name());
            row.setDescription(dto.description());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, List<CouponTranslationDto>> translationsByCoupon(List<Long> ids) {
        Map<Long, List<CouponTranslationDto>> map = new HashMap<>();
        for (CouponTranslation row : couponRepository.listTranslationsByCouponIds(ids)) {
            map.computeIfAbsent(row.getCouponId(), k -> new ArrayList<>())
                    .add(new CouponTranslationDto(row.getLocale(), row.getName(), row.getDescription()));
        }
        return map;
    }

    private CouponDto toDto(Coupon c, List<CouponTranslationDto> translations) {
        return new CouponDto(c.getId(), c.getCode(), c.getName(), c.getType().getKey(), c.getValue(),
                c.getMinAmount(), c.getTotalLimit(), c.getUsedCount(), c.getStartAt(), c.getEndAt(),
                c.getStatus().getKey(), c.getDescription(), translations);
    }

    private String snapshot(Coupon c) {
        return "{\"code\":\"" + c.getCode() + "\",\"status\":\"" + c.getStatus().getKey()
                + "\",\"value\":\"" + c.getValue() + "\"}";
    }
}
