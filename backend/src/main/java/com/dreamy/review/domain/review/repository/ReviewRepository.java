package com.dreamy.review.domain.review.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.review.domain.enums.ReviewSort;
import com.dreamy.review.domain.enums.ReviewStatus;
import com.dreamy.review.domain.review.entity.Review;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价仓储（RM-REV-001~015；015 为 L3 修复轮新增「我的评价」F-049）。
 * L2 TRACE: review-data-detail §2 ReviewRepository / IDX-REV-001~003。
 */
@Repository
public class ReviewRepository {

    private final ReviewMapper reviewMapper;

    public ReviewRepository(ReviewMapper reviewMapper) {
        this.reviewMapper = reviewMapper;
    }

    /** RM-REV-001 pageApprovedByProduct —— approved 过滤 + 四枚举排序（E-REV-01 STEP-REV-02；IDX-REV-002） */
    public Page<Review> pageApprovedByProduct(Long productId, ReviewSort sort, int page, int pageSize) {
        LambdaQueryWrapper<Review> qw = new LambdaQueryWrapper<Review>()
                .eq(Review::getProductId, productId)
                .eq(Review::getStatus, ReviewStatus.APPROVED);
        switch (sort) {
            case NEWEST -> qw.orderByDesc(Review::getSubmittedAt);
            case RATING_DESC -> qw.orderByDesc(Review::getRating).orderByDesc(Review::getSubmittedAt);
            case RATING_ASC -> qw.orderByAsc(Review::getRating).orderByDesc(Review::getSubmittedAt);
            case FEATURED_FIRST -> qw.orderByDesc(Review::getFeatured).orderByDesc(Review::getSubmittedAt);
        }
        return reviewMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /**
     * RM-REV-002 aggregateApproved 数据源 —— 单条 GROUP BY rating（NP-REV-002 禁止逐星级 COUNT×5）。
     * 返回 rating → count；汇总（avg HALF_UP 2 位 / breakdown 全档）由 RatingSummary.fromCounts 承载，
     * 与 ReviewQueryPort.approvedRatingSummary 同源（catalog EVT-CAT-002 回查口径强一致）。
     */
    public Map<Integer, Long> countApprovedByRating(Long productId) {
        QueryWrapper<Review> qw = new QueryWrapper<Review>()
                .select("rating", "COUNT(*) AS cnt")
                .eq("product_id", productId)
                .eq("status", ReviewStatus.APPROVED.getKey())
                .groupBy("rating");
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : reviewMapper.selectMaps(qw)) {
            Object rating = row.get("rating");
            Object cnt = row.get("cnt");
            if (rating instanceof Number r && cnt instanceof Number c) {
                counts.put(r.intValue(), c.longValue());
            }
        }
        return counts;
    }

