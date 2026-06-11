package com.dreamy.showroom.domain.showroom.service;

import com.dreamy.showroom.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.port.DyeLotPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * dye lot 24h 窗口判定服务（决策 20.4 定稿，CV-SHR-011）：
 * `dye_lot_notice = (last_ordered_at IS NOT NULL AND last_ordered_at > now() - INTERVAL :window HOUR)`，
 * window 配置项 dreamy.showroom.dye-lot-window-hours 缺省 24。判定纯展示（不影响履约）。
 * 同时承载 DyeLotPort 实现（trading cart/quote 消费，showroom-data-detail §8.3）：
 * RM-SHR-010 参与域先行收敛 showroom_ids → RM-SHR-025 窗口命中——Showroom 详情视图与
 * 购物车/结算提示强一致（同列同口径）。不缓存（CACHE-SHR-001 连带口径）。
 * L2 TRACE: SHR-IMPL-PORT / TC-SHR-004/021。
 */
@Service
public class DyeLotService implements DyeLotPort {

    private final ShowroomRepository showroomRepository;
    private final ShowroomItemRepository itemRepository;
    private final long windowHours;

    public DyeLotService(ShowroomRepository showroomRepository, ShowroomItemRepository itemRepository,
                         @Value("${dreamy.showroom.dye-lot-window-hours:24}") long windowHours) {
        this.showroomRepository = showroomRepository;
        this.itemRepository = itemRepository;
        this.windowHours = windowHours;
    }

    /** CV-SHR-011 窗口纯函数（now 注入便于测试，TC-SHR-004） */
    public boolean isWithinWindow(LocalDateTime lastOrderedAt, LocalDateTime now) {
        return lastOrderedAt != null && lastOrderedAt.isAfter(now.minusHours(windowHours));
    }

    /** CV-SHR-011 窗口判定（当前时刻） */
    public boolean isWithinWindow(LocalDateTime lastOrderedAt) {
        return isWithinWindow(lastOrderedAt, LocalDateTime.now());
    }

    /**
     * DyeLotPort.hintProductIds：customerId 无参与房/无命中 → 空数组
     * （trading STEP「空结果返回空数组」对齐）。
     */
    @Override
    public List<Long> hintProductIds(Long customerId, Collection<Long> productIds) {
        if (customerId == null || productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> showroomIds = showroomRepository.listIdsByCustomerParticipation(customerId);
        if (showroomIds.isEmpty()) {
            return List.of();
        }
        return itemRepository.selectDyeLotProductIds(showroomIds, productIds,
                LocalDateTime.now().minusHours(windowHours));
    }
}
