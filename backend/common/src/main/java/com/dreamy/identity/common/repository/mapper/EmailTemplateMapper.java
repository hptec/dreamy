package com.dreamy.identity.common.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.identity.common.repository.entity.EmailTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * EmailTemplateMapper —— RM-120（uk_template_code_locale）。表 email_template。
 */
@Mapper
public interface EmailTemplateMapper extends BaseMapper<EmailTemplateEntity> {
}
