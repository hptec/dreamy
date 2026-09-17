package com.dreamy.infra.grpc;

import dreamy.identity.v1.CondOp;
import dreamy.identity.v1.Condition;
import dreamy.identity.v1.GetUserRequest;
import dreamy.identity.v1.GetUserResponse;
import dreamy.identity.v1.IdentityGateGrpc;
import dreamy.identity.v1.ListUsersRequest;
import dreamy.identity.v1.ListUsersResponse;
import dreamy.identity.v1.ProviderIdentity;
import dreamy.identity.v1.ResolvePermissionsResponse;
import dreamy.identity.v1.UserColumn;
import dreamy.identity.v1.ValidateAdminSessionResponse;
import dreamy.identity.v1.ValidateStoreSessionResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * IdentityGate gRPC 客户端(Java backend ⇄ Rust server,P4 切换版接线)。
 *
 * 契约纪律(与 proto/dreamy/identity/v1/identity.proto 一致):
 * - deadline 500ms、零重试(会话校验是每请求调用,重试放大雪崩)
 * - UNAVAILABLE/DEADLINE_EXCEEDED → {@link IdentityUnavailableException}(调用方转 HTTP 503)
 * - NOT_FOUND → Optional.empty();INVALID_ARGUMENT → IllegalStateException(编码 BUG,fail fast)
 * - 只传验签后的结构化标识(token_id=jti/admin_id/user_id),绝不传原始 JWT/密码
 *
 * 接线开关:IDENTITY_GRPC_ENABLED(默认 false,P4 切换版启用;
 * false 时全部方法抛 IdentityUnavailableException,保证未接线期间行为可预期)。
 * 通道复用 {@link GrpcChannels} 共享 ManagedChannel(生命周期归其管理)。
 */
