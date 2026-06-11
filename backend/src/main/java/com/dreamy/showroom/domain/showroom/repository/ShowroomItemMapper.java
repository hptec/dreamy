package com.dreamy.showroom.domain.showroom.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import org.apache.ibatis.annotations.Mapper;

/** ShowroomItemMapper。表 showroom_item。 */
@Mapper
public interface ShowroomItemMapper extends BaseMapper<ShowroomItem> {
}
