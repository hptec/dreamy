package com.dreamy.infra.mail;

/** mail_record 表列名常量（CP-015 范式）。L3 修复轮 TRACE: FUNC-016/019 / FLOW-P11 */
public interface MailRecordDBConst {

    String TABLE = "mail_record";

    String ID = "id";
    String TYPE = "type";
    String RECIPIENT = "recipient";
    String LOCALE = "locale";
    String PAYLOAD = "payload";
    String STATUS = "status";
    String RETRY_COUNT = "retry_count";
    String EVENT_ID = "event_id";
    String SENT_AT = "sent_at";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
}
