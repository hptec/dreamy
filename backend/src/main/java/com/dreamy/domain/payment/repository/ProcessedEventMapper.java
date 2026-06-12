package com.dreamy.domain.payment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.payment.entity.ProcessedEvent;
import org.apache.ibatis.annotations.Mapper;

/** ProcessedEventMapper。表 processed_event。 */
@Mapper
public interface ProcessedEventMapper extends BaseMapper<ProcessedEvent> {
}
