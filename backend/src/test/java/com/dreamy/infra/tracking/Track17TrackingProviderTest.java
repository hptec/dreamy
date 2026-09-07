package com.dreamy.infra.tracking;

import com.dreamy.enums.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 17TRACK 状态映射 + 缺 key fail-fast。 */
class Track17TrackingProviderTest {

    @Test
    @DisplayName("stage/sub_status → ShipmentStatus 映射；未知 → null")
    void mapStatus() {
        assertThat(Track17TrackingProvider.mapStatus("InfoReceived", null)).isEqualTo(ShipmentStatus.PENDING);
        assertThat(Track17TrackingProvider.mapStatus("InTransit", null)).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(Track17TrackingProvider.mapStatus(null, "InTransit_PickedUp")).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(Track17TrackingProvider.mapStatus("OutForDelivery", null)).isEqualTo(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThat(Track17TrackingProvider.mapStatus("Delivered", null)).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(Track17TrackingProvider.mapStatus("Exception", null)).isEqualTo(ShipmentStatus.EXCEPTION);
        assertThat(Track17TrackingProvider.mapStatus("Undelivered", null)).isEqualTo(ShipmentStatus.EXCEPTION);
        assertThat(Track17TrackingProvider.mapStatus("Whatever", null)).isNull();
        assertThat(Track17TrackingProvider.CARRIER_KEYS).containsKeys("FEDEX", "UPS", "DHL", "USPS");
    }

    @Test
    @DisplayName("track17 模式缺 api key → 启动 fail-fast")
    void missingKeyFailsFast() {
        TrackingProperties props = new TrackingProperties();
        props.setMode("track17");
        assertThatThrownBy(() -> new Track17TrackingProvider(props, new com.fasterxml.jackson.databind.ObjectMapper()))
                .isInstanceOf(IllegalStateException.class);
    }
}
