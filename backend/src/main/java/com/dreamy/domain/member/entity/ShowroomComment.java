package com.dreamy.domain.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.member.consts.ShowroomCommentDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 showroom_comment（款式留言，F-069，提交即可见不审核）。
 * - nickname 联 showroom_member 派生不冗余（MAP-SHR-005：昵称即身份不可改）。
 * - created_at 直接复用基类审计列作为业务时间（契约 ShowroomComment.created_at，
 *   无独立业务时间语义，showroom-data-detail §1.1）。
 * L2 TRACE: showroom-data-detail §1.2/§9 DDL-5 / IDX-SHR-010 / SHR-IMPL-ENTITY。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "showroom_comment", comment = "款式留言（带昵称展示，提交即可见不审核）", indexes = {
        @Index(name = "idx_sc_item", columns = {"showroom_item_id", "created_at"}, unique = false, local = false)
})
@TableName(value = "showroom_comment", autoResultMap = true)
public class ShowroomComment extends LongAuditableEntity {

    @Column(name = ShowroomCommentDBConst.SHOWROOM_ITEM_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom_item.id'")
    private Long showroomItemId;

    @Column(name = ShowroomCommentDBConst.MEMBER_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom_member.id（nickname 联表派生展示）'")
    private Long memberId;

    @Column(name = ShowroomCommentDBConst.CONTENT,
            definition = "varchar(500) NOT NULL COMMENT '留言 trim 1..500'")
    private String content;
}
