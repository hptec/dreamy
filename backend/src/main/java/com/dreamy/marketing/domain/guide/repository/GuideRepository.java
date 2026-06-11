package com.dreamy.marketing.domain.guide.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.marketing.domain.enums.PublishStatus;
import com.dreamy.marketing.domain.guide.entity.Guide;
import com.dreamy.marketing.domain.guide.entity.GuideTranslation;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 指南仓储（RM-MKT-080~089）。
 * L2 TRACE: marketing-data-detail §2 GuideRepository / GuideTranslationRepository。
 */
@Repository
public class GuideRepository {

    private final GuideMapper guideMapper;
    private final GuideTranslationMapper translationMapper;

    public GuideRepository(GuideMapper guideMapper, GuideTranslationMapper translationMapper) {
        this.guideMapper = guideMapper;
        this.translationMapper = translationMapper;
    }

    /** RM-MKT-080 listStorePublished —— ORDER BY phase ASC, id ASC（E-MKT-08，phase 字典序即阶段序） */
    public List<Guide> listStorePublished() {
        return guideMapper.selectList(new LambdaQueryWrapper<Guide>()
                .eq(Guide::getStatus, PublishStatus.PUBLISHED)
                .orderByAsc(Guide::getPhase)
                .orderByAsc(Guide::getId));
    }

    /** RM-MKT-081 listAdmin */
    public List<Guide> listAdmin(PublishStatus status) {
        LambdaQueryWrapper<Guide> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Guide::getStatus, status);
        }
        return guideMapper.selectList(qw.orderByAsc(Guide::getPhase).orderByAsc(Guide::getId));
    }

    /** RM-MKT-082 findById */
    public Guide findById(Long id) {
        return id == null ? null : guideMapper.selectById(id);
    }

    /** RM-MKT-083 insert */
    public void insert(Guide guide) {
        guideMapper.insert(guide);
    }

    /** RM-MKT-084 update */
    public void update(Guide guide) {
        guideMapper.updateById(guide);
    }

    /** RM-MKT-085 deleteById */
    public void deleteById(Long id) {
        guideMapper.deleteById(id);
    }

    /** RM-MKT-086 updateStatus */
    public void updateStatus(Long id, PublishStatus status) {
        guideMapper.update(null, new LambdaUpdateWrapper<Guide>()
                .eq(Guide::getId, id)
                .set(Guide::getStatus, status));
    }

    /** RM-MKT-087 listTranslationsByGuideIds —— 批查防 N+1（NP-MKT-001） */
    public List<GuideTranslation> listTranslationsByGuideIds(Collection<Long> guideIds) {
        if (guideIds == null || guideIds.isEmpty()) {
            return List.of();
        }
        return translationMapper.selectList(new LambdaQueryWrapper<GuideTranslation>()
                .in(GuideTranslation::getGuideId, guideIds));
    }

    /** RM-MKT-088 replaceTranslations —— DELETE+批量 INSERT（整单覆盖） */
    public void replaceTranslations(Long guideId, List<GuideTranslation> rows) {
        deleteTranslationsByGuideId(guideId);
        if (rows != null) {
            for (GuideTranslation row : rows) {
                row.setGuideId(guideId);
                translationMapper.insert(row);
            }
        }
    }

    /** RM-MKT-089 deleteTransByGuideId */
    public void deleteTranslationsByGuideId(Long guideId) {
        translationMapper.delete(new LambdaQueryWrapper<GuideTranslation>()
                .eq(GuideTranslation::getGuideId, guideId));
    }
}
