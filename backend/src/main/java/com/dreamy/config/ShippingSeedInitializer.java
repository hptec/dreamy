package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import com.dreamy.domain.carrier.entity.Carrier;
import com.dreamy.domain.carrier.repository.CarrierMapper;
import com.dreamy.enums.CarrierStatus;
import com.dreamy.domain.shippingrate.entity.ShippingRate;
import com.dreamy.domain.shippingrate.repository.ShippingRateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * shipping 域种子数据初始化（决策 21 mock 转种子；配置类数据生产与 dev/staging 同灌）。
 * 幂等：carrier/shipping_rate 各自表非空即跳过（跟随 identity DataInitializer / CatalogSeedInitializer 惯例）。
 * 同时幂等补登 RBAC 权限点 /shipping 并绑定超管角色（shipping-data-detail §8.3；identity 种子已含则跳过）。
 * 种子自检（§8.2）：①三 enabled 满足 CV-SHP-005；②每区域 3 行精确承运商行；③Rest of World 兜底行（CV-SHP-006）；
 * ④承运商 name 与 Order.carrier 枚举三值逐字一致；含 USPS Priority disabled 第 4 行（DEC-SHP-6）。
 */
@Component
@Order(30)
public class ShippingSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(ShippingSeedInitializer.class);

    private final CarrierMapper carrierMapper;
    private final ShippingRateMapper rateMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public ShippingSeedInitializer(CarrierMapper carrierMapper, ShippingRateMapper rateMapper,
                                   PermissionMapper permissionMapper, RoleMapper roleMapper,
                                   RolePermissionMapper rolePermissionMapper) {
        this.carrierMapper = carrierMapper;
        this.rateMapper = rateMapper;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        ensurePermission();
        seedCarriers();
        seedRates();
    }

    /** §8.3 权限字典幂等补登（/shipping，发布与系统/物流配置）+ 绑定超管角色 */
    private void ensurePermission() {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, "/shipping"));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode("/shipping");
            permission.setGroup("发布与系统");
            permission.setLabel("物流配置");
            permissionMapper.insert(permission);
            log.info("[ShippingSeed] 权限点 /shipping 已登记");
        }
        Role superRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getName, "超级管理员"));
        if (superRole != null) {
            Long bound = rolePermissionMapper.selectCount(new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRoleId, superRole.getId())
                    .eq(RolePermission::getPermissionId, permission.getId()));
            if (bound == null || bound == 0) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(superRole.getId());
                rp.setPermissionId(permission.getId());
                rolePermissionMapper.insert(rp);
            }
        }
    }

    /** 种子承运方：FedEx/UPS/DHL 三启用 + USPS disabled（DEC-SHP-6 原型对照行） */
    private void seedCarriers() {
        Long count = carrierMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        insertCarrier("FedEx International Priority", "全球", "3-5 天", CarrierStatus.ENABLED);
        insertCarrier("UPS Worldwide Express", "北美 / 欧洲", "4-6 天", CarrierStatus.ENABLED);
        insertCarrier("DHL Express", "全球", "3-6 天", CarrierStatus.ENABLED);
        insertCarrier("USPS Priority", "美国境内", "2-4 天", CarrierStatus.DISABLED);
        log.info("[ShippingSeed] 承运方种子 4 行已灌入");
    }

    /** 种子规则行：区域 × 承运商差异化价格 9 行 + Rest of World 无后缀兜底行（CV-SHP-006），共 10 行 */
    private void seedRates() {
        Long count = rateMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        List<Object[]> rows = List.of(
                new Object[]{"North America / FedEx International Priority", "8.00", "0.00", "200.00"},
                new Object[]{"North America / UPS Worldwide Express", "10.00", "0.00", "250.00"},
                new Object[]{"North America / DHL Express", "9.00", "0.00", "220.00"},
                new Object[]{"Europe / FedEx International Priority", "28.00", "0.00", "400.00"},
                new Object[]{"Europe / UPS Worldwide Express", "26.00", "0.00", "380.00"},
                new Object[]{"Europe / DHL Express", "27.00", "0.00", "400.00"},
                new Object[]{"Oceania / FedEx International Priority", "32.00", "0.00", "400.00"},
                new Object[]{"Oceania / UPS Worldwide Express", "34.00", "0.00", "420.00"},
                new Object[]{"Oceania / DHL Express", "33.00", "0.00", "400.00"},
                new Object[]{"Rest of World", "38.00", "0.00", "500.00"});
        for (Object[] row : rows) {
            ShippingRate rate = new ShippingRate();
            rate.setZone((String) row[0]);
            rate.setFeeUnder(new BigDecimal((String) row[1]));
            rate.setFeeOver(new BigDecimal((String) row[2]));
            rate.setThreshold(new BigDecimal((String) row[3]));
            rateMapper.insert(rate);
        }
        log.info("[ShippingSeed] 运费规则种子 10 行已灌入（含 Rest of World 兜底行）");
    }

    private void insertCarrier(String name, String zones, String leadTime, CarrierStatus status) {
        Carrier carrier = new Carrier();
        carrier.setName(name);
        carrier.setZones(zones);
        carrier.setLeadTime(leadTime);
        carrier.setStatus(status);
        carrierMapper.insert(carrier);
    }
}
