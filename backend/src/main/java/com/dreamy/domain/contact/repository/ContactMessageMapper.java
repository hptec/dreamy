package com.dreamy.domain.contact.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.contact.entity.ContactMessage;
import org.apache.ibatis.annotations.Mapper;

/** ContactMessageMapper。表 contact_message（RM-MKT-141 由 Repository 封装）。 */
@Mapper
public interface ContactMessageMapper extends BaseMapper<ContactMessage> {
}
