package com.dreamy.infra.tracking;

import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.port.TrackingProviderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** stub 轨迹供应商：无操作（register 返回 null；fetchEvents 空）。 */
@Component
@ConditionalOnProperty(name = "dreamy.tracking.mode", havingValue = "stub", matchIfMissing = true)
public class StubTrackingProvider implements TrackingProviderPort {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public boolean isStub() {
        return true;
    }

    @Override
    public String register(Shipment shipment) {
        return null;
    }

    @Override
    public List<ProviderEvent> fetchEvents(Shipment shipment) {
        return List.of();
    }
}
