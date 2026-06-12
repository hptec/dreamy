package com.dreamy.domain.member.consts;

import com.dreamy.consts.ShowroomCommonDBConst;

/** showroom_member 表列名常量。L2 TRACE: showroom-data-detail §9 DDL-3 */
public interface ShowroomMemberDBConst extends ShowroomCommonDBConst {

    String TABLE = "showroom_member";

    String NICKNAME = "nickname";
    String EMAIL = "email";
    String ASSIGNED_ITEM_ID = "assigned_item_id";
    String ASSIGN_STATUS = "assign_status";
    String LINKED_CUSTOMER_ID = "linked_customer_id";
}
