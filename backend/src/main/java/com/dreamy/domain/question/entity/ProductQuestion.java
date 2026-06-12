package com.dreamy.domain.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.consts.ProductQuestionDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 product_question（商品 Q&A：消费端提问，后台回答并控制前台可见性）。
 * - user_id 为设计派生列（er-diagram 无此字段；BE-DIM-6 提交者强关联，不出契约 DTO）。
 * - answer NULL = unanswered（question_answer_flow 初始态）；首次回答自动置 visible（E-REV-14）。
 * - 前台展示双条件口径（CV-REV-009）：visible='visible' AND answer IS NOT NULL。
 * L2 TRACE: review-data-detail §1.2/§9 DDL-3 / IDX-REV-005 / TASK-029 / TASK-049。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product_question", comment = "商品 Q&A（后台回答并控制前台可见性）", indexes = {
        @Index(name = "idx_pq_product_visible", columns = {"product_id", "visible", "asked_at"},
                unique = false, local = false)
})
@TableName(value = "product_question", autoResultMap = true)
public class ProductQuestion extends LongAuditableEntity {

    @Column(name = ProductQuestionDBConst.PRODUCT_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = ProductQuestionDBConst.USER_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 user.id（设计派生列：BE-DIM-6 提交者强关联，不出契约 DTO）'")
    private Long userId;

    @Column(name = ProductQuestionDBConst.ASKER,
            definition = "varchar(64) NULL COMMENT '提问者姓名快照（store 输出脱敏，CV-REV-010 不可变）'")
    private String asker;

    @Column(name = ProductQuestionDBConst.QUESTION,
            definition = "varchar(1000) NOT NULL COMMENT '提问内容 trim 1..1000'")
    private String question;

    @Column(name = ProductQuestionDBConst.ASKED_AT,
            definition = "datetime(3) NOT NULL COMMENT '提问时间（业务时间，列表排序键）'")
    private LocalDateTime askedAt;

    @Column(name = ProductQuestionDBConst.ANSWER,
            definition = "varchar(2000) NULL COMMENT '官方回答；NULL=unanswered（question_answer_flow）'")
    private String answer;

    @Column(name = ProductQuestionDBConst.ANSWER_TIME,
            definition = "datetime(3) NULL COMMENT '回答时间（服务端生成）'")
    private LocalDateTime answerTime;

    @Column(name = ProductQuestionDBConst.VISIBLE,
            definition = "tinyint NOT NULL DEFAULT 2 COMMENT '可见性：1=显示 2=隐藏；首次回答自动置显示（E-REV-14）'")
    private QuestionVisibility visible;
}
