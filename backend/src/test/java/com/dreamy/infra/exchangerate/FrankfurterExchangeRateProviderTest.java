package com.dreamy.infra.exchangerate;

import com.dreamy.port.ExchangeRateProviderPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Frankfurter 响应解析（G）：date/rates 提取；空 rates → ProviderException。 */
class FrankfurterExchangeRateProviderTest {

    private final FrankfurterExchangeRateProvider provider =
            new FrankfurterExchangeRateProvider(new ExchangeRateProperties(), new ObjectMapper());

    @Test
    @DisplayName("parse：{amount,base,date,rates} → RateQuote(quoteDate, rates 大写键 BigDecimal)")
    void parse() {
        ExchangeRateProviderPort.RateQuote q = provider.parse(
                "{\"amount\":1.0,\"base\":\"USD\",\"date\":\"2026-09-05\",\"rates\":{\"AUD\":1.5012,\"EUR\":0.9134,\"GBP\":0.7899,\"CAD\":1.3611}}");
        assertThat(q.quoteDate()).isEqualTo(LocalDate.of(2026, 9, 5));
        assertThat(q.rates()).hasSize(4);
        assertThat(q.rates().get("EUR")).isEqualByComparingTo("0.9134");
        assertThat(provider.name()).isEqualTo("frankfurter");
        assertThat(provider.isManual()).isFalse();
    }

    @Test
    @DisplayName("空 rates / 非法 JSON → ProviderException")
    void parseFailures() {
        assertThatThrownBy(() -> provider.parse("{\"base\":\"USD\",\"rates\":{}}"))
                .isInstanceOf(ExchangeRateProviderPort.ProviderException.class);
        assertThatThrownBy(() -> provider.parse("not json"))
                .isInstanceOf(ExchangeRateProviderPort.ProviderException.class);
    }
}
