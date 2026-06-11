package com.dreamy.showroom.domain.member.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.showroom.domain.member.entity.ShowroomComment;
import org.apache.ibatis.annotations.Mapper;

/** ShowroomCommentMapper。表 showroom_comment。 */
@Mapper
public interface ShowroomCommentMapper extends BaseMapper<ShowroomComment> {
}
