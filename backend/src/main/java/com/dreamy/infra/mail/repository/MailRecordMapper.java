package com.dreamy.infra.mail.repository;

import com.dreamy.infra.mail.MailRecord;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** MailRecordMapper。表 mail_record。 */
@Mapper
public interface MailRecordMapper extends BaseMapper<MailRecord> {
}