    /** RM-REV-003 existsByUserAndProduct —— 409801 预检（uk_review_user_product 兜底） */
    public boolean existsByUserAndProduct(Long userId, Long productId) {
        return reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .eq(Review::getProductId, productId)) > 0;
    }

    /**
     * RM-REV-015 pageByUser —— 我的评价（E-REV-16 / F-049，L3 修复轮新增）：
     * 按 user_id 过滤（BE-DIM-6：customer_id=JWT subject，本人全状态 pending|approved|rejected 可见），
     * ORDER BY submitted_at DESC（uk_review_user_product 左前缀 user_id 承载索引面）。
     */
    public Page<Review> pageByUser(Long userId, int page, int pageSize) {
        return reviewMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getSubmittedAt));
    }

    /** RM-REV-004 insert —— 唯一索引冲突向上抛（调用方映射 409801 并发双提交） */
    public void insert(Review review) {
        reviewMapper.insert(review);
    }

    /** RM-REV-005 findById —— 404801 */
    public Review findById(Long id) {
        return id == null ? null : reviewMapper.selectById(id);
    }

    /** RM-REV-006 pageByAdminFilter —— search 双 LIKE（customer_name/content）；ORDER BY submitted_at DESC（E-REV-06） */
    public Page<Review> pageByAdminFilter(ReviewStatus status, Integer rating, Boolean featured,
                                          Long productId, String search, int page, int pageSize) {
        LambdaQueryWrapper<Review> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Review::getStatus, status);
        }
        if (rating != null) {
            qw.eq(Review::getRating, rating);
        }
        if (featured != null) {
            qw.eq(Review::getFeatured, featured);
        }
        if (productId != null) {
            qw.eq(Review::getProductId, productId);
        }
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            qw.and(w -> w.like(Review::getCustomerName, s).or().like(Review::getContent, s));
        }
        qw.orderByDesc(Review::getSubmittedAt);
        return reviewMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** RM-REV-007 countPending —— pending_count 角标（不随筛选变化；IDX-REV-003） */
    public long countPending() {
        return reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getStatus, ReviewStatus.PENDING));
    }

    /**
     * RM-REV-008 casModerate —— CAS 状态机 guard（E-REV-07；affected=0 → 409802；
     * reject 强制 featured=0，state-machine guard / CV-REV-007）。
     */
    public int casModerate(Long id, ReviewStatus toStatus) {
        LambdaUpdateWrapper<Review> uw = new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getStatus, ReviewStatus.PENDING)
                .set(Review::getStatus, toStatus);
        if (toStatus == ReviewStatus.REJECTED) {
            uw.set(Review::getFeatured, false);
        }
        return reviewMapper.update(null, uw);
    }

    /** RM-REV-009 casSetFeatured —— 仅 approved 且未精选可置 1（E-REV-08；affected=0 且非幂等 → 409803） */
    public int casSetFeatured(Long id) {
        return reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getStatus, ReviewStatus.APPROVED)
                .eq(Review::getFeatured, false)
                .set(Review::getFeatured, true));
    }

    /** RM-REV-010 unsetFeatured —— 任意状态允许取消精选 */
    public int unsetFeatured(Long id) {
        return reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getFeatured, true)
                .set(Review::getFeatured, false));
    }

    /** RM-REV-011 listByIds —— 批量分拣（E-REV-09 STEP-REV-01） */
    public List<Review> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return reviewMapper.selectList(new LambdaQueryWrapper<Review>().in(Review::getId, ids));
    }

    /**
     * RM-REV-012 casBatchTransit —— WHERE id AND status=读取态 逐条 CAS（E-REV-09 STEP-REV-03，
     * 并发被他人抢先 affected=0 转 skipped）；reject 强制 featured=0。
     */
    public int casBatchTransit(Long id, ReviewStatus expectStatus, ReviewStatus toStatus) {
        LambdaUpdateWrapper<Review> uw = new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .eq(Review::getStatus, expectStatus)
                .set(Review::getStatus, toStatus);
        if (toStatus == ReviewStatus.REJECTED) {
            uw.set(Review::getFeatured, false);
        }
        return reviewMapper.update(null, uw);
    }

    /** RM-REV-013 updateReply —— PUT 回复 UPSERT 语义（E-REV-10） */
    public void updateReply(Long id, String author, String content, LocalDateTime time) {
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .set(Review::getReplyAuthor, author)
                .set(Review::getReplyContent, content)
                .set(Review::getReplyTime, time));
    }

    /** RM-REV-014 clearReply —— 三字段置 NULL（E-REV-11） */
    public void clearReply(Long id) {
        reviewMapper.update(null, new LambdaUpdateWrapper<Review>()
                .eq(Review::getId, id)
                .set(Review::getReplyAuthor, null)
                .set(Review::getReplyContent, null)
                .set(Review::getReplyTime, null));
    }

    /** 种子幂等判定（review 表非空即跳过，决策 21） */
    public long countAll() {
        return reviewMapper.selectCount(null);
    }
}
