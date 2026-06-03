package com.dreamy.identity.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.identity.domain.admin.entity.AdminUserEntity;
import com.dreamy.identity.domain.admin.repository.AdminUserMapper;
import com.dreamy.identity.domain.authconfig.entity.AuthConfigEntity;
import com.dreamy.identity.domain.authconfig.repository.AuthConfigMapper;
import com.dreamy.identity.domain.role.entity.PermissionEntity;
import com.dreamy.identity.domain.role.entity.RoleEntity;
import com.dreamy.identity.domain.role.entity.RolePermissionEntity;
import com.dreamy.identity.domain.role.repository.PermissionMapper;
import com.dreamy.identity.domain.role.repository.RoleMapper;
import com.dreamy.identity.domain.role.repository.RolePermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 种子数据初始化（幂等）。监听 ApplicationReadyEvent，确保在 huihao-mysql DDLInit（ApplicationRunner，
 * LOWEST_PRECEDENCE）完成建表之后运行。初始化运行所需基线数据：
 * auth_config 单例 + permission 权限字典 + 超管角色（is_locked）+ 超管账户。
 * 主键为 Long 自增，无法硬编码 id，故采用「按业务键查→缺则建」的幂等策略。
 * 替代原 seed-supplement.sql（旧 varchar 主键种子已随主键迁移废弃）。
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SUPER_ADMIN_EMAIL = "admin@dreamy.com";
    private static final String SUPER_ADMIN_ROLE = "超级管理员";
    // BCrypt of "Admin@123456"
    private static final String SUPER_ADMIN_PWD_HASH =
            "$2a$10$P18uxFq2na0XwcipZK74MuoHHcteyR18ShF1ph7Dugc6SdmaZW17.";

    private final AuthConfigMapper authConfigMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AuthConfigMapper authConfigMapper, PermissionMapper permissionMapper,
                           RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                           AdminUserMapper adminUserMapper, PasswordEncoder passwordEncoder) {
        this.authConfigMapper = authConfigMapper;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        initAuthConfig();
        initPermissions();
        Long roleId = initSuperRole();
        initSuperAdmin(roleId);
        log.info("[DataInitializer] 种子数据初始化完成");
    }

    /** auth_config 单例（id=1）：缺则建默认值（email 恒开，OTP 6 位 5 分钟） */
    private void initAuthConfig() {
        if (authConfigMapper.selectById(1L) != null) {
            return;
        }
        AuthConfigEntity cfg = new AuthConfigEntity();
        cfg.setEmailEnabled(true);
        cfg.setGoogleEnabled(true);
        cfg.setAppleEnabled(true);
        cfg.setOtpLength(6);
        cfg.setOtpTtlMinutes(5);
        cfg.setOtpResendSeconds(60);
        cfg.setOtpMaxAttempts(5);
        cfg.setMinMethods(1);
        authConfigMapper.insert(cfg);
        log.info("[DataInitializer] auth_config 单例已初始化 id={}", cfg.getId());
    }
    // PERM_PLACEHOLDER

    /** permission 权限字典（21 菜单级路由，来源 portal-admin 路由表）。按 perm_code 幂等。 */
    private void initPermissions() {
        String[][] perms = {
                {"/", "工作台", "工作台"},
                {"/site/home", "站点装修", "首页装修"},
                {"/site/navigation", "站点装修", "导航与页脚"},
                {"/site/banners", "站点装修", "Banner 管理"},
                {"/products", "商品管理", "商品列表"},
                {"/categories", "商品管理", "品类与主题"},
                {"/orders", "订单管理", "订单列表"},
                {"/refunds", "订单管理", "退款工单"},
                {"/customers", "用户管理", "用户列表"},
                {"/marketing/promotions", "营销活动", "优惠券与促销"},
                {"/marketing/email", "营销活动", "邮件营销"},
                {"/content/blog", "内容管理", "Blog 文章"},
                {"/content/weddings", "内容管理", "Real Weddings"},
                {"/content/lookbook", "内容管理", "Lookbook 与指南"},
                {"/analytics", "数据分析", "数据看板"},
                {"/publish", "发布与系统", "发布中心"},
                {"/shipping", "发布与系统", "物流配置"},
                {"/system/admins", "系统管理", "管理员管理"},
                {"/system/roles", "系统管理", "角色权限"},
                {"/system/auth", "系统管理", "登录与认证"},
                {"/system/logs", "系统管理", "操作日志"},
        };
        for (String[] p : perms) {
            Long existing = permissionMapper.findIdByPermCode(p[0]);
            if (existing != null) {
                continue;
            }
            PermissionEntity pe = new PermissionEntity();
            pe.setPermCode(p[0]);
            pe.setGroup(p[1]);
            pe.setLabel(p[2]);
            permissionMapper.insert(pe);
        }
    }

    /** 超管角色（is_locked=true）：缺则建，并关联全部权限（与应用层短路双保险 RISK-03）。返回 roleId。 */
    private Long initSuperRole() {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getName, SUPER_ADMIN_ROLE));
        if (role == null) {
            role = new RoleEntity();
            role.setName(SUPER_ADMIN_ROLE);
            role.setType("preset");
            role.setIsLocked(true);
            role.setVersion(0);
            roleMapper.insert(role);
            log.info("[DataInitializer] 超管角色已初始化 id={}", role.getId());
        }
        Long roleId = role.getId();
        // 关联全部权限（幂等：已存在的跳过）
        List<String> existingPerms = rolePermissionMapper.listKeysByRoleId(roleId);
        for (PermissionEntity p : permissionMapper.selectList(null)) {
            if (existingPerms.contains(p.getPermCode())) {
                continue;
            }
            RolePermissionEntity rp = new RolePermissionEntity();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getId());
            rolePermissionMapper.insert(rp);
        }
        return roleId;
    }

    /** 超管账户：缺则建（email 唯一）。密码 Admin@123456。 */
    private void initSuperAdmin(Long roleId) {
        AdminUserEntity existing = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getEmail, SUPER_ADMIN_EMAIL));
        if (existing != null) {
            return;
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setName("超级管理员");
        admin.setEmail(SUPER_ADMIN_EMAIL);
        admin.setPasswordHash(SUPER_ADMIN_PWD_HASH);
        admin.setRoleId(roleId);
        admin.setStatus("active");
        admin.setVersion(0);
        adminUserMapper.insert(admin);
        log.info("[DataInitializer] 超管账户已初始化 id={}", admin.getId());
    }
}
