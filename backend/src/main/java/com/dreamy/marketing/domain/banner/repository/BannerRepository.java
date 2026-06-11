package com.dreamy.marketing.domain.banner.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.marketing.domain.banner.entity.Banner;
import com.dreamy.marketing.domain.banner.entity.BannerTranslation;
import com.dreamy.marketing.domain.enums.BannerPosition;
import com.dreamy.marketing.domain.enums.ContentStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Banner 仓储（RM-MKT-001~012）。
 * L2 TRACE: marketing-data-detail §2 BannerRepository / BannerTranslationRepository。
 */
@Repository
public class BannerRepository {

    private final BannerMapper bannerMapper;
    private final BannerTranslationMapper translationMapper;

    public BannerRepository(BannerMapper bannerMapper, BannerTranslationMapper translationMapper) {
        this.bannerMapper = bannerMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-MKT-001 listStoreActive —— status='published' AND 窗口谓词（E-MKT-01 / DEC-MKT-2）ORDER BY sort, id */
    public List<Banner> listStoreActive(BannerPosition position, LocalDateTime now) {
        LambdaQueryWrapper<Banner> qw = new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, ContentStatus.PUBLISHED)
                .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, now))
                .and(w -> w.isNull(Banner::getEndTime).or().gt(Banner::getEndTime, now));
        if (position != null) {
            qw.eq(Banner::getPosition, position);
        }
        return bannerMapper.selectList(qw.orderByAsc(Banner::getSort).orderByAsc(Banner::getId));
    }

    /** RM-MKT-002 listAdmin —— ORDER BY sort, id（E-MKT-21） */
    public List<Banner> listAdmin(BannerPosition position) {
        LambdaQueryWrapper<Banner> qw = new LambdaQueryWrapper<>();
        if (position != null) {
            qw.eq(Banner::getPosition, position);
        }
        return bannerMapper.selectList(qw.orderByAsc(Banner::getSort).orderByAsc(Banner::getId));
    }

    /** RM-MKT-003 findById */
    public Banner findById(Long id) {
        return id == null ? null : bannerMapper.selectById(id);
    }

    /** RM-MKT-004 insert */
    public void insert(Banner banner) {
        bannerMapper.insert(banner);
    }

    /** RM-MKT-005 update（SET 不含 clicks——V-MKT-046 只读列写权限约束） */
    public void update(Banner banner) {
        bannerMapper.update(null, new LambdaUpdateWrapper<Banner>()
                .eq(Banner::getId, banner.getId())
                .set(Banner::getName, banner.getName())
                .set(Banner::getImageUrl, banner.getImageUrl())
                .set(Banner::getPosition, banner.getPosition())
                .set(Banner::getStartTime, banner.getStartTime())
                .set(Banner::getEndTime, banner.getEndTime())
                .set(Banner::getStatus, banner.getStatus())
                .set(Banner::getSort, banner.getSort())
                .set(Banner::getTitle, banner.getTitle())
                .set(Banner::getSubtitle, banner.getSubtitle())
                .set(Banner::getCtaText, banner.getCtaText()));
    }

    /** RM-MKT-006 deleteById */
    public void deleteById(Long id) {
        bannerMapper.deleteById(id);
    }

    /** RM-MKT-007 updateStatus */
    public void updateStatus(Long id, ContentStatus status) {
        bannerMapper.update(null, new LambdaUpdateWrapper<Banner>()
                .eq(Banner::getId, id)
                .set(Banner::getStatus, status));
    }

    /** RM-MKT-008 listCrossedWindow —— published 且 start_time/end_time ∈ (lastTick, now]（SCHED-MKT-01④ 窗口边界穿越检测） */
    public List<Banner> listCrossedWindow(LocalDateTime lastTick, LocalDateTime now) {
        return bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                .eq(Banner::getStatus, ContentStatus.PUBLISHED)
                .and(w -> w
                        .and(s -> s.gt(Banner::getStartTime, lastTick).le(Banner::getStartTime, now))
                        .or(e -> e.gt(Banner::getEndTime, lastTick).le(Banner::getEndTime, now))));
    }

    /** RM-MKT-010 listTranslationsByBannerIds —— 批查防 N+1（NP-MKT-001） */
    public List<BannerTranslation> listTranslationsByBannerIds(Collection<Long> bannerIds) {
        if (bannerIds == null || bannerIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<BannerTranslation>()
                .in(BannerTranslation::getBannerId, bannerIds));
    }

    /** RM-MKT-011 replaceTranslations —— DELETE+批量 INSERT（整单覆盖，事务内调用） */
    public void replaceTranslations(Long bannerId, List<BannerTranslation> rows) {
        deleteTranslationsByBannerId(bannerId);
        if (rows != null) {
            for (BannerTranslation row : rows) {
                row.setBannerId(bannerId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-012 deleteByBannerId */
    public void deleteTranslationsByBannerId(Long bannerId) {
        translationMapper.delete(new LambdaQueryWrapper<BannerTranslation>()
                .eq(BannerTranslation::getBannerId, bannerId));
    }
}
