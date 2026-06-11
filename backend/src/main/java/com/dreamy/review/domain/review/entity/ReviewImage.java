package com.dreamy.review.domain.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.review.domain.review.consts.ReviewImageDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 review_image（评价买家秀图片，决策 9 预签名直传；可单独驳回/恢复——review_image_visibility）。
 * 仅经 TX-REV-001 聚合根事务写入（API 面无独立插图入口，bs-703 由事务边界天然保证）。
 * L2 TRACE: review-data-detail §1.2/§9 DDL-2 / IDX-REV-004 / TASK-028 / TASK-048。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "review_image", comment = "评价买家秀图片（可单独驳回/恢复）", indexes = {
        @Index(name = "idx_ri_review", columns = {"review_id"}, unique = false, local = false)
})
@TableName(value = "review_image", autoResultMap = true)
public class ReviewImage extends LongAuditableEntity {

    @Column(name = ReviewImageDBConst.REVIEW_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 review.id（仅经 TX-REV-001 聚合根写入）'")
    private Long reviewId;

    @Column(name = ReviewImageDBConst.URL,
            definition = "varchar(512) NOT NULL COMMENT '预签名上传 public_url（review/ 前缀，CV-REV-008）'")
    private String url;

    @Column(name = ReviewImageDBConst.REJECTED,
            definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '驳回标记；1=前台不展示（review_image_visibility shown/rejected）'")
    private Boolean rejected;
}
