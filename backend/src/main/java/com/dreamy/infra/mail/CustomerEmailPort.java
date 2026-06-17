package com.dreamy.infra.mail;

/**
 * identity 领域用户邮箱查询端口（进程内直调防腐层，决策 3；与 review IdentityQueryPort 同范式）。
 * 用途：订单类邮件事件（order.paid/order.shipped/refund.resolved）payload 仅含 customer_id，
 * 消费侧经本端口解析收件邮箱（禁止消费者直查 user 表语义，适配实现在 MailPortConfig 收口）。
 */
public interface CustomerEmailPort {

    /** 用户邮箱；用户不存在/已匿名化返回 null（消费侧告警跳过，不落 MailRecord） */
    String getEmail(Long customerId);

    /**
     * 用户偏好语言 locale_pref（en/es/fr）；不存在/未设置返回 null（决策13 / FUNC-020）。
     * 邮件 locale 选择优先级：user.locale_pref > orders.locale_snapshot(payload.locale) > en。
     */
    String getLocalePref(Long customerId);
}
