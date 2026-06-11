package com.dreamy.shipping.domain.carrier.service;

import com.dreamy.shipping.domain.enums.CarrierStatus;
import com.dreamy.shipping.error.ShippingErrorCode;
import com.dreamy.shipping.error.ShippingException;

import java.util.Map;

/**
 * carrier_status 状态机纯 guard（TASK-050；state-machine carrier_status 两迁移 + 409902 业务 guard 叠加）。
 * - enabled --disable--> disabled：仅当 enabledCount > 1（CV-SHP-005 不变量 enabled >= 1）
 * - disabled --enable--> enabled：无 guard
 * - 同值迁移：幂等短路（E-SHP-05 STEP-SHP-02，无写库、无审计、无失效）
 * 纯函数，独立可单测（TC-SHP-011）；调用方须在 EC-SHP-001 锁内取 enabledCount。
 */
public final class CarrierStatusMachine {

    /** 迁移评估结果 */
    public enum Transition {
        /** 同值幂等短路 */
        NOOP,
        /** 合法迁移，执行写库 */
        APPLY
    }

    private CarrierStatusMachine() {
    }

    /**
     * 评估迁移；非法禁用（最后启用承运方）抛 409902。
     *
     * @param current      现行状态
     * @param target       目标状态
     * @param enabledCount 当前 enabled 计数（锁内读取）
     */
    public static Transition evaluate(CarrierStatus current, CarrierStatus target, long enabledCount) {
        if (current == target) {
            return Transition.NOOP;
        }
        if (current == CarrierStatus.ENABLED && target == CarrierStatus.DISABLED) {
            assertNotLastEnabled(enabledCount);
        }
        return Transition.APPLY;
    }

    /** 409902 guard：enabled 计数不变量（E-SHP-03/04/05 同源；CV-SHP-005） */
    public static void assertNotLastEnabled(long enabledCount) {
        if (enabledCount <= 1) {
            throw new ShippingException(ShippingErrorCode.LAST_ENABLED_CARRIER,
                    Map.of("enabled_count", 1));
        }
    }
}
