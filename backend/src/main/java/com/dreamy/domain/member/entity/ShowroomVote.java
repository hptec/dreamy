package com.dreamy.domain.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.VoteValue;
import com.dreamy.domain.member.consts.ShowroomVoteDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 showroom_vote（款式投票，F-069）。
 * - uk_sv_member_item：member_id+showroom_item_id 唯一（PUT 幂等 UPSERT 承载，CV-SHR-005）。
 * - member_id 仅取鉴权主体解析结果，不接收请求体（CV-SHR-006，bs-727~730 不可达）。
 * L2 TRACE: showroom-data-detail §1.2/§9 DDL-4 / IDX-SHR-008/009 / SHR-IMPL-ENTITY。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "showroom_vote", comment = "款式投票（member+item 唯一，UPSERT 幂等）", indexes = {
        @Index(name = "uk_sv_member_item", columns = {"member_id", "showroom_item_id"}, unique = true, local = false),
        @Index(name = "idx_sv_item", columns = {"showroom_item_id"}, unique = false, local = false)
})
@TableName(value = "showroom_vote", autoResultMap = true)
public class ShowroomVote extends LongAuditableEntity {

    @Column(name = ShowroomVoteDBConst.SHOWROOM_ITEM_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom_item.id'")
    private Long showroomItemId;

    @Column(name = ShowroomVoteDBConst.MEMBER_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom_member.id（鉴权主体解析，不接收请求体）'")
    private Long memberId;

    @Column(name = ShowroomVoteDBConst.VOTE,
            definition = "varchar(8) NOT NULL COMMENT 'like|dislike（重复投票覆盖原票）'")
    private VoteValue vote;
}
