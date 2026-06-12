package com.dreamy.domain.showroom.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.showroom.entity.Showroom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** ShowroomMapper。表 showroom。 */
@Mapper
public interface ShowroomMapper extends BaseMapper<Showroom> {

    /**
     * RM-SHR-006 邀请重置单语句原子：token 轮转 + prev 单代保留 + version 自增（TX-SHR-004）。
     * L2 TRACE: E-SHR-06 STEP-SHR-02 / CV-SHR-008。
     */
    @Update("UPDATE showroom SET invite_token_prev = invite_token, invite_token = #{newToken}, "
            + "invite_version = invite_version + 1, updated_at = NOW(3) WHERE id = #{id}")
    int resetInvite(@Param("id") Long id, @Param("newToken") String newToken);
}
