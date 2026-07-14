package com.dreamy.infra.stripe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePropertiesTest {

    @Test
    void realModeRequiresBothSecrets() {
        StripeProperties properties = new StripeProperties();
        properties.setMode("real");

        assertThatThrownBy(properties::validateRealModeSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");

        properties.setSecretKey("sk_test_explicit");
        assertThatThrownBy(properties::validateRealModeSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET");

        properties.setWebhookSecret("whsec_explicit");
        assertThatCode(properties::validateRealModeSecrets).doesNotThrowAnyException();
    }

    @Test
    void stubModeDoesNotRequireExternalSecrets() {
        assertThatCode(new StripeProperties()::validateRealModeSecrets).doesNotThrowAnyException();
    }
}
