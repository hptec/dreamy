package com.dreamy.domain.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.domain.order.consts.OrderEventDBConst;
import com.dreamy.enums.OrderActorType;
import com.dreamy.enums.OrderEventType;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 表 order_event（订单时间线：状态变更/备注/发货/支付/退款/邮件/制作阶段——order-flow-complete §2.2/§4.5）。
 * 写入始终与触发它的业务变更同事务（OrderEventRecorder 在调用方事务内落表）。
 * customer_visible=0 的事件仅后台可见（内部备注/迟到支付补偿/邮件已发送）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "order_event", comment = "订单时间线事件", indexes = {
        @Index(name = "idx_order_event_order_created", columns = {"order_id", "created_at"}, unique = false, local = false)
})
@TableName(value = "order_event", autoResultMap = true)
public class OrderEvent extends LongAuditableEntity {

    @Column(name = OrderEventDBConst.ORDER_ID, definition = "bigint NOT NULL")
    private Long orderId;

    @Column(name = OrderEventDBConst.TYPE, definition = "tinyint NOT NULL COMMENT '1=STATUS_CHANGED 2=NOTE 3=SHIPMENT 4=PAYMENT 5=REFUND 6=EMAIL 7=PRODUCTION'")
    private OrderEventType type;

    @Column(name = OrderEventDBConst.ACTOR_TYPE, definition = "tinyint NOT NULL COMMENT '1=SYSTEM 2=CUSTOMER 3=ADMIN'")
    private OrderActorType actorType;

    @Column(name = OrderEventDBConst.ACTOR_ID, definition = "bigint NULL COMMENT 'customer_id / admin_user.id；SYSTEM 为 NULL'")
    private Long actorId;

    @Column(name = OrderEventDBConst.TITLE, definition = "varchar(128) NOT NULL")
    private String title;

    @Column(name = OrderEventDBConst.DETAIL, definition = "varchar(512) NULL")
    private String detail;

    @Column(name = OrderEventDBConst.PAYLOAD, definition = "json NULL COMMENT '结构化附加数据（from/to 状态、金额、单号等）'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    @Column(name = OrderEventDBConst.CUSTOMER_VISIBLE, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean customerVisible;
}
