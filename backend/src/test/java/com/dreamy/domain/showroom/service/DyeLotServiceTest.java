package com.dreamy.domain.showroom.service;

import com.dreamy.domain.showroom.repository.ShowroomItemRepository;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dye lot 窗口判定单元测试。
 * L2 TRACE: TC-SHR-004 [P0]（CV-SHR-011 窗口纯函数：23h59m true / 24h01m false / NULL false /
 * 窗口配置 48h 时 30h true）/ TC-SHR-021 单测面（ShowroomDyeLotPort 空参与域空数组）。
 */
@ExtendWith(MockitoExtension.class)
class DyeLotServiceTest {

    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomItemRepository itemRepository;

    private DyeLotService service(long windowHours) {
        return new DyeLotService(showroomRepository, itemRepository, windowHours);
    }

    @Test
    @DisplayName("窗口纯函数：now-23h59m → true；now-24h01m → false；NULL → false")
    void windowPureFunction() {
        DyeLotService service = service(24);
        LocalDateTime now = LocalDateTime.of(2026, 6, 10, 12, 0);
        assertThat(service.isWithinWindow(now.minusHours(23).minusMinutes(59), now)).isTrue();
        assertThat(service.isWithinWindow(now.minusHours(24).minusMinutes(1), now)).isFalse();
        assertThat(service.isWithinWindow(null, now)).isFalse();
    }

    @Test
    @DisplayName("窗口配置 48h 时 now-30h → true（配置化 dreamy.showroom.dye-lot-window-hours）")
    void configurableWindow() {
        DyeLotService service = service(48);
        LocalDateTime now = LocalDateTime.of(2026, 6, 10, 12, 0);
        assertThat(service.isWithinWindow(now.minusHours(30), now)).isTrue();
        assertThat(service.isWithinWindow(now.minusHours(49), now)).isFalse();
    }

    @Test
    @DisplayName("hintProductIds：customerId 无参与房 → 空数组且不触发窗口查询")
    void hintNoParticipation() {
        when(showroomRepository.listIdsByCustomerParticipation(7L)).thenReturn(List.of());
        assertThat(service(24).hintProductIds(7L, List.of(11L))).isEmpty();
        verify(itemRepository, never()).selectDyeLotProductIds(anyCollection(), anyCollection(), any());
    }

    @Test
    @DisplayName("hintProductIds：null/空入参 → 空数组（trading 空结果对齐）；命中透传仓储结果")
    void hintGuardsAndHit() {
        DyeLotService service = service(24);
        assertThat(service.hintProductIds(null, List.of(1L))).isEmpty();
        assertThat(service.hintProductIds(7L, List.of())).isEmpty();

        when(showroomRepository.listIdsByCustomerParticipation(7L)).thenReturn(List.of(1L, 2L));
        when(itemRepository.selectDyeLotProductIds(anyCollection(), anyCollection(), any()))
                .thenReturn(List.of(11L));
        assertThat(service.hintProductIds(7L, List.of(11L, 12L))).containsExactly(11L);
    }
}
