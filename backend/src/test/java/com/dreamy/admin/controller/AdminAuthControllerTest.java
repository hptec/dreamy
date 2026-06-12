package com.dreamy.admin.controller;
import com.dreamy.enums.*;

import com.dreamy.aspect.AuditAspect;
import com.dreamy.aspect.AuditLog;
import com.dreamy.aspect.PermissionAspect;
import com.dreamy.config.AdminConfig;
import com.dreamy.controller.AdminAuthController;
import com.dreamy.security.AdminJwtFilter;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.admin.service.AdminService;
import com.dreamy.domain.audit.service.AuditService;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.role.service.RoleService;
import com.dreamy.dto.AdminDTO;
import com.dreamy.dto.mapper.IdentityDtoMapper;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.error.GlobalExceptionHandler;
import com.dreamy.i18n.MessageResolver;
import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.JwtProperties;
import com.dreamy.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web 切片测试：AdminAuthController + AdminJwtFilter + GlobalExceptionHandler。
 * TC-WEB-ADMIN-001~006。
 * STUB_REASON: Web 切片测试边界，mock service 层以隔离 DB/Redis。
 * STUB_SCOPE: repository_io（service 层作为 web 切片的 I/O 边界）。
 * L0 TRACE: FUNC-014/021, EDGE-024, NBT-01/02/03/04
 * L2 TRACE: IT-06(web), NBT-01~04
 */
@WebMvcTest(controllers = {AdminAuthController.class},
        // 切片隔离：包合并后无法按包名区分域，按类型排除其他域的 @ControllerAdvice 与全局 Filter
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {com.dreamy.error.AnalyticsExceptionHandler.class,
                        com.dreamy.error.CatalogExceptionHandler.class,
                        com.dreamy.error.MarketingExceptionHandler.class,
                        com.dreamy.error.ReviewExceptionHandler.class,
                        com.dreamy.error.ShippingExceptionHandler.class,
                        com.dreamy.error.ShowroomExceptionHandler.class,
                        com.dreamy.error.TradingExceptionHandler.class,
                        com.dreamy.infra.web.SecurityHeadersFilter.class}))
@Import({AdminJwtFilter.class, AdminConfig.class, GlobalExceptionHandler.class,
        PermissionAspect.class,
        AdminAuthControllerTest.TestConfig.class})
