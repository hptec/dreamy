package com.dreamy.review.domain.review.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.review.domain.review.entity.ReviewImage;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 买家秀图片仓储（RM-REV-020~023）。
 * L2 TRACE: review-data-detail §2 ReviewImageRepository / IDX-REV-004。
 */
@Repository
public class ReviewImageRepository {

    private final ReviewImageMapper imageMapper;

    public ReviewImageRepository(ReviewImageMapper imageMapper) {
        this.imageMapper = imageMapper;
    }

    /**
     * RM-REV-020 listByReviewIds —— 单次 IN 批查防 N+1（NP-REV-001）；
     * store 传 excludeRejected=true（AND rejected=0，js_guard 前台排除驳回图）、admin 传 false 全量。
     */
    public List<ReviewImage> listByReviewIds(Collection<Long> reviewIds, boolean excludeRejected) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ReviewImage> qw = new LambdaQueryWrapper<ReviewImage>()
                .in(ReviewImage::getReviewId, reviewIds);
        if (excludeRejected) {
            qw.eq(ReviewImage::getRejected, false);
        }
        qw.orderByAsc(ReviewImage::getId);
        return imageMapper.selectList(qw);
    }

    /** RM-REV-021 batchInsert —— TX-REV-001 子表批插（rejected=0） */
    public void batchInsert(List<ReviewImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (ReviewImage image : images) {
            imageMapper.insert(image);
        }
    }

    /** RM-REV-022 findByIdAndReviewId —— 归属校验（404803，E-REV-12） */
    public ReviewImage findByIdAndReviewId(Long imageId, Long reviewId) {
        if (imageId == null || reviewId == null) {
            return null;
        }
        return imageMapper.selectOne(new LambdaQueryWrapper<ReviewImage>()
                .eq(ReviewImage::getId, imageId)
                .eq(ReviewImage::getReviewId, reviewId));
    }

    /** RM-REV-023 updateRejected —— shown↔rejected 翻转（review_image_visibility 双向） */
    public void updateRejected(Long id, boolean rejected) {
        imageMapper.update(null, new LambdaUpdateWrapper<ReviewImage>()
                .eq(ReviewImage::getId, id)
                .set(ReviewImage::getRejected, rejected));
    }
}
