package com.dreamy.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * analytics 域初始化（ANA-IMPL-SEED-COORD）。本域无自有表/无自有种子（DEC-ANA-1 虚拟视图）。
 * 1. 权限字典幂等补登 /dashboard（概览/仪表盘）+ /analytics（数据分析/数据看板）并绑定超管角色
 *    （analytics-data-detail §7；/analytics identity 种子已含则跳过）。
 * 2. IDX-ANA-001 协同索引：向 trading orders 表增列 idx_order_status_paid(status, paid_at)
 *    （仅增列不改语义；orders 表此刻可能尚未由 trading 域建表——缺表/失败仅告警不阻塞，
 *    千单量级缺失仅退化为小表扫描）。
 */
@Component
@Order(40)
public class AnalyticsSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSeedInitializer.class);

    private static final String IDX_NAME = "idx_order_status_paid";

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final DataSource dataSource;

    public AnalyticsSeedInitializer(PermissionMapper permissionMapper, RoleMapper roleMapper,
                                    RolePermissionMapper rolePermissionMapper, DataSource dataSource) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        ensurePermissions();
        ensureOrdersPaidIndex();
    }

    /** §7 权限字典幂等补登 + 绑定超管角色 */
    @Transactional
    public void ensurePermissions() {
        ensurePermission("/dashboard", "概览", "仪表盘");
        ensurePermission("/analytics", "数据分析", "数据看板");
    }

    private void ensurePermission(String permCode, String group, String label) {
        Permission permission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getPermCode, permCode));
        if (permission == null) {
            permission = new Permission();
            permission.setPermCode(permCode);
            permission.setGroup(group);
            permission.setLabel(label);
            permissionMapper.insert(permission);
            log.info("[AnalyticsSeed] 权限点 {} 已登记", permCode);
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

    /** IDX-ANA-001：orders(status, paid_at) 协同索引（幂等：information_schema 判存在；缺表不阻塞） */
    private void ensureOrdersPaidIndex() {
        try (Connection conn = dataSource.getConnection()) {
            if (!tableExists(conn, "orders")) {
                log.info("[AnalyticsSeed] orders 表尚未建表（trading 域 DdlAuto 运行期解决），跳过 {}", IDX_NAME);
                return;
            }
            if (indexExists(conn)) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE `orders` ADD INDEX `" + IDX_NAME + "` (`status`, `paid_at`)");
                log.info("[AnalyticsSeed] orders 协同索引 {} 已创建（IDX-ANA-001）", IDX_NAME);
            }
        } catch (Exception ex) {
            // 索引缺失仅退化为小表扫描，不阻塞功能（IDX-ANA-001 协同声明）
            log.warn("[AnalyticsSeed] 协同索引 {} 创建失败（不阻塞）：{}", IDX_NAME, ex.getClass().getSimpleName());
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = ?")) {
            ps.setString(1, IDX_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }
}
