package com.dreamy.domain.showroom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.showroom.consts.ShowroomDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 表 showroom（协作空间：新娘创建，邀请伴娘团协作，决策 20，F-066/F-068）。
 * - owner_id 强隔离：owner 读写路径唯一入口 findByIdAndOwner（跨用户 404101 防探测，CV-SHR-007）。
 * - invite_token：不可猜 UUID（决策 20.2），uk_showroom_invite。
 * - invite_version 设计派生列：guest JWT inv_ver 等值校验（重置自增级联失效，CV-SHR-008）。
 * - invite_token_prev 设计派生列：上一代 token 单代保留（重置识别 410101，E-SHR-06 STEP-SHR-03）。
 * - 物理级联删除（不启用逻辑删除——协作空间删除即终结，showroom-data-detail §1.1）。
 * L2 TRACE: showroom-data-detail §1.2/§9 DDL-1 / IDX-SHR-001/002/011 / SHR-IMPL-ENTITY。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "showroom", comment = "Showroom 协作空间（新娘创建，邀请伴娘团协作）", indexes = {
        @Index(name = "uk_showroom_invite", columns = {"invite_token"}, unique = true, local = false),
        @Index(name = "idx_showroom_owner", columns = {"owner_id", "created_at"}, unique = false, local = false),
        @Index(name = "idx_showroom_invite_prev", columns = {"invite_token_prev"}, unique = false, local = false)
})
@TableName(value = "showroom", autoResultMap = true)
public class Showroom extends LongAuditableEntity {

    @Column(name = ShowroomDBConst.OWNER_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 user.id（创建者新娘，JWT subject，BE-DIM-6 强隔离）'")
    private Long ownerId;

    @Column(name = ShowroomDBConst.NAME,
            definition = "varchar(64) NOT NULL COMMENT '名称 trim 1..64（CV-SHR-001）'")
    private String name;

    @Column(name = ShowroomDBConst.WEDDING_DATE,
            definition = "date NULL COMMENT '婚期（F-077 结算自动带入，可空）'")
    private LocalDate weddingDate;

    @Column(name = ShowroomDBConst.INVITE_TOKEN,
            definition = "varchar(64) NOT NULL COMMENT '不可猜 UUID 邀请 token（决策 20.2）'")
    private String inviteToken;

    @Column(name = ShowroomDBConst.INVITE_TOKEN_PREV,
            definition = "varchar(64) NULL COMMENT '设计派生列：上一代 token（重置识别 410101，单代保留）'")
    private String inviteTokenPrev;

    @Column(name = ShowroomDBConst.INVITE_VERSION,
            definition = "int NOT NULL DEFAULT 1 COMMENT '设计派生列：邀请版本号（guest JWT inv_ver 等值校验，重置自增，CV-SHR-008）'")
    private Integer inviteVersion;
}
