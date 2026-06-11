package com.dreamy.infra.mail;

/**
 * identity 领域用户邮箱查询端口（进程内直调防腐层，决策 3；与 review IdentityQueryPort 同范式）。
 * 用途：订单类邮件事件（order.paid/order.shipped/refund.resolved）payload 仅含 customer_id，
 * 消费侧经本端口解析收件邮箱（禁止消费者直查 user 表语义，适配实现在 MailPortConfig 收口）。
 */
public interface CustomerEmailPort {

    /** 用户邮箱；用户不存在/已匿名化返回 null（消费侧告警跳过，不落 MailRecord） */
    String getEmail(Long customerId);
}
