package com.dreamy.domain.outbox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.outbox.entity.EventOutbox;
import org.apache.ibatis.annotations.Mapper;

/** EventOutboxMapper。表 event_outbox。 */
@Mapper
public interface EventOutboxMapper extends BaseMapper<EventOutbox> {
}
