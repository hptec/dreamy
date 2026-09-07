package com.dreamy.domain.tax.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** tax_rule 表列名常量（order-flow-complete §2.2）。 */
public interface TaxRuleDBConst extends TradingCommonDBConst {

    String TABLE = "tax_rule";

    String COUNTRY_CODE = "country_code";
    String REGION = "region";
    String TAX_TYPE = "tax_type";
    String RATE_SCALED = "rate_scaled";
    String APPLIES_TO_SHIPPING = "applies_to_shipping";
    String THRESHOLD_USD = "threshold_usd";
    String EFFECTIVE_FROM = "effective_from";
    String EFFECTIVE_TO = "effective_to";
    String ENABLED = "enabled";
    String LABEL = "label";
}