@org.springframework.context.annotation.EnableAspectJAutoProxy
class AdminAuthControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public MessageSource messageSource() {
            ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
            ms.setBasename("classpath:i18n/messages");
            ms.setDefaultEncoding("UTF-8");
            ms.setUseCodeAsDefaultMessage(true);
            ms.setFallbackToSystemLocale(false);
            return ms;
        }

        @Bean
        public MessageResolver messageResolver(MessageSource messageSource) {
            return new MessageResolver(messageSource);
        }

        @Bean
        public JwtProperties jwtProperties() {
            JwtProperties p = new JwtProperties();
            p.getStore().setSecret("store-test-secret-key-32chars-min");
            p.getAdmin().setSecret("admin-test-secret-key-32chars-min");
            return p;
        }

        @Bean
        public JwtTokenProvider jwtTokenProvider(JwtProperties props) {
            return new JwtTokenProvider(props);
        }

        // 白名单配置化（portal-api-integration 基建）：@WebMvcTest 切片会装配 StoreJwtFilter（Filter 组件），
        // 其依赖 StoreSecurityProperties；admin 切片提供 identity 既有 5 条公开路径，行为口径不变。
        @Bean
        public com.dreamy.security.StoreSecurityProperties storeSecurityProperties() {
            com.dreamy.security.StoreSecurityProperties p =
                    new com.dreamy.security.StoreSecurityProperties();
            p.setStorePublicPaths(java.util.List.of(
                    "/api/store/auth/otp/send",
                    "/api/store/auth/otp/verify",
                    "/api/store/auth/oidc/**",
                    "/api/store/auth/refresh",
                    "/api/store/auth/config"));
            return p;
        }

        // 主键 Long 迁移后 DreamyApplication 携带 @EnableMysql，@WebMvcTest 以其为配置源会 Import
        // DdlAutoConfiguration（informationSchemaService/ddlInit 需 DataSource）。Web 切片无 DataSource，
        // 故提供 mock 满足装配；DDLInit 为 ApplicationRunner，切片上下文不触发其执行。
        @Bean
        public javax.sql.DataSource dataSource() {
            return org.mockito.Mockito.mock(javax.sql.DataSource.class);
        }

        // 同理 @MapperScan 把 domain mapper 注册进切片，MapperFactoryBean.checkDaoConfig 需
        // sqlSessionTemplate.getConfiguration() 非空。mock template 返回真实 Configuration 以通过
        // mapper 注册校验；service 层已 @MockitoBean，mapper 代理不会被真实调用。
        @Bean
        public org.mybatis.spring.SqlSessionTemplate sqlSessionTemplate() {
            org.mybatis.spring.SqlSessionTemplate t =
                    org.mockito.Mockito.mock(org.mybatis.spring.SqlSessionTemplate.class);
            org.mockito.Mockito.when(t.getConfiguration())
                    .thenReturn(new org.apache.ibatis.session.Configuration());
            return t;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean AdminService adminService;
    @MockitoBean RoleService roleService;
    @MockitoBean IdentityDtoMapper mapper;
    @MockitoBean AuditService auditService;
    @MockitoBean com.dreamy.infra.SessionValidator sessionValidator;
    // PermissionAspect 实时查 DB，mock AdminUserMapper + RoleMapper 返回权限
    @MockitoBean AdminUserMapper adminUserMapper;
    @MockitoBean RoleMapper roleMapper;

    @org.junit.jupiter.api.BeforeEach
    void stubSessionValid() {
        when(sessionValidator.isAdminSessionValid(any())).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    // ===== TC-WEB-ADMIN-001: POST /api/admin/auth/login 公开可达 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-001 [P0]: POST /api/admin/auth/login 无 token → 200（FUNC-014）")
    void adminLogin_validCredentials_returns200() throws Exception {
        // ARRANGE
        AdminUser admin = adminUser(1L, "admin@dreamy.com", AdminStatus.ACTIVE);
        Role role = customRole(1L, "运营");
        AdminService.LoginOutcome outcome = new AdminService.LoginOutcome(
                admin, role, List.of("/system/admins"), "mock-token");
        when(adminService.login(eq("admin@dreamy.com"), eq("pass123"), any(), any()))
                .thenReturn(outcome);
        when(roleService.findById(1L)).thenReturn(role);

        // ACT + ASSERT: L1 200, L2 token 存在, L3 admin/permission_keys, L4 admin.email（R 包络 data）
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dreamy.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("mock-token"))
                .andExpect(jsonPath("$.data.admin.email").value("admin@dreamy.com"))
                .andExpect(jsonPath("$.data.permission_keys[0]").value("/system/admins"));
    }

    // ===== TC-WEB-ADMIN-002: POST /api/admin/auth/login 凭据错误 → 401 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-002 [P0]: POST /api/admin/auth/login 密码错误 → 401 + code=40103（EDGE-011）")
    void adminLogin_wrongPassword_returns401() throws Exception {
        // ARRANGE
        when(adminService.login(any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.CREDENTIALS_INVALID));

        // ACT + ASSERT: L1 401, L2 code=40103, L4 中文 message（admin 固定 zh）
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dreamy.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103))
                .andExpect(jsonPath("$.message").value("邮箱或密码错误"));
    }

    // ===== TC-WEB-ADMIN-003: GET /api/admin/auth/me 无 token → 401 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-003 [P0]: GET /api/admin/auth/me 无 token → 401（鉴权守卫）")
    void adminMe_noToken_returns401() throws Exception {
        // ASSERT: L1 401, L2 code=40100
        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ===== TC-WEB-ADMIN-004: GET /api/admin/auth/me 有效 admin token → 200 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-004 [P0]: GET /api/admin/auth/me 有效 admin token → 200（FUNC-021）")
    void adminMe_withValidToken_returns200() throws Exception {
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken("1", "1");
        when(adminService.meData(eq(1L), any())).thenReturn(
                new AdminService.MeData(
                        new AdminDTO(1L, "Admin", "admin@dreamy.com", 1L, "运营", AdminStatus.ACTIVE, null),
                        "运营", false, List.of("/system/admins")));

        // ACT + ASSERT: L1 200, L2 admin 存在, L3 role_name/is_super/permission_keys, L4 is_super=false
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin.id").value(1))
                .andExpect(jsonPath("$.data.role_name").value("运营"))
                .andExpect(jsonPath("$.data.is_super").value(false))
                .andExpect(jsonPath("$.data.permission_keys[0]").value("/system/admins"));
    }

    // ===== TC-WEB-ADMIN-005: EDGE-024 — store token 访问 /api/admin/* → 401 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-005 [P0]: store token 访问 /api/admin/auth/me → 401（EDGE-024 跨端隔离）")
    void adminMe_withStoreToken_returns401() throws Exception {
        // ARRANGE: 签发 store token（用 store 密钥）
        JwtProperties storeProps = new JwtProperties();
        storeProps.getStore().setSecret("store-test-secret-key-32chars-min");
        storeProps.getAdmin().setSecret("admin-test-secret-key-32chars-min");
        JwtTokenProvider storeProvider = new JwtTokenProvider(storeProps);
        com.dreamy.security.TokenPair storePair =
                storeProvider.issueStoreTokens("user-1", "email");

        // ACT + ASSERT: L1 401（admin 密钥无法解析 store token）
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + storePair.getAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ===== TC-WEB-ADMIN-009: BLOCKER-2 — 低权管理员越权调用 createAdmin → 403 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-009 [P0]: 无 /system/admins 权限的 admin 调用 POST /api/admin/admins → 403 + 40300（BLOCKER-2 服务端 RBAC）")
    void createAdmin_withoutPermission_returns403() throws Exception {
        // ARRANGE: 低权 admin，实时查 DB 权限仅含 /customers（不含 /system/admins）
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken("2", "2");
        AdminUser admin = adminUser(2L, "low@dreamy.com", AdminStatus.ACTIVE);
        Role role = customRole(2L, "客服");
        when(adminUserMapper.selectById(2L)).thenReturn(admin);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleService.effectivePermissionKeys(role)).thenReturn(List.of("/customers"));

        // ACT + ASSERT: PermissionAspect 实时校验 → 403 FORBIDDEN
        mockMvc.perform(post("/api/admin/admins")
                        .header("Authorization", "Bearer " + token.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@dreamy.com\",\"password\":\"pass123\",\"role_id\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    // ===== TC-WEB-ADMIN-010: BLOCKER-2 — 拥有权限的 admin 调用 createAdmin → 放行 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-010 [P0]: 含 /system/admins 权限的 admin 调用 POST /api/admin/admins → 201（BLOCKER-2 放行）")
    void createAdmin_withPermission_returns201() throws Exception {
        // ARRANGE: admin 实时查 DB 权限含 /system/admins
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken("3", "3");
        AdminUser admin = adminUser(3L, "ops@dreamy.com", AdminStatus.ACTIVE);
        Role role = customRole(3L, "运营");
        when(adminUserMapper.selectById(3L)).thenReturn(admin);
        when(roleMapper.selectById(1L)).thenReturn(role);
        when(roleService.effectivePermissionKeys(role)).thenReturn(List.of("/system/admins"));
        when(adminService.createAdminDTO(any(), any(), any(), any()))
                .thenReturn(new AdminDTO(10L, "X", "x@dreamy.com", 1L, "运营", AdminStatus.ACTIVE, null));

        // ACT + ASSERT: 权限校验通过 → 业务执行 → 201（R 包络 data）
        mockMvc.perform(post("/api/admin/admins")
                        .header("Authorization", "Bearer " + token.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@dreamy.com\",\"password\":\"pass123\",\"role_id\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    // ===== TC-WEB-ADMIN-011: BLOCKER-1 — admin 会话撤销后旧 token 访问 → 401 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-011 [P0]: admin 会话撤销后带旧 token 访问 /api/admin/auth/me → 401（BLOCKER-1）")
    void adminMe_revokedSession_returns401() throws Exception {
        // ARRANGE: 签名合法的 admin token，但会话已撤销
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken("1", "1");
        when(sessionValidator.isAdminSessionValid(any())).thenReturn(false);
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ===== TC-WEB-ADMIN-006: NBT-01 CORS Preflight /api/admin/* =====

    @Test
    @DisplayName("TC-WEB-ADMIN-006 [P0]: OPTIONS /api/admin/auth/login origin=5174 → CORS 预检通过（NBT-01）")
    void corsPreflightAdmin_allowedOrigin_returnsHeaders() throws Exception {
        // ASSERT: L1 200/204, L2 ACAO=5174, L4 Allow-Credentials=true
        mockMvc.perform(options("/api/admin/auth/login")
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // ===== TC-WEB-ADMIN-007: NBT-03 非白名单 origin 拒绝 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-007 [P0]: OPTIONS /api/admin/auth/login origin=evil.com → 无 ACAO 头（NBT-03）")
    void corsPreflightAdmin_nonWhitelistOrigin_noAcaoHeader() throws Exception {
        // ASSERT: L2 无 Access-Control-Allow-Origin
        mockMvc.perform(options("/api/admin/auth/login")
                        .header("Origin", "http://evil.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ===== TC-WEB-ADMIN-008: NBT-04 store origin 访问 /api/admin/* → 无 ACAO（CORS 层拒绝） =====

    @Test
    @DisplayName("TC-WEB-ADMIN-008 [P0]: OPTIONS /api/admin/auth/login origin=5173 → 无 ACAO（NBT-04 跨端隔离）")
    void corsPreflightAdmin_storeOrigin_noAcaoHeader() throws Exception {
        // ASSERT: admin CORS 仅允许 5174，5173 被拒
        mockMvc.perform(options("/api/admin/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ===== helpers =====

    private AdminUser adminUser(Long id, String email, AdminStatus status) {
        AdminUser a = new AdminUser();
        a.setId(id);
        a.setName("Admin");
        a.setEmail(email);
        a.setRoleId(1L);
        a.setStatus(status);
        return a;
    }

    private Role customRole(Long id, String name) {
        Role r = new Role();
        r.setId(id);
        r.setName(name);
        r.setType(RoleType.CUSTOM);
        r.setIsLocked(false);
        return r;
    }
}
