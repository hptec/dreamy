package com.dreamy.identity.admin.controller;

import com.dreamy.identity.admin.aspect.AuditAspect;
import com.dreamy.identity.admin.aspect.AuditLog;
import com.dreamy.identity.admin.config.AdminConfig;
import com.dreamy.identity.admin.filter.AdminJwtFilter;
import com.dreamy.identity.common.domain.service.AdminService;
import com.dreamy.identity.common.domain.service.AuditService;
import com.dreamy.identity.common.domain.service.RoleService;
import com.dreamy.identity.common.dto.AdminDTO;
import com.dreamy.identity.common.dto.mapper.IdentityDtoMapper;
import com.dreamy.identity.common.error.BizException;
import com.dreamy.identity.common.error.ErrorCode;
import com.dreamy.identity.common.error.GlobalExceptionHandler;
import com.dreamy.identity.common.i18n.MessageResolver;
import com.dreamy.identity.common.repository.entity.AdminUserEntity;
import com.dreamy.identity.common.repository.entity.RoleEntity;
import com.dreamy.identity.common.security.AuthContext;
import com.dreamy.identity.common.security.AuthPrincipal;
import com.dreamy.identity.common.security.JwtProperties;
import com.dreamy.identity.common.security.JwtTokenProvider;
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
@WebMvcTest(controllers = {AdminAuthController.class})
@Import({AdminJwtFilter.class, AdminConfig.class, GlobalExceptionHandler.class,
        com.dreamy.identity.admin.aspect.PermissionAspect.class,
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
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtTokenProvider jwtTokenProvider;

    @MockitoBean AdminService adminService;
    @MockitoBean RoleService roleService;
    @MockitoBean IdentityDtoMapper mapper;
    // AuditAspect 依赖 AuditService；WebMvcTest 不加载 AOP，但 @AuditLog 注解在 Controller 上
    // 需要 mock AuditService 以防 Spring 上下文找不到 bean（若 AuditAspect 被扫描）
    @MockitoBean AuditService auditService;
    // BLOCKER-1：AdminJwtFilter 依赖 SessionValidator 校验 admin 会话有效性，默认有效
    @MockitoBean com.dreamy.identity.common.infra.SessionValidator sessionValidator;

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
        AdminUserEntity admin = adminUser("admin-1", "admin@dreamy.com", "active");
        RoleEntity role = customRole("role-1", "运营");
        AdminService.LoginOutcome outcome = new AdminService.LoginOutcome(
                admin, role, List.of("/system/admins"), "mock-token");
        when(adminService.login(eq("admin@dreamy.com"), eq("pass123"), any(), any()))
                .thenReturn(outcome);
        when(roleService.findById("role-1")).thenReturn(role);

        // ACT + ASSERT: L1 200, L2 token 存在, L3 admin/permission_keys, L4 admin.email
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@dreamy.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-token"))
                .andExpect(jsonPath("$.admin.email").value("admin@dreamy.com"))
                .andExpect(jsonPath("$.permission_keys[0]").value("/system/admins"));
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
        // ARRANGE: 签发真实 admin token，手动设置 AuthContext（过滤器在 WebMvcTest 中运行）
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                "admin-1", "role-1", List.of("/system/admins"));
        when(adminService.meData(eq("admin-1"), any())).thenReturn(
                new AdminService.MeData(
                        new AdminDTO("admin-1", "Admin", "admin@dreamy.com", "role-1", "运营", "active", null),
                        "运营", false));

        // ACT + ASSERT: L1 200, L2 admin 存在, L3 role_name/is_super/permission_keys, L4 is_super=false
        mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + token.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin.id").value("admin-1"))
                .andExpect(jsonPath("$.role_name").value("运营"))
                .andExpect(jsonPath("$.is_super").value(false))
                .andExpect(jsonPath("$.permission_keys[0]").value("/system/admins"));
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
        com.dreamy.identity.common.security.TokenPair storePair =
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
        // ARRANGE: 低权 admin token，permission_keys 仅含 /customers（不含 /system/admins）
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                "admin-low", "role-low", List.of("/customers"));

        // ACT + ASSERT: PermissionAspect 在业务执行前短路 → 403 FORBIDDEN
        mockMvc.perform(post("/api/admin/admins")
                        .header("Authorization", "Bearer " + token.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@dreamy.com\",\"password\":\"pass123\",\"roleId\":\"role-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    // ===== TC-WEB-ADMIN-010: BLOCKER-2 — 拥有权限的 admin 调用 createAdmin → 放行 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-010 [P0]: 含 /system/admins 权限的 admin 调用 POST /api/admin/admins → 201（BLOCKER-2 放行）")
    void createAdmin_withPermission_returns201() throws Exception {
        // ARRANGE: admin token 含 /system/admins
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                "admin-ok", "role-ok", List.of("/system/admins"));
        when(adminService.createAdminDTO(any(), any(), any(), any()))
                .thenReturn(new AdminDTO("new-1", "X", "x@dreamy.com", "role-1", "运营", "active", null));

        // ACT + ASSERT: 权限校验通过 → 业务执行 → 201
        mockMvc.perform(post("/api/admin/admins")
                        .header("Authorization", "Bearer " + token.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"x@dreamy.com\",\"password\":\"pass123\",\"roleId\":\"role-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-1"));
    }

    // ===== TC-WEB-ADMIN-011: BLOCKER-1 — admin 会话撤销后旧 token 访问 → 401 =====

    @Test
    @DisplayName("TC-WEB-ADMIN-011 [P0]: admin 会话撤销后带旧 token 访问 /api/admin/auth/me → 401（BLOCKER-1）")
    void adminMe_revokedSession_returns401() throws Exception {
        // ARRANGE: 签名合法的 admin token，但会话已撤销
        JwtTokenProvider.AdminToken token = jwtTokenProvider.issueAdminToken(
                "admin-1", "role-1", List.of("/system/admins"));
        when(sessionValidator.isAdminSessionValid(any())).thenReturn(false);

        // ACT + ASSERT: 过滤器校验会话失效 → 401
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

    private AdminUserEntity adminUser(String id, String email, String status) {
        AdminUserEntity a = new AdminUserEntity();
        a.setId(id);
        a.setName("Admin");
        a.setEmail(email);
        a.setRoleId("role-1");
        a.setStatus(status);
        return a;
    }

    private RoleEntity customRole(String id, String name) {
        RoleEntity r = new RoleEntity();
        r.setId(id);
        r.setName(name);
        r.setType("custom");
        r.setIsLocked(false);
        return r;
    }
}
