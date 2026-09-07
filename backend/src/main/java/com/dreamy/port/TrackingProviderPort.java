package com.dreamy.port;

import com.dreamy.domain.shipment.entity.Shipment;
import com.dreamy.enums.ShipmentStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹供应商端口（order-flow-complete D）：stub（无操作）| track17（17TRACK v2.2）。
 * 失败语义（§4.6）：单包裹失败抛 {@link ProviderException}，调用方计数并跳过，连续失败 3 次仅告警不改状态。
 */
public interface TrackingProviderPort {

    /** 供应商轨迹事件（providerEventId 为空时由调用方以 sha1(occurred_at|status|description) 前 40 位派生） */
    record ProviderEvent(String providerEventId, LocalDateTime occurredAt, ShipmentStatus status, String location,
                         String description) {
    }

    class ProviderException extends RuntimeException {
        public ProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** stub | track17 */
    String name();

    /** stub 模式：sync → 204 无操作；调度器跳过 */
    default boolean isStub() {
        return false;
    }

    /** 登记包裹（创建后调用；返回供应商引用，可为 null） */
    String register(Shipment shipment);

    /** 拉取全部轨迹事件（供应商侧全量；调用方按 provider_event_id 去重） */
    List<ProviderEvent> fetchEvents(Shipment shipment);
}
