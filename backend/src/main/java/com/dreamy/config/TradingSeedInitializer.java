package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import com.dreamy.domain.checkout.entity.CheckoutConfig;
import com.dreamy.domain.checkout.repository.CheckoutConfigRepository;
import com.dreamy.domain.exchangerate.entity.ExchangeRate;
import com.dreamy.domain.exchangerate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * trading 域种子数据初始化（决策 21：汇率表五币种种子 + CheckoutConfig 单例；订单类数据不灌假数据）。
 * - exchange_rate：USD=1.000000 恒定 / EUR 0.92 / CAD 1.36 / AUD 1.52 / GBP 0.79
 *   （原型前端硬编码汇率口径，上线后管理端维护接管——trading-data-detail §9 种子说明）。
 * - checkout_config：id=1，gift_wrap_fee_usd=15.00（决策 28 原型 +$15）/ grace=24h（决策 24 默认）。
 * - RBAC：/settings 权限点（汇率维护 + 结算配置，trading-api-detail §0）按 perm_code 幂等注册并绑定超管。
 * 幂等：按业务键查→缺则建（与 identity/catalog 种子同惯例）。
 */
@Component
@Order(30)
public class TradingSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(TradingSeedInitializer.class);

    private static final Map<String, String> RATE_SEEDS = Map.of(
            "USD", "1.000000",
            "EUR", "0.920000",
            "CAD", "1.360000",
            "AUD", "1.520000",
            "GBP", "0.790000");

    private final ExchangeRateRepository exchangeRateRepository;
    private final CheckoutConfigRepository checkoutConfigRepository;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public TradingSeedInitializer(ExchangeRateRepository exchangeRateRepository,
                                  CheckoutConfigRepository checkoutConfigRepository,
                                  PermissionMapper permissionMapper, RoleMapper roleMapper,
                                  RolePermissionMapper rolePermissionMapper) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.checkoutConfigRepository = checkoutConfigRepository;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedExchangeRates();
        seedCheckoutConfig();
        ensureSettingsPermission();
    }

    private void seedExchangeRates() {
        int inserted = 0;
        for (Map.Entry<String, String> entry : RATE_SEEDS.entrySet()) {
            if (exchangeRateRepository.findByCurrency(entry.getKey()) != null) {
                continue;
            }
            ExchangeRate rate = new ExchangeRate();
            rate.setCurrency(entry.getKey());
            rate.setRate(new BigDecimal(entry.getValue()));
            exchangeRateRepository.insert(rate);
            inserted++;
        }
        if (inserted > 0) {
            log.info("[TRADING-SEED] exchange_rate seeded rows={}", inserted);
        }
    }

    private void seedCheckoutConfig() {
        if (checkoutConfigRepository.exists()) {
            return;
        }
        CheckoutConfig config = new CheckoutConfig();
        config.setId(CheckoutConfig.SINGLETON_ID);
        config.setGiftWrapFeeUsd(new BigDecimal("15.00"));
        config.setCustomRefundGraceHours(24);
        checkoutConfigRepository.insert(config);
        log.info("[TRADING-SEED] checkout_config 单例已初始化（gift_wrap_fee_usd=15.00, grace=24h）");
    }

    /** /settings 权限点注册 + 超管角色绑定（与 catalog ensureAttributeSetsPermission 同范式） */
    private void ensureSettingsPermission() {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, "/settings"));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode("/settings");
            permission.setGroup("发布与系统");
            permission.setLabel("汇率与结算配置");
            permissionMapper.insert(permission);
            log.info("[TRADING-SEED] permission /settings 已注册");
        }
        Role superRole = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, "超级管理员"));
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
}
