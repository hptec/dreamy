package com.dreamy.review.domain.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.review.domain.enums.ReviewStatus;
import com.dreamy.review.domain.review.consts.ReviewDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 review（商品评价：消费端提交/后台审核/精选/官方回复，ALIGN-014 / s-756 / s-762）。
 * - uk_review_user_product：同用户同商品唯一（409801 并发兜底，IDX-REV-001）。
 * - 官方回复三字段同表内嵌（reply 一对一无独立生命周期，不拆表）。
 * - featured 不变量（CV-REV-007）：status != approved ⇒ featured = 0，全部写路径维护。
 * - submitted_at 为业务时间（写入时刻显式赋值），与基类 created_at 语义分离。
 * L2 TRACE: review-data-detail §1.2/§9 DDL-1 / IDX-REV-001~003 / TASK-027 / TASK-047 review_moderation。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "review", comment = "商品评价（消费端提交/后台审核/精选/官方回复）", indexes = {
        @Index(name = "uk_review_user_product", columns = {"user_id", "product_id"}, unique = true, local = false),
        @Index(name = "idx_review_product_status", columns = {"product_id", "status", "featured", "submitted_at"},
                unique = false, local = false),
        @Index(name = "idx_review_status_submitted", columns = {"status", "submitted_at"},
                unique = false, local = false)
})
@TableName(value = "review", autoResultMap = true)
public class Review extends LongAuditableEntity {

    @Column(name = ReviewDBConst.PRODUCT_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验，CV-REV-005）'")
    private Long productId;

    @Column(name = ReviewDBConst.USER_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 user.id（JWT subject，BE-DIM-6 强隔离）'")
    private Long userId;

    @Column(name = ReviewDBConst.CUSTOMER_NAME,
            definition = "varchar(64) NULL COMMENT '提交时用户姓名快照（store 输出脱敏 MAP-REV-001，CV-REV-010 不可变）'")
    private String customerName;

    @Column(name = ReviewDBConst.RATING,
            definition = "tinyint NOT NULL COMMENT '评分 1..5（应用层校验 CV-REV-001）'")
    private Integer rating;

    @Column(name = ReviewDBConst.CONTENT,
            definition = "text NULL COMMENT '评价内容 <=5000，trim 后空存 NULL；不做多语翻译'")
    private String content;

    @Column(name = ReviewDBConst.STATUS,
            definition = "varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|approved|rejected（review_moderation）'")
    private ReviewStatus status;

    @Column(name = ReviewDBConst.FEATURED,
            definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '精选；不变量 status!=approved => 0（CV-REV-007）'")
    private Boolean featured;

    @Column(name = ReviewDBConst.SUBMITTED_AT,
            definition = "datetime(3) NOT NULL COMMENT '提交时间（业务时间，列表排序键）'")
    private LocalDateTime submittedAt;

    @Column(name = ReviewDBConst.REPLY_AUTHOR,
            definition = "varchar(64) NULL COMMENT '官方回复署名（缺省 \"Dreamy Team\"，配置化 dreamy.review.reply-author）'")
    private String replyAuthor;

    @Column(name = ReviewDBConst.REPLY_CONTENT,
            definition = "varchar(2000) NULL COMMENT '官方回复内容，trim 非空（CV-REV-006）'")
    private String replyContent;

    @Column(name = ReviewDBConst.REPLY_TIME,
            definition = "datetime(3) NULL COMMENT '官方回复时间（服务端生成）'")
    private LocalDateTime replyTime;
}
