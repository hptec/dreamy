package com.dreamy.domain.showroom.consts;

import com.dreamy.consts.ShowroomCommonDBConst;

/** showroom 表列名常量。L2 TRACE: showroom-data-detail §9 DDL-1 */
public interface ShowroomDBConst extends ShowroomCommonDBConst {

    String TABLE = "showroom";

    String OWNER_ID = "owner_id";
    String NAME = "name";
    String WEDDING_DATE = "wedding_date";
    String INVITE_TOKEN = "invite_token";
    String INVITE_TOKEN_PREV = "invite_token_prev";
    String INVITE_VERSION = "invite_version";
}
