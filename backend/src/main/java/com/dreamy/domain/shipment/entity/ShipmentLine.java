package com.dreamy.domain.shipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.shipment.consts.ShipmentLineDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 shipment_line（order-flow-complete §2.2）：包裹分配行；uk (shipment_id, order_line_id)；
 * 不变量 SUM(qty WHERE order_line_id=X, shipment 非 CANCELLED) ≤ order_line.qty（锁内校验 → 422906）。分配不可变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ShipmentLineDBConst.TABLE, comment = "包裹分配行", indexes = {
        @Index(name = "uk_shipment_line", columns = {ShipmentLineDBConst.SHIPMENT_ID, ShipmentLineDBConst.ORDER_LINE_ID},
                unique = true, local = false),
        @Index(name = "idx_shipment_line_order_line", columns = {ShipmentLineDBConst.ORDER_LINE_ID}, unique = false,
                local = false)
})
@TableName(value = ShipmentLineDBConst.TABLE, autoResultMap = true)
public class ShipmentLine extends LongAuditableEntity {

    @Column(name = ShipmentLineDBConst.SHIPMENT_ID, definition = "bigint NOT NULL")
    private Long shipmentId;

    @Column(name = ShipmentLineDBConst.ORDER_LINE_ID, definition = "bigint NOT NULL")
    private Long orderLineId;

    @Column(name = ShipmentLineDBConst.QTY, definition = "int NOT NULL")
    private Integer qty;
}
