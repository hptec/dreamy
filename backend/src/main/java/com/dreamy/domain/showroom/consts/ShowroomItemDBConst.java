package com.dreamy.domain.showroom.consts;

import com.dreamy.consts.ShowroomCommonDBConst;

/** showroom_item 表列名常量。L2 TRACE: showroom-data-detail §9 DDL-2 */
public interface ShowroomItemDBConst extends ShowroomCommonDBConst {

    String TABLE = "showroom_item";

    String PRODUCT_ID = "product_id";
    String COLOR = "color";
    String LAST_ORDERED_AT = "last_ordered_at";
}
