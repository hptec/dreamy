package com.dreamy.trading.domain.address.consts;

import com.dreamy.trading.domain.consts.TradingCommonDBConst;

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
    String IS_DEFAULT = "is_default";
}
