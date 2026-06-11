package com.dreamy.showroom.domain.showroom.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.showroom.domain.showroom.consts.ShowroomItemDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 showroom_item（收藏款式，F-067）。
 * - uk_si_room_product_color：同房 product_id+color 三元唯一（409102）；color 未选归一化空串
 *   参与 uk（MySQL uk 对 NULL 不去重的规避，CV-SHR-003）。
 * - last_ordered_at 设计派生列：同房该款式最近已付订单时间（order.paid 消费回写，
 *   dye lot 24h 窗口源，决策 20.4 / EVT-SHR-003 / CV-SHR-011）。
 * L2 TRACE: showroom-data-detail §1.2/§9 DDL-2 / IDX-SHR-003/004 / SHR-IMPL-ENTITY。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "showroom_item", comment = "Showroom 收藏款式（同房 product+color 唯一 409102）", indexes = {
        @Index(name = "uk_si_room_product_color", columns = {"showroom_id", "product_id", "color"},
                unique = true, local = false),
        @Index(name = "idx_si_product_ordered", columns = {"product_id", "last_ordered_at"},
                unique = false, local = false)
})
@TableName(value = "showroom_item", autoResultMap = true)
public class ShowroomItem extends LongAuditableEntity {

    @Column(name = ShowroomItemDBConst.SHOWROOM_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 showroom.id'")
    private Long showroomId;

    @Column(name = ShowroomItemDBConst.PRODUCT_ID,
            definition = "bigint NOT NULL COMMENT '逻辑外键 product.id（写前经 CatalogSnapshotPort 校验）'")
    private Long productId;

    @Column(name = ShowroomItemDBConst.COLOR,
            definition = "varchar(64) NOT NULL DEFAULT '' COMMENT '加入时选择的颜色；未选归一化空串（uk 三元唯一前提，CV-SHR-003）'")
    private String color;

    @Column(name = ShowroomItemDBConst.LAST_ORDERED_AT,
            definition = "datetime(3) NULL COMMENT '设计派生列：同房该款式最近已付订单时间（order.paid 消费回写，dye lot 24h 窗口源，决策 20.4）'")
    private LocalDateTime lastOrderedAt;
}
