package com.dreamy.domain.browse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.browse.entity.BrowseHistory;
import org.apache.ibatis.annotations.Mapper;

/** BrowseHistoryMapper。表 browse_history。 */
@Mapper
public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {
}
