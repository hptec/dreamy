package com.dreamy.infra.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跨域用户信息访问门面(P4 切换版)。
 * 订单/退款/展厅/评论/邮件等域读 User 数据的唯一入口——
 * 切换后走 IdentityGate gRPC(固定脱敏 UserRecord),回滚态走本地 DB(原 v1)。
 *
 * 使用方一律面向 CustomerInfo 值对象,不接触 User Entity——
 * 这样 P4 删码时只需删回滚态分支,调用方零改动。
 */
@Component
public class CustomerInfoPort {

    private static final Logger log = LoggerFactory.getLogger(CustomerInfoPort.class);

    /** 跨域只读字段集(与 proto UserRecord 同构;provider_uid 等凭证永不出域) */
    public record CustomerInfo(
            Long id, String email, boolean emailVerified, String name, String phone,
            int tier, int status, String avatar, String localePref,
            String joinedAt, boolean anonymized) {
    }

    private final boolean enabled;
    private final IdentityGateClient gate;
    private final Function<Long, com.dreamy.domain.user.entity.User> dbFallback;

    public CustomerInfoPort(
            @Value("${identity.grpc.enabled:false}") boolean enabled,
            IdentityGateClient gate,
            com.dreamy.domain.user.repository.UserMapper userMapper) {
        this.enabled = enabled;
        this.gate = gate;
        this.dbFallback = userMapper::selectById;
    }

    /** 按 id 单查(gRPC 优先;回滚态 DB) */
    public Optional<CustomerInfo> byId(long id) {
        if (enabled) {
            return gate.getUserById(id).map(this::toInfo);
        }
        return Optional.ofNullable(dbFallback.apply(id)).map(this::fromEntity);
    }

    /** 按 email 单查(v2.2:Rust 路由表两跳;回滚态由调用方走 uk 索引) */
    public Optional<CustomerInfo> byEmail(String email) {
        if (enabled) {
            return gate.getUserByEmail(email).map(this::toInfo);
        }
        return Optional.empty();
    }

    /** 批量按 id(订单详情/退款列表防 N+1:一次 gRPC ListUsers IN 查询) */
    public Map<Long, CustomerInfo> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        if (enabled) {
            try {
                dreamy.identity.v1.ListUsersResponse resp = gate.listUsers(
                        List.of(dreamy.identity.v1.Condition.newBuilder()
                                .setColumn(dreamy.identity.v1.UserColumn.USER_COLUMN_ID)
                                .setOp(dreamy.identity.v1.CondOp.COND_OP_IN)
                                .addAllValues(ids.stream().map(String::valueOf).collect(Collectors.toList()))
                                .build()),
                        "id asc", 1, Math.min(ids.size(), 100));
                return resp.getItemsList().stream()
                        .map(r -> new CustomerInfo(r.getId(), r.getEmail(), r.getEmailVerified(), r.getName(),
                                        r.getPhone(), r.getTier(), r.getStatus(), r.getAvatar(),
                                        r.getLocalePref(), r.getJoinedAt(), r.getAnonymized()))
                        .collect(Collectors.toMap(CustomerInfo::id, i -> i, (a, b) -> a));
            } catch (IdentityUnavailableException e) {
                log.error("[customer-port] 批量查询 gRPC 不可达,返回空集(调用方自行降级):{}", e.getMessage());
                return Map.of();
            }
        }
        // 回滚态:DB 批量
        Map<Long, CustomerInfo> out = new HashMap<>();
        for (Long id : ids) {
            byId(id).ifPresent(i -> out.put(i.id(), i));
        }
        return out;
    }

    /** demo 种子通道(ShowroomSeedInitializer/ReviewSeedInitializer) */
    public long ensureDemoUser(String email, String name) {
        if (enabled) {
            return gate.ensureDemoUser(email, name);
        }
        return -1; // 回滚态走原 DB 路径(Initializer 自行处理)
    }

    private CustomerInfo toInfo(IdentityGateClient.UserRecord r) {
        return new CustomerInfo(r.id(), r.email(), r.emailVerified(), r.name(), r.phone(),
                r.tier(), r.status(), r.avatar(), r.localePref(), r.joinedAt(), r.anonymized());
    }

    private CustomerInfo fromEntity(com.dreamy.domain.user.entity.User u) {
        return new CustomerInfo(u.getId(), u.getEmail(), Boolean.TRUE.equals(u.getEmailVerified()),
                u.getName(), u.getPhone(),
                u.getTier() == null ? 1 : u.getTier().getKey(),
                u.getStatus() == null ? 1 : u.getStatus().getKey(),
                u.getAvatar(), u.getLocalePref(), null, Boolean.TRUE.equals(u.getAnonymized()));
    }
}
