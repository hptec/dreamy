package com.dreamy.domain.shipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.shipment.consts.ShipmentEventDBConst;
import com.dreamy.enums.ShipmentEventSource;
import com.dreamy.enums.ShipmentStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 shipment_event（order-flow-complete §2.2）：包裹轨迹；uk (shipment_id, provider_event_id)
 * （MANUAL 行 NULL 不受限；PROVIDER 行 insertIgnore 去重；供应商无事件 id 时取 sha1(occurred_at|status|description) 前 40 位）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ShipmentEventDBConst.TABLE, comment = "包裹轨迹事件", indexes = {
        @Index(name = "uk_shipment_event_provider", columns = {ShipmentEventDBConst.SHIPMENT_ID,
                ShipmentEventDBConst.PROVIDER_EVENT_ID}, unique = true, local = false),
        @Index(name = "idx_shipment_event_occurred", columns = {ShipmentEventDBConst.SHIPMENT_ID,
                ShipmentEventDBConst.OCCURRED_AT}, unique = false, local = false)
})
@TableName(value = ShipmentEventDBConst.TABLE, autoResultMap = true)
public class ShipmentEvent extends LongAuditableEntity {

    @Column(name = ShipmentEventDBConst.SHIPMENT_ID, definition = "bigint NOT NULL")
    private Long shipmentId;

    @Column(name = ShipmentEventDBConst.OCCURRED_AT, definition = "datetime(3) NOT NULL")
    private LocalDateTime occurredAt;

    @Column(name = ShipmentEventDBConst.STATUS, definition = "tinyint NOT NULL COMMENT 'ShipmentStatus'")
    private ShipmentStatus status;

    @Column(name = ShipmentEventDBConst.LOCATION, definition = "varchar(128) NULL")
    private String location;

    @Column(name = ShipmentEventDBConst.DESCRIPTION, definition = "varchar(255) NULL")
    private String description;

    @Column(name = ShipmentEventDBConst.SOURCE, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '1=MANUAL 2=PROVIDER 3=SYSTEM'")
    private ShipmentEventSource source;

    @Column(name = ShipmentEventDBConst.PROVIDER_EVENT_ID, definition = "varchar(64) NULL COMMENT '供应商事件 id（MANUAL 为 NULL）'")
    private String providerEventId;
}
