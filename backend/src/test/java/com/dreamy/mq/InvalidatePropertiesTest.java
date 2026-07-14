package com.dreamy.mq;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvalidatePropertiesTest {

    @Test
    void realModeRequiresExplicitRevalidateToken() {
        InvalidateProperties properties = new InvalidateProperties();
        properties.setMode("real");

        assertThatThrownBy(properties::validateRealModeToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVALIDATE_TOKEN");

        properties.setRevalidateToken("explicit-test-token");
        assertThatCode(properties::validateRealModeToken).doesNotThrowAnyException();
    }

    @Test
    void stubModeDoesNotRequireToken() {
        assertThatCode(new InvalidateProperties()::validateRealModeToken).doesNotThrowAnyException();
    }
}
