package com.dreamy.infra.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Rust server gRPC 共享通道(单一 ManagedChannel,三客户端复用:
 * IdentityGateClient / AuditGateClient / TemplateGateClient)。
 *
 * 常开(惰性连接 + 自动重连):2026-09-17 Java 删码后 IdentityGate/AuditGate/TemplateGate 三通道恒开。
 * 目标地址与身份校验通道同源(IDENTITY_GRPC_ADDR,compose 内网 http://server:18083)。
 */
@Component
public class GrpcChannels {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannels.class);

    private final ManagedChannel channel;

    public GrpcChannels(@Value("${IDENTITY_GRPC_ADDR:http://server:18083}") String addr) {
        String target = addr.replaceFirst("^http://", "");
        this.channel = NettyChannelBuilder.forTarget(target)
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .idleTimeout(300, TimeUnit.SECONDS)
                .maxInboundMessageSize(1024 * 1024)
                .build();
        log.info("[grpc-channels] 共享通道就绪,目标 {}", addr);
    }

    public ManagedChannel channel() {
        return channel;
    }

    @PreDestroy
    void shutdown() {
        channel.shutdown();
        try {
            channel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
