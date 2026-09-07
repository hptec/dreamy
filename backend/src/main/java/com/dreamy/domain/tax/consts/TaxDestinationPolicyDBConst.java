package com.dreamy.domain.tax.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** tax_destination_policy 表列名常量（order-flow-complete §2.2）。 */
public interface TaxDestinationPolicyDBConst extends TradingCommonDBConst {

    String TABLE = "tax_destination_policy";

    String COUNTRY_CODE = "country_code";
    String INCOTERM = "incoterm";
    String DUTIES_NOTICE = "duties_notice";
    String NOTICE_TEXT = "notice_text";
}
