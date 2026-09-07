package com.dreamy.controller;

import com.dreamy.domain.order.repository.OrderRepository;
import com.dreamy.domain.order.service.StoreOrderService;
import com.dreamy.domain.payment.repository.PaymentRepository;
import com.dreamy.domain.payment.service.StripeWebhookService;
import com.dreamy.domain.payment.service.StubPaymentConfirmService;
import com.dreamy.infra.stripe.StripeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * order-flow-complete §4.1 第 7 步 / §6.3 第 7 条：stub 支付确认控制器与服务仅在 dreamy.stripe.mode=stub
 * （或缺省）注册；STRIPE_MODE=real 时两个 Bean 均不存在 → 端点 404，不受任何其他开关影响。
 */
class StorePaymentConfirmControllerConditionTest {

    @Configuration
    static class Deps {
        @Bean OrderRepository orderRepository() { return Mockito.mock(OrderRepository.class); }
        @Bean PaymentRepository paymentRepository() { return Mockito.mock(PaymentRepository.class); }
        @Bean StripeWebhookService stripeWebhookService() { return Mockito.mock(StripeWebhookService.class); }
        @Bean StoreOrderService storeOrderService() { return Mockito.mock(StoreOrderService.class); }
        @Bean StripeClient stripeClient() { return Mockito.mock(StripeClient.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Deps.class, StubPaymentConfirmService.class, StorePaymentConfirmController.class);

    @Test
    @DisplayName("mode 缺省 / stub → 控制器与服务 Bean 均注册")
    void registeredInStubMode() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(StubPaymentConfirmService.class);
            assertThat(ctx).hasSingleBean(StorePaymentConfirmController.class);
        });
        runner.withPropertyValues("dreamy.stripe.mode=stub").run(ctx -> {
            assertThat(ctx).hasSingleBean(StubPaymentConfirmService.class);
            assertThat(ctx).hasSingleBean(StorePaymentConfirmController.class);
        });
    }

    @Test
    @DisplayName("mode=real → 两个 Bean 均不存在（端点根本不注册 → 404）")
    void absentInRealMode() {
        runner.withPropertyValues("dreamy.stripe.mode=real").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(StubPaymentConfirmService.class);
            assertThat(ctx).doesNotHaveBean(StorePaymentConfirmController.class);
        });
    }
}
