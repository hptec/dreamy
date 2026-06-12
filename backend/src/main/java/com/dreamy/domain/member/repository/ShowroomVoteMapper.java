package com.dreamy.domain.member.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.member.entity.ShowroomVote;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** ShowroomVoteMapper。表 showroom_vote。 */
@Mapper
public interface ShowroomVoteMapper extends BaseMapper<ShowroomVote> {

    /**
     * RM-SHR-050 PUT 幂等 UPSERT（uk_sv_member_item 承载；同值重放零变更、改值覆盖）。
     * L2 TRACE: E-SHR-10 STEP-SHR-04 / CV-SHR-005。
     */
    @Insert("INSERT INTO showroom_vote (showroom_item_id, member_id, vote, created_at, updated_at) "
            + "VALUES (#{itemId}, #{memberId}, #{vote}, NOW(3), NOW(3)) "
            + "ON DUPLICATE KEY UPDATE vote = #{vote}, updated_at = NOW(3)")
    int upsertVote(@Param("itemId") Long itemId, @Param("memberId") Long memberId, @Param("vote") Integer vote);
}
