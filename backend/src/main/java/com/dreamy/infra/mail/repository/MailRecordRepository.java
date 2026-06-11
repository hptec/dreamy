package com.dreamy.infra.mail.repository;

import com.dreamy.infra.mail.MailRecord;
import com.dreamy.infra.mail.MailStatus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 邮件发送记录仓储（FLOW-P11；huihao 范式：Repository 收口 Mapper，业务侧不直查表）。
 * 幂等键 event_id 唯一索引兜底并发重投（insert DuplicateKeyException → 调用方回读）。
 */
@Repository
public class MailRecordRepository {

    private final MailRecordMapper mailRecordMapper;

    public MailRecordRepository(MailRecordMapper mailRecordMapper) {
        this.mailRecordMapper = mailRecordMapper;
    }

    /** event_id 幂等回查（消费重入/重投判定 sent|dead 跳过） */
    public MailRecord findByEventId(String eventId) {
        if (eventId == null) {
            return null;
        }
        return mailRecordMapper.selectOne(new LambdaQueryWrapper<MailRecord>()
                .eq(MailRecord::getEventId, eventId)
                .last("LIMIT 1"));
    }

    /** 落表 pending（uk_mail_record_event 冲突向上抛 DuplicateKeyException，调用方回读幂等） */
    public void insert(MailRecord record) {
        mailRecordMapper.insert(record);
    }

    /** pending|failed → sent（同写 sent_at，FLOW-P11 成功分支） */
    public void markSent(Long id, LocalDateTime sentAt) {
        mailRecordMapper.update(null, new LambdaUpdateWrapper<MailRecord>()
                .eq(MailRecord::getId, id)
                .set(MailRecord::getStatus, MailStatus.SENT)
                .set(MailRecord::getSentAt, sentAt));
    }

    /** 失败分支：retry_count 覆盖写 + failed（重投重入再尝试）/ dead（超上限，告警人工补发） */
    public void markFailure(Long id, MailStatus status, int retryCount) {
        mailRecordMapper.update(null, new LambdaUpdateWrapper<MailRecord>()
                .eq(MailRecord::getId, id)
                .set(MailRecord::getStatus, status)
                .set(MailRecord::getRetryCount, retryCount));
    }
}
