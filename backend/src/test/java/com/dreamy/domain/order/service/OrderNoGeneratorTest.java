package com.dreamy.domain.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单号格式纯函数单测。
 * L2 TRACE: TC-TRD-012 [P1]（^DRM-\d{8}-\d{4}$ / ^RFD-\d{8}-\d{4}$ 模式断言 + 序号回绕）。
 */
class OrderNoGeneratorTest {

    @Test
    @DisplayName("TC-TRD-012 [P1]: 订单号/工单号模式断言（CV-TRD-005）")
    void formatPattern() {
        assertThat(OrderNoGenerator.format("DRM", "20260610", 7)).matches("^DRM-\\d{8}-\\d{4}$");
        assertThat(OrderNoGenerator.format("DRM", "20260610", 7)).isEqualTo("DRM-20260610-0007");
        assertThat(OrderNoGenerator.format("RFD", "20260610", 42)).isEqualTo("RFD-20260610-0042");
        assertThat(OrderNoGenerator.format("RFD", "20260610", 9999)).matches("^RFD-\\d{8}-\\d{4}$");
    }

    @Test
    @DisplayName("TC-TRD-012 [P1]: 当日序号 >9999 回绕 4 位（uk_order_no 兜底重试承载唯一性）")
    void sequenceWraps() {
        assertThat(OrderNoGenerator.format("DRM", "20260610", 10001)).isEqualTo("DRM-20260610-0001");
        assertThat(OrderNoGenerator.format("DRM", "20260610", 10001)).matches("^DRM-\\d{8}-\\d{4}$");
    }
}
