package com.dreamy.showroom.mq;

import com.dreamy.infra.mq.AbstractIdempotentEventConsumer;
import com.dreamy.infra.mq.DomainEvent;
import com.dreamy.infra.mq.EventIdempotencyGuard;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EVT-SHR-003：q.showroom 消费 order.paid（trading EVT-TRD-001，FLOW-P07 扇出 / FLOW-P12 注记）。
 * 最小依赖面 = event_id + customer_id + lines[].product_id（其余字段不依赖，与 trading 兼容基线）。
 * ① event_id 幂等闸（AbstractIdempotentEventConsumer：Redis SETNX TTL 7d；失败释放键 nack 重投重入）
 * ② place_order 推进（state-machine assigned|reminded → ordered）：linked_customer_id 定位
 *    （RM-SHR-038，「访客下单需登录」由定位前提天然承载——未绑定成员不可命中）→
 *    assigned_item.product_id ∈ lines[].product_id 的成员逐一 casOrder（RM-SHR-037，
 *    guard「订单行含被指派款式」bs-836/837；已 ordered affected=0 幂等空操作）
 * ③ dye lot 窗口回写（决策 20.4）：参与域（RM-SHR-010 自有+被绑定）× lines 款式
 *    touchLastOrdered（RM-SHR-024 覆盖写可重入）→ 24h 内 dye_lot_notice=true / DyeLotPort 命中
 * ④ TX-SHR-012 单事务：任一失败整体回滚 → 释放幂等键 + 抛出 → real 模式按
 *    q.showroom 重试阶梯 5s/30s/180s ×3 → dreamy.dlx → dreamy.dlq（application.yml dreamy.mq.queues）。
 * L2 TRACE: SHR-IMPL-MQ-CONSUMER / TC-SHR-020/021/030/055。
 */
@Component
public class ShowroomOrderPaidConsumer extends AbstractIdempotentEventConsumer {

    public static final String QUEUE = "q.showroom";

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final ShowroomMemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    public ShowroomOrderPaidConsumer(EventIdempotencyGuard idempotencyGuard,
                                     ShowroomRepository showroomRepository,
                                     ShowroomItemRepository itemRepository,
                                     ShowroomMemberRepository memberRepository,
                                     TransactionTemplate transactionTemplate) {
        super(idempotencyGuard);
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.memberRepository = memberRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String queue() {
        return QUEUE;
    }

    @Override
    public List<String> bindingKeys() {
        return List.of("order.paid");
    }

    @Override
    protected void handle(DomainEvent event) {
        Long customerId = extractCustomerId(event.payload());
        Set<Long> productIds = extractProductIds(event.payload());
        if (customerId == null || productIds.isEmpty()) {
            log.warn("[EVT-SHR-003] order.paid event_id={} without customer_id/lines, skipped",
                    event.eventId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        // TX-SHR-012 单事务（事件级幂等闸在事务外，基类承载）
        transactionTemplate.executeWithoutResult(tx -> {
            // ② place_order 推进
            List<ShowroomMember> members = memberRepository.listByLinkedCustomer(customerId);
            List<Long> assignedItemIds = members.stream()
                    .map(ShowroomMember::getAssignedItemId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Long, Long> productByItem = new HashMap<>();
            for (ShowroomItem item : itemRepository.listByIds(assignedItemIds)) {
                productByItem.put(item.getId(), item.getProductId());
            }
            int progressed = 0;
            for (ShowroomMember member : members) {
                Long itemProductId = productByItem.get(member.getAssignedItemId());
                if (itemProductId != null && productIds.contains(itemProductId)) {
                    progressed += memberRepository.casOrder(member.getId());
                }
            }
            // ③ dye lot 窗口回写（参与域：自有 + 被绑定）
            List<Long> showroomIds = showroomRepository.listIdsByCustomerParticipation(customerId);
            int touched = itemRepository.touchLastOrdered(showroomIds, productIds, now);
            log.info("[EVT-SHR-003] order.paid consumed event_id={} customer_id={} ordered+={} dye_lot_touched={}",
                    event.eventId(), customerId, progressed, touched);
        });
    }

    private Long extractCustomerId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("customer_id");
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Set<Long> extractProductIds(Map<String, Object> payload) {
        Set<Long> ids = new LinkedHashSet<>();
        if (payload == null) {
            return ids;
        }
        Object lines = payload.get("lines");
        if (lines instanceof List<?> list) {
            for (Object line : list) {
                if (line instanceof Map<?, ?> map) {
                    Object pid = map.get("product_id");
                    if (pid instanceof Number n) {
                        ids.add(n.longValue());
                    } else if (pid instanceof String s) {
                        try {
                            ids.add(Long.parseLong(s));
                        } catch (NumberFormatException ignored) {
                            // 非法行容忍跳过（最小依赖面外不报错）
                        }
                    }
                }
            }
        }
        return ids;
    }
}
