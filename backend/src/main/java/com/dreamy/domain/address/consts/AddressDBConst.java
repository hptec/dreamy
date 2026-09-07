package com.dreamy.domain.address.consts;

import com.dreamy.consts.TradingCommonDBConst;

/** address 表列名常量。L2 TRACE: trading-data-detail §9 DDL-3 */
public interface AddressDBConst extends TradingCommonDBConst {

    String TABLE = "address";

    String RECEIVER = "receiver";
    String PHONE = "phone";
    String LINE = "line";
    String CITY = "city";
    String STATE = "state";
    String ZIP = "zip";
    String COUNTRY = "country";
    String COUNTRY_CODE = "country_code";
    String REGION_CODE = "region_code";
    String IS_DEFAULT = "is_default";
}
