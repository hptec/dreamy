package com.dreamy.infra.exchangerate;

import com.dreamy.port.ExchangeRateProviderPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** manual 模式供应商（无外部行情；refresh → 409905 由服务层判定 isManual）。 */
@Component
@ConditionalOnProperty(name = "dreamy.exchange-rate.mode", havingValue = "manual", matchIfMissing = true)
public class StubExchangeRateProvider implements ExchangeRateProviderPort {

    @Override
    public String name() {
        return "manual";
    }

    @Override
    public boolean isManual() {
        return true;
    }

    @Override
    public RateQuote fetchLatest(String base, List<String> currencies) {
        throw new ProviderException("manual mode has no exchange rate provider", null);
    }
}
