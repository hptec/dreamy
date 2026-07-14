package com.dreamy.it;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.domain.admin.entity.AdminUser;
import com.dreamy.domain.admin.repository.AdminUserMapper;
import com.dreamy.domain.admin.service.AdminService;
import com.dreamy.domain.role.entity.Role;
import com.dreamy.domain.role.repository.RoleMapper;
import com.dreamy.domain.session.entity.AdminSession;
import com.dreamy.domain.session.repository.AdminSessionMapper;
import com.dreamy.enums.AdminStatus;
import com.dreamy.enums.RoleType;
import com.dreamy.enums.SessionStatus;
import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.infra.AdminSessionValidityCache;
import com.dreamy.infra.SessionValidator;
import com.dreamy.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAuthConcurrencyIT extends AbstractIT {

    private static final String PASSWORD = "ConcurrentAdmin@123";

    @Autowired AdminService adminService;
    @Autowired AdminUserMapper adminUserMapper;
    @Autowired AdminSessionMapper adminSessionMapper;
    @Autowired RoleMapper roleMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired SessionValidator sessionValidator;
    @Autowired AdminSessionValidityCache validityCache;
    @Autowired PlatformTransactionManager transactionManager;

    private Long adminId;
    private Long roleId;
    private String email;

    @AfterEach
    void cleanUp() {
        if (adminId != null) {
            adminSessionMapper.delete(new LambdaQueryWrapper<AdminSession>()
                    .eq(AdminSession::getAdminId, adminId));
            adminUserMapper.deleteById(adminId);
        }
        if (roleId != null) {
            roleMapper.deleteById(roleId);
        }
    }

    @Test
    @DisplayName("登录先提交、随后禁用：新 token 被撤销且缓存失效")
    void loginThenDisableLeavesNoUsableSession() throws Exception {
        insertAdmin();
        assertLoginFirstIsInvalidatedBy(Mutation.DISABLE);
    }

    @Test
    @DisplayName("禁用先提交、并发登录：登录看到禁用状态且不创建会话")
    void disableThenLoginCannotCreateSession() throws Exception {
        insertAdmin();
        assertMutationFirstRejectsLogin(Mutation.DISABLE, ErrorCode.ADMIN_DISABLED);
    }

    @Test
    @DisplayName("登录先提交、随后删除：管理员和全部会话被物理删除")
    void loginThenDeleteLeavesNoUsableSession() throws Exception {
        insertAdmin();
        assertLoginFirstIsInvalidatedBy(Mutation.DELETE);
    }

    @Test
    @DisplayName("删除先提交、并发登录：登录看不到管理员且不创建孤儿会话")
    void deleteThenLoginCannotCreateOrphanSession() throws Exception {
        insertAdmin();
        assertMutationFirstRejectsLogin(Mutation.DELETE, ErrorCode.CREDENTIALS_INVALID);
    }

    private void assertLoginFirstIsInvalidatedBy(Mutation mutation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch loginReadyToCommit = new CountDownLatch(1);
        CountDownLatch allowLoginCommit = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        try {
            Future<AdminService.LoginOutcome> loginFuture = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        assertThat(adminUserMapper.selectByIdForUpdate(adminId)).isNotNull();
                        AdminService.LoginOutcome outcome = login();
                        loginReadyToCommit.countDown();
                        await(allowLoginCommit);
                        return outcome;
                    }));

            assertThat(loginReadyToCommit.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> mutationFuture = executor.submit(() -> {
                mutationStarted.countDown();
                mutate(mutation);
            });
            assertThat(mutationStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertBlocked(mutationFuture);

            allowLoginCommit.countDown();
            AdminService.LoginOutcome outcome = loginFuture.get(10, TimeUnit.SECONDS);
            mutationFuture.get(10, TimeUnit.SECONDS);

            String tokenId = jwtTokenProvider.parseAdminToken(outcome.token()).tokenId();
            assertNoActiveSession(tokenId);
            if (mutation == Mutation.DISABLE) {
                assertThat(adminUserMapper.selectById(adminId).getStatus()).isEqualTo(AdminStatus.DISABLED);
            } else {
                assertThat(adminUserMapper.selectById(adminId)).isNull();
            }
        } finally {
            allowLoginCommit.countDown();
            executor.shutdownNow();
        }
    }

    private void assertMutationFirstRejectsLogin(Mutation mutation, ErrorCode expectedError) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch mutationReadyToCommit = new CountDownLatch(1);
        CountDownLatch allowMutationCommit = new CountDownLatch(1);
        CountDownLatch loginStarted = new CountDownLatch(1);
        try {
            Future<?> mutationFuture = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        assertThat(adminUserMapper.selectByIdForUpdate(adminId)).isNotNull();
                        mutate(mutation);
                        mutationReadyToCommit.countDown();
                        await(allowMutationCommit);
                    }));

            assertThat(mutationReadyToCommit.await(10, TimeUnit.SECONDS)).isTrue();
            Future<AdminService.LoginOutcome> loginFuture = executor.submit(() -> {
                loginStarted.countDown();
                return login();
            });
            assertThat(loginStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertBlocked(loginFuture);

            allowMutationCommit.countDown();
            mutationFuture.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> loginFuture.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .satisfies(ex -> assertThat(ex.getCause())
                            .isInstanceOf(BizException.class)
                            .satisfies(cause -> assertThat(((BizException) cause).getErrorCode())
                                    .isEqualTo(expectedError)));

            assertThat(activeSessionCount()).isZero();
        } finally {
            allowMutationCommit.countDown();
            executor.shutdownNow();
        }
    }

    private void assertNoActiveSession(String tokenId) {
        assertThat(activeSessionCount()).isZero();
        assertThat(validityCache.isValid(tokenId)).isFalse();
        assertThat(sessionValidator.isAdminSessionValid(tokenId)).isFalse();
    }

    private long activeSessionCount() {
        return adminSessionMapper.selectCount(new LambdaQueryWrapper<AdminSession>()
                .eq(AdminSession::getAdminId, adminId)
                .eq(AdminSession::getStatus, SessionStatus.ACTIVE));
    }

    private AdminService.LoginOutcome login() {
        return adminService.login(email, PASSWORD, "127.0.0.1", "concurrency-it");
    }

    private void mutate(Mutation mutation) {
        if (mutation == Mutation.DISABLE) {
            adminService.toggleStatus(adminId, AdminStatus.DISABLED);
        } else {
            adminService.deleteAdmin(adminId, -1L);
        }
    }

    private void insertAdmin() {
        String suffix = UUID.randomUUID().toString();
        Role role = new Role();
        role.setName("并发测试-" + suffix);
        role.setType(RoleType.CUSTOM);
        role.setIsLocked(false);
        role.setVersion(0);
        roleMapper.insert(role);
        roleId = role.getId();

        email = "admin-concurrency-" + suffix + "@dreamy.test";
        AdminUser admin = new AdminUser();
        admin.setName("并发测试管理员");
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setRoleId(roleId);
        admin.setStatus(AdminStatus.ACTIVE);
        admin.setVersion(0);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
    }

    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrency test timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency test interrupted", ex);
        }
    }

    private enum Mutation {
        DISABLE,
        DELETE
    }
}
