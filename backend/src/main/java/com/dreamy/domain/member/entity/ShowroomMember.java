package com.dreamy.domain.member.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.enums.AssignStatus;
import com.dreamy.domain.member.consts.ShowroomMemberDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 showroom_member（成员：免注册访客凭邀请 token+昵称参与；指派/提醒/下单状态机，F-068/F-070）。
 * - uk_sm_room_nickname：同房昵称唯一（投票/留言去重身份，409101；并发兜底 CV-SHR-004）。
 * - assign_status：showroom_member_assignment 状态机，仅 CAS 推进（RM-SHR-035/036/037，CV-SHR-002）。
 * - linked_customer_id：访客登录后绑定回填（决策 20.2，E-SHR-07 STEP-SHR-05 / EVT-SHR-003 定位前提）。
 * - 受保护昵称（CV-SHR-009）：linked_customer_id 非空不可被匿名复用。
 * L2 TRACE: showroom-data-detail §1.2/§9 DDL-3 / IDX-SHR-005/006/007 / SHR-IMPL-ENTITY。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "showroom_member", comment = "Showroom 成员（访客凭邀请 token+昵称参与；指派/提醒/下单状态机）", indexes = {
        @Index(name = "uk_sm_room_nickname", columns = {"showroom_id", "nickname"}, unique = true, local = false),
        @Index(name = "idx_sm_linked", columns = {"linked_customer_id"}, unique = false, local = false),
        @Index(name = "idx_sm_assigned_item", columns = {"assigned_item_id"}, unique = false, local = false)
})
@TableName(value = "showroom_member", autoResultMap = true)
public class ShowroomMember extends LongAuditableEntity {

    @Column(name = ShowroomMemberDBConst.SHOWROOM_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom.id'")
    private Long showroomId;

    @Column(name = ShowroomMemberDBConst.NICKNAME,
            definition = "varchar(32) NOT NULL COMMENT '昵称 trim 1..32，同房唯一（去重身份 409101）'")
    private String nickname;

    @Column(name = ShowroomMemberDBConst.EMAIL,
            definition = "varchar(254) NULL COMMENT '提醒邮件收件地址（决策 20.5 新娘指派时填写；仅 owner 视图输出）'")
    private String email;

    @Column(name = ShowroomMemberDBConst.ASSIGNED_ITEM_ID,
            definition = "bigint NULL COMMENT '逻辑外键 showroom_item.id（被指派款式；item 删除时清理 RM-SHR-039/040）'")
    private Long assignedItemId;

    @Column(name = ShowroomMemberDBConst.ASSIGN_STATUS,
            definition = "varchar(16) NOT NULL DEFAULT 'unassigned' COMMENT 'unassigned|assigned|reminded|ordered（showroom_member_assignment 状态机，仅 CAS 推进）'")
    private AssignStatus assignStatus;

    @Column(name = ShowroomMemberDBConst.LINKED_CUSTOMER_ID,
            definition = "bigint NULL COMMENT '逻辑外键 user.id（访客登录后绑定回填，决策 20.2；仅 owner 视图输出）'")
    private Long linkedCustomerId;
}
