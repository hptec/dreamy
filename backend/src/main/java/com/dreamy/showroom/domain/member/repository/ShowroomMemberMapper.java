package com.dreamy.showroom.domain.member.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import org.apache.ibatis.annotations.Mapper;

/** ShowroomMemberMapper。表 showroom_member。 */
@Mapper
public interface ShowroomMemberMapper extends BaseMapper<ShowroomMember> {
}
