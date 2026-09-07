package com.dreamy.domain.shipment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.shipment.consts.ShipmentDBConst;
import com.dreamy.enums.ShipmentStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 shipment（order-flow-complete §2.2：包裹；一单多包裹/部分发货）。
 * uk shipment_no；uk idempotency_key（NULL 不受限）；uk (order_id, carrier_code, tracking_no) 重复录入幂等闸 → 409908。
 * 状态 PENDING→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED 单向；EXCEPTION 可进可恢复；CANCELLED 仅无供应商事件且未签收。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = ShipmentDBConst.TABLE, comment = "包裹（部分发货/多包裹）", indexes = {
        @Index(name = "uk_shipment_no", columns = {ShipmentDBConst.SHIPMENT_NO}, unique = true, local = false),
        @Index(name = "uk_shipment_idem", columns = {ShipmentDBConst.IDEMPOTENCY_KEY}, unique = true, local = false),
        @Index(name = "uk_shipment_tracking", columns = {ShipmentDBConst.ORDER_ID, ShipmentDBConst.CARRIER_CODE,
                ShipmentDBConst.TRACKING_NO}, unique = true, local = false),
        @Index(name = "idx_shipment_order", columns = {ShipmentDBConst.ORDER_ID}, unique = false, local = false),
        @Index(name = "idx_shipment_status_event", columns = {ShipmentDBConst.STATUS, ShipmentDBConst.LAST_EVENT_AT},
                unique = false, local = false)
})
@TableName(value = ShipmentDBConst.TABLE, autoResultMap = true)
public class Shipment extends LongAuditableEntity {

    @Column(name = ShipmentDBConst.ORDER_ID, definition = "bigint NOT NULL")
    private Long orderId;

    @Column(name = ShipmentDBConst.SHIPMENT_NO, definition = "varchar(24) NOT NULL COMMENT 'SHP-yyyyMMdd-NNNN'")
    private String shipmentNo;

    @Column(name = ShipmentDBConst.CARRIER_CODE, definition = "varchar(32) NOT NULL COMMENT 'carrier.code'")
    private String carrierCode;

    @Column(name = ShipmentDBConst.CARRIER_NAME, definition = "varchar(64) NULL COMMENT 'carrier.name 快照'")
    private String carrierName;

    @Column(name = ShipmentDBConst.TRACKING_NO, definition = "varchar(64) NOT NULL")
    private String trackingNo;

    @Column(name = ShipmentDBConst.TRACKING_URL, definition = "varchar(255) NULL COMMENT '按 carrier.tracking_url_template 生成'")
    private String trackingUrl;

    @Column(name = ShipmentDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '1=PENDING 2=IN_TRANSIT 3=OUT_FOR_DELIVERY 4=DELIVERED 5=EXCEPTION 6=CANCELLED'")
    private ShipmentStatus status;

    @Column(name = ShipmentDBConst.SHIPPED_AT, definition = "datetime(3) NULL")
    private LocalDateTime shippedAt;

    @Column(name = ShipmentDBConst.DELIVERED_AT, definition = "datetime(3) NULL")
    private LocalDateTime deliveredAt;

    @Column(name = ShipmentDBConst.LAST_EVENT_AT, definition = "datetime(3) NULL")
    private LocalDateTime lastEventAt;

    @Column(name = ShipmentDBConst.LAST_EVENT_DESC, definition = "varchar(255) NULL")
    private String lastEventDesc;

    @Column(name = ShipmentDBConst.PROVIDER_REF, definition = "varchar(64) NULL COMMENT '轨迹供应商登记引用'")
    private String providerRef;

    @Column(name = ShipmentDBConst.IDEMPOTENCY_KEY, definition = "varchar(64) NULL COMMENT '创建请求 Idempotency-Key'")
    private String idempotencyKey;

    @Column(name = ShipmentDBConst.SYNCED_AT, definition = "datetime(3) NULL COMMENT '供应商最近同步时间'")
    private LocalDateTime syncedAt;

    @Column(name = ShipmentDBConst.SYNC_FAILURES, definition = "int NOT NULL DEFAULT 0 COMMENT '供应商连续失败次数（≥3 告警）'")
    private Integer syncFailures;
}
