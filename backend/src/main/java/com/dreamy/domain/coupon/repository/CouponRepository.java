package com.dreamy.domain.coupon.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.domain.coupon.entity.Coupon;
import com.dreamy.domain.coupon.entity.CouponTranslation;
import com.dreamy.enums.CouponStatus;
import com.dreamy.support.PromoWindow;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 优惠券仓储（RM-MKT-100~112）。
 * RM-MKT-107/108 为券核销 CAS 的权威定义点（与 trading RM-TRD-112/113 同 SQL 文本）。
 * L2 TRACE: marketing-data-detail §2 CouponRepository / CouponTranslationRepository / EC-MKT-001。
 */
@Repository
public class CouponRepository {

    private final CouponMapper couponMapper;
    private final CouponTranslationMapper translationMapper;

    public CouponRepository(CouponMapper couponMapper, CouponTranslationMapper translationMapper) {
        this.couponMapper = couponMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-MKT-100 findByCode —— uk_coupon_code 点查（E-MKT-10 / SVC-MKT-01；调用方先大写归一 CV-MKT-008） */
    public Coupon findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return couponMapper.selectOne(new LambdaQueryWrapper<Coupon>().eq(Coupon::getCode, code));
    }

    /** RM-MKT-101 pageAdmin —— code/name LIKE（IDX-MKT-002）ORDER BY id DESC */
    public Page<Coupon> pageAdmin(CouponStatus status, String search, int page, int pageSize) {
        LambdaQueryWrapper<Coupon> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Coupon::getStatus, status);
        }
        if (search != null) {
            qw.and(w -> w.like(Coupon::getCode, search).or().like(Coupon::getName, search));
        }
        qw.orderByDesc(Coupon::getId);
        return couponMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-MKT-102 findById */
    public Coupon findById(Long id) {
        return id == null ? null : couponMapper.selectById(id);
    }

    /** RM-MKT-103 existsByCodeExcept —— 409701 */
    public boolean existsByCodeExcept(String code, Long exceptId) {
        LambdaQueryWrapper<Coupon> qw = new LambdaQueryWrapper<Coupon>().eq(Coupon::getCode, code);
        if (exceptId != null) {
            qw.ne(Coupon::getId, exceptId);
        }
        return couponMapper.selectCount(qw) > 0;
    }

    /** RM-MKT-104 insert */
    public void insert(Coupon coupon) {
        couponMapper.insert(coupon);
    }

    /** RM-MKT-105 update（SET 不含 used_count——V-MKT-029 只读列写权限约束） */
    public void update(Coupon coupon) {
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, coupon.getId())
                .set(Coupon::getCode, coupon.getCode())
                .set(Coupon::getName, coupon.getName())
                .set(Coupon::getType, coupon.getType())
                .set(Coupon::getValue, coupon.getValue())
                .set(Coupon::getMinAmount, coupon.getMinAmount())
                .set(Coupon::getTotalLimit, coupon.getTotalLimit())
                .set(Coupon::getStartAt, coupon.getStartAt())
                .set(Coupon::getEndAt, coupon.getEndAt())
                .set(Coupon::getStatus, coupon.getStatus())
                .set(Coupon::getDescription, coupon.getDescription()));
    }

    /** RM-MKT-106 deleteById */
    public void deleteById(Long id) {
        couponMapper.deleteById(id);
    }

    /**
     * RM-MKT-107 redeemCas —— `UPDATE coupon SET used_count=used_count+1 WHERE id=? AND used_count<total_limit`
     * （与 trading RM-TRD-112 同 SQL，本域为权威定义点；affected=0 → 业务性耗尽 422703，不重试——EC-MKT-001）。
     */
    public int redeemCas(Long couponId) {
        return couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .apply("used_count < total_limit")
                .setSql("used_count = used_count + 1"));
    }

    /** RM-MKT-108 rollbackRedeem —— `UPDATE coupon SET used_count=GREATEST(used_count-1,0) WHERE id=?`（RM-TRD-113 一致；CV-MKT-010 防负） */
    public void rollbackRedeem(Long couponId) {
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .setSql("used_count = GREATEST(used_count - 1, 0)"));
    }

    /**
     * RM-MKT-109 flipStatusByWindow —— SCHED-MKT-01② 三段翻转（scheduled→active / active→expiring /
     * active|expiring→expired，目标态判定收敛于 PromoWindow 纯函数）；逐行 status CAS 更新
     * （WHERE status=旧值，防与管理端编辑并发竞争）；返回受影响 id（审计日志用，不发 MQ——DEC-MKT-3）。
     */
    public List<Long> flipStatusByWindow(LocalDateTime now, Duration expiringThreshold) {
        List<Coupon> candidates = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .in(Coupon::getStatus, CouponStatus.SCHEDULED, CouponStatus.ACTIVE, CouponStatus.EXPIRING));
        List<Long> flipped = new ArrayList<>();
        for (Coupon coupon : candidates) {
            CouponStatus target = PromoWindow.couponTarget(coupon.getStatus(), coupon.getStartAt(),
                    coupon.getEndAt(), now, expiringThreshold);
            if (target == null || target == coupon.getStatus()) {
                continue;
            }
            int affected = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                    .eq(Coupon::getId, coupon.getId())
                    .eq(Coupon::getStatus, coupon.getStatus())
                    .set(Coupon::getStatus, target));
            if (affected > 0) {
                flipped.add(coupon.getId());
            }
        }
        return flipped;
    }

    /** RM-MKT-110 listTranslationsByCouponIds —— 批查防 N+1（NP-MKT-001） */
    public List<CouponTranslation> listTranslationsByCouponIds(Collection<Long> couponIds) {
        if (couponIds == null || couponIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<CouponTranslation>()
                .in(CouponTranslation::getCouponId, couponIds));
    }

    /** RM-MKT-111 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long couponId, List<CouponTranslation> rows) {
        deleteTranslationsByCouponId(couponId);
        if (rows != null) {
            for (CouponTranslation row : rows) {
                row.setCouponId(couponId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-112 deleteTransByCouponId */
    public void deleteTranslationsByCouponId(Long couponId) {
        translationMapper.delete(new LambdaQueryWrapper<CouponTranslation>()
                .eq(CouponTranslation::getCouponId, couponId));
    }
}
