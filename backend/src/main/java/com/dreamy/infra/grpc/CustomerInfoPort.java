package com.dreamy.infra.grpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 跨域用户信息访问门面(纯 gRPC 终态;2026-09-17 Java 删码:回滚态 DB 分支已移除)。
 * 订单/退款/展厅/评论/邮件等域读 User 数据的唯一入口——
 * 一律面向 CustomerInfo 值对象,不接触 User Entity。
 * gRPC 不可达 → IdentityUnavailableException(网关层转 HTTP 503 {code:50001})。
 */
@Component
public class CustomerInfoPort {

    private static final Logger log = LoggerFactory.getLogger(CustomerInfoPort.class);

    /** 跨域只读字段集(与 proto UserRecord 同构;provider_uid 等凭证永不出域) */
    public record CustomerInfo(
            Long id, String email, boolean emailVerified, String name, String phone,
            int tier, int status, String avatar, String localePref,
            String joinedAt, boolean anonymized) {

        /** 已匿名化用户(email 脱敏不可达);邮件等消费侧据此跳过 */
        public boolean isAnonymized() {
            return anonymized;
        }
    }

    private final IdentityGateClient gate;

    public CustomerInfoPort(IdentityGateClient gate) {
        this.gate = gate;
    }

    /** 按 id 单查(Redis-first 热路径) */
    public Optional<CustomerInfo> byId(long id) {
        return gate.getUserById(id).map(this::toInfo);
    }

    /** 按 email 单查(v2.2:Rust 路由表两跳) */
    public Optional<CustomerInfo> byEmail(String email) {
        return gate.getUserByEmail(email).map(this::toInfo);
    }

    /** 批量按 id(订单详情/退款列表防 N+1:一次 gRPC ListUsers IN 查询,上限 100) */
    public Map<Long, CustomerInfo> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
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

    /** demo 种子通道(ShowroomSeedInitializer/ReviewSeedInitializer;幂等按 email 归并) */
    public long ensureDemoUser(String email, String name) {
        return gate.ensureDemoUser(email, name);
    }

    private CustomerInfo toInfo(IdentityGateClient.UserRecord r) {
        return new CustomerInfo(r.id(), r.email(), r.emailVerified(), r.name(), r.phone(),
                r.tier(), r.status(), r.avatar(), r.localePref(), r.joinedAt(), r.anonymized());
    }
}