@Component
public class IdentityGateClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityGateClient.class);
    private static final long DEADLINE_MS = 500;

    private final boolean enabled;
    private final IdentityGateGrpc.IdentityGateBlockingStub stub;

    public IdentityGateClient(
            @Value("${identity.grpc.enabled:false}") boolean enabled,
            GrpcChannels channels) {
        this.enabled = enabled;
        if (enabled) {
            this.stub = IdentityGateGrpc.newBlockingStub(channels.channel());
            log.info("[identity-grpc] 启用");
        } else {
            this.stub = null;
        }
    }

    // ===== 会话校验(热路径) =====

    /** store 会话有效性(StoreJwtFilter 每请求调用) */
    public boolean validateStoreSession(String tokenId) {
        ValidateStoreSessionResponse resp = call(
                "validateStoreSession",
                stub -> stub.validateStoreSession(
                        dreamy.identity.v1.ValidateStoreSessionRequest.newBuilder()
                                .setTokenId(tokenId).build()));
        return resp.getValid();
    }

    /** admin 会话有效性(含管理员状态复核) */
    public AdminSessionValidity validateAdminSession(String tokenId) {
        ValidateAdminSessionResponse resp = call(
                "validateAdminSession",
                stub -> stub.validateAdminSession(
                        dreamy.identity.v1.ValidateAdminSessionRequest.newBuilder()
                                .setTokenId(tokenId).build()));
        return new AdminSessionValidity(resp.getValid(), resp.getAdminActive());
    }

    public record AdminSessionValidity(boolean valid, boolean adminActive) {
    }

    // ===== 权限解析 =====

    /** 管理员实时权限集(PermissionAspect 调用;admin 不存在 → 空列表) */
    public List<String> resolvePermissions(long adminId) {
        ResolvePermissionsResponse resp = call(
                "resolvePermissions",
                stub -> stub.resolvePermissions(
                        dreamy.identity.v1.ResolvePermissionsRequest.newBuilder()
                                .setAdminId(adminId).build()));
        return resp.getPermissionKeysList();
    }

    // ===== 用户查询(v2.2 路由表两跳) =====

    public Optional<UserRecord> getUserById(long id) {
        return getUser(GetUserRequest.newBuilder().setId(id).build());
    }

    public Optional<UserRecord> getUserByEmail(String email) {
        return getUser(GetUserRequest.newBuilder().setEmail(email).build());
    }

    public Optional<UserRecord> getUserByProvider(int provider, String providerUid) {
        return getUser(GetUserRequest.newBuilder()
                .setProviderIdentity(ProviderIdentity.newBuilder()
                        .setProvider(provider)
                        .setProviderUid(providerUid))
                .build());
    }

    private Optional<UserRecord> getUser(GetUserRequest request) {
        Optional<GetUserResponse> resp = callOptional(
                "getUser",
                stub -> stub.getUser(request));
        return resp.map(r -> new UserRecord(
                r.getRecord().getId(),
                r.getRecord().getEmail(),
                r.getRecord().getEmailVerified(),
                r.getRecord().getName(),
                r.getRecord().getPhone(),
                r.getRecord().getTier(),
                r.getRecord().getStatus(),
                r.getRecord().getAvatar(),
                r.getRecord().getLocalePref(),
                r.getRecord().getJoinedAt(),
                r.getRecord().getAnonymized()));
    }

    /** 脱敏用户记录(固定字段集,provider_uid 等凭证永不出域) */
    public record UserRecord(
            long id, String email, boolean emailVerified, String name, String phone,
            int tier, int status, String avatar, String localePref,
            String joinedAt, boolean anonymized) {
    }

    /** 条件构造(MyBatis-Lambda 式类型化;LIKE 仅 Email 列且拒绝通配符) */
    public static Condition eq(UserColumn column, String value) {
        return Condition.newBuilder()
                .setColumn(column)
                .setOp(CondOp.COND_OP_EQ)
                .addValues(value)
                .build();
    }

    public static Condition likePrefix(String emailPrefix) {
        return Condition.newBuilder()
                .setColumn(UserColumn.USER_COLUMN_EMAIL)
                .setOp(CondOp.COND_OP_LIKE_PREFIX)
                .addValues(emailPrefix)
                .build();
    }

    public ListUsersResponse listUsers(List<Condition> conditions, String orderBy, int page, int pageSize) {
        ListUsersRequest.Builder b = ListUsersRequest.newBuilder()
                .setOrderBy(orderBy == null ? "" : orderBy)
                .setPage(page)
                .setPageSize(pageSize);
        conditions.forEach(b::addConditions);
        return call("listUsers", stub -> stub.listUsers(b.build()));
    }

    // ===== demo 种子通道 =====

    /** 幂等(按 email 归并);返回 user_id */
    public long ensureDemoUser(String email, String name) {
        return call("ensureDemoUser",
                stub -> stub.ensureDemoUser(
                        dreamy.identity.v1.EnsureDemoUserRequest.newBuilder()
                                .setEmail(email).setName(name).build()))
                .getUserId();
    }

    // ===== 统一调用骨架(deadline/异常映射/未接线守卫) =====

    private <T> T call(String rpc, GrpcCall<T> fn) {
        Optional<T> result = callOptional(rpc, fn);
        return result.orElseThrow(() -> new IllegalStateException(
                "IdentityGate." + rpc + " 返回空(契约违约)"));
    }

    private <T> Optional<T> callOptional(String rpc, GrpcCall<T> fn) {
        if (!enabled) {
            throw new IdentityUnavailableException(
                    "IdentityGate 未启用(IDENTITY_GRPC_ENABLED=false):" + rpc, null);
        }
        try {
            return Optional.of(fn.apply(stub.withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)));
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                log.error("[identity-grpc] {} 不可达({}):转 503", rpc, code);
                throw new IdentityUnavailableException(rpc + ":" + code, e);
            }
            if (code == Status.Code.INVALID_ARGUMENT) {
                throw new IllegalStateException("IdentityGate." + rpc + " 参数非法(编码 BUG):"
                        + e.getStatus().getDescription(), e);
            }
            log.error("[identity-grpc] {} 未预期错误:{}", rpc, e.getStatus());
            throw new IllegalStateException("IdentityGate." + rpc + " 未预期失败", e);
        }
    }

    @FunctionalInterface
    private interface GrpcCall<T> {
        T apply(IdentityGateGrpc.IdentityGateBlockingStub stub) throws StatusRuntimeException;
    }
}
