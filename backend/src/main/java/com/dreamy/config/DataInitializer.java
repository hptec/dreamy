package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.enums.AdminStatus;
import com.dreamy.enums.RoleType;
import com.dreamy.domain.authconfig.entity.AuthConfig;
import com.dreamy.domain.authconfig.repository.AuthConfigMapper;
import com.dreamy.domain.role.entity.Permission;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.entity.RolePermission;
import com.dreamy.domain.role.repository.PermissionMapper;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.repository.RolePermissionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String DEMO_ADMIN_EMAIL = "admin@dreamy.com";
    private static final String SUPER_ADMIN_ROLE = "超级管理员";

    @Value("${dreamy.bootstrap-admin.email:}")
    private String bootstrapAdminEmail;

    @Value("${dreamy.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Value("${dreamy.seed.demo-enabled:false}")
    private boolean demoSeedEnabled;

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
        AuthConfig cfg = new AuthConfig();
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
    /** permission 权限字典（后台菜单级路由，来源 portal-admin 路由表）。按 perm_code 幂等。 */
    private void initPermissions() {
        String[][] perms = {
                {"/", "工作台", "工作台"},
                {"/dashboard", "工作台", "工作台"},
                {"/site/home", "站点装修", "首页装修"},
                {"/site/navigation", "站点装修", "导航与页脚"},
                {"/site/announcement", "站点装修", "公告管理"},
                {"/banners", "站点装修", "Banner 管理"},
                {"/products", "商品管理", "商品列表"},
                {"/categories", "商品管理", "品类与主题"},
                {"/orders", "订单管理", "订单列表"},
                {"/refunds", "订单管理", "退款工单"},
                {"/customers", "用户管理", "用户列表"},
                {"/promotions", "营销活动", "优惠券与促销"},
                {"/marketing/email", "营销活动", "邮件营销"},
                {"/content/blog", "内容管理", "Blog 文章"},
                {"/content/weddings", "内容管理", "Real Weddings"},
                {"/content/lookbook", "内容管理", "Lookbook 与指南"},
                {"/reviews", "内容管理", "评价与 Q&A"},
                {"/analytics", "数据分析", "数据看板"},
                {"/publish", "发布与系统", "发布中心"},
                {"/shipping", "发布与系统", "物流配置"},
                {"/settings", "发布与系统", "汇率与结算配置"},
                {"/system/admins", "系统管理", "管理员管理"},
                {"/system/roles", "系统管理", "角色权限"},
                {"/system/auth", "系统管理", "登录与认证"},
                {"/system/logs", "系统管理", "操作日志"},
                // i18n-complete-with-ai-assist：网关配置（AI 翻译代理仍用）
                {"/system/gateways", "系统管理", "外部网关配置"},
                {"/attribute-sets", "商品管理", "属性集"},
        };
        for (String[] p : perms) {
            // A4: LambdaQueryWrapper 替代 @Select findIdByPermCode（仅判存在性，幂等）
            Long existing = permissionMapper.selectCount(new LambdaQueryWrapper<Permission>()
                    .eq(Permission::getPermCode, p[0]));
            if (existing != null && existing > 0) {
                continue;
            }
            Permission pe = new Permission();
            pe.setPermCode(p[0]);
            pe.setGroup(p[1]);
            pe.setLabel(p[2]);
            permissionMapper.insert(pe);
        }
    }

    /** 超管角色（is_locked=true）：缺则建，并关联全部权限（与应用层短路双保险 RISK-03）。返回 roleId。 */
    private Long initSuperRole() {
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getName, SUPER_ADMIN_ROLE));
        if (role == null) {
            role = new Role();
            role.setName(SUPER_ADMIN_ROLE);
            role.setType(RoleType.PRESET);
            role.setIsLocked(true);
            role.setVersion(0);
            roleMapper.insert(role);
            log.info("[DataInitializer] 超管角色已初始化 id={}", role.getId());
        }
        Long roleId = role.getId();
        // 关联全部权限（幂等：已存在的跳过）
        // A3: 分步查询替代 listKeysByRoleId JOIN —— 取已关联的 permission_id 集合按 id 判重
        java.util.Set<Long> existingPermIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId)
                .collect(java.util.stream.Collectors.toSet());
        for (Permission p : permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().isNotNull(Permission::getId))) {
            if (existingPermIds.contains(p.getId())) {
                continue;
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getId());
            rolePermissionMapper.insert(rp);
        }
        return roleId;
    }

    /**
     * 超管账户：缺则建（email 唯一）。生产/正式环境必须显式提供 bootstrap 凭据；
     * 仅 demo seed 开启时才允许使用本地演示账号，避免 Docker 重启生成公开固定密码。
     */
    private void initSuperAdmin(Long roleId) {
        String email = trimToNull(bootstrapAdminEmail);
        String password = trimToNull(bootstrapAdminPassword);
        if (email == null && demoSeedEnabled) {
            email = DEMO_ADMIN_EMAIL;
            password = "Admin@123456";
        }
        if (email == null || password == null) {
            log.warn("[DataInitializer] 未创建首个超管：请设置 DREAMY_BOOTSTRAP_ADMIN_EMAIL/PASSWORD "
                    + "（demo seed 关闭时不使用默认凭据）");
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("Bootstrap admin password must contain at least 12 characters");
        }
        AdminUser existing = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getEmail, email));
        if (existing != null) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setName("超级管理员");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRoleId(roleId);
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setVersion(0);
        adminUserMapper.insert(admin);
        log.info("[DataInitializer] 超管账户已初始化 id={}", admin.getId());
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
