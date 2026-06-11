package com.dreamy.showroom.domain.member.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dreamy.showroom.domain.enums.VoteValue;
import com.dreamy.showroom.domain.member.entity.ShowroomVote;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 投票仓储（RM-SHR-050~053）。
 * L2 TRACE: showroom-data-detail §2 ShowroomVoteRepository / IDX-SHR-008/009 / NP-SHR-002。
 */
@Repository
public class ShowroomVoteRepository {

    private final ShowroomVoteMapper voteMapper;

    public ShowroomVoteRepository(ShowroomVoteMapper voteMapper) {
        this.voteMapper = voteMapper;
    }

    /** 票数聚合对（RM-SHR-051 输出形状） */
    public record VoteCounts(int likeCount, int dislikeCount) {
        public static final VoteCounts ZERO = new VoteCounts(0, 0);
    }

    /** RM-SHR-050 upsert —— PUT 幂等核心（uk_sv_member_item；同值重放零变更、改值覆盖） */
    public void upsert(Long itemId, Long memberId, VoteValue vote) {
        voteMapper.upsertVote(itemId, memberId, vote.getKey());
    }

    /**
     * RM-SHR-051 aggregateByItems —— 单条 GROUP BY 内存汇总（NP-SHR-002，禁止逐 item 逐枚举 COUNT）。
     */
    public Map<Long, VoteCounts> aggregateByItems(Collection<Long> itemIds) {
        Map<Long, VoteCounts> result = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> rows = voteMapper.selectMaps(new QueryWrapper<ShowroomVote>()
                .select("showroom_item_id", "vote", "COUNT(*) AS cnt")
                .in("showroom_item_id", itemIds)
                .groupBy("showroom_item_id", "vote"));
        for (Map<String, Object> row : rows) {
            Object itemId = row.get("showroom_item_id");
            Object vote = row.get("vote");
            Object cnt = row.get("cnt");
            if (!(itemId instanceof Number itemNum) || !(cnt instanceof Number cntNum)) {
                continue;
            }
            VoteCounts current = result.getOrDefault(itemNum.longValue(), VoteCounts.ZERO);
            if (VoteValue.LIKE.getKey().equals(String.valueOf(vote))) {
                result.put(itemNum.longValue(),
                        new VoteCounts(current.likeCount() + cntNum.intValue(), current.dislikeCount()));
            } else if (VoteValue.DISLIKE.getKey().equals(String.valueOf(vote))) {
                result.put(itemNum.longValue(),
                        new VoteCounts(current.likeCount(), current.dislikeCount() + cntNum.intValue()));
            }
        }
        return result;
    }

    /** RM-SHR-052 listByMemberAndItems —— my_vote 批查 */
    public Map<Long, VoteValue> listByMemberAndItems(Long memberId, Collection<Long> itemIds) {
        Map<Long, VoteValue> result = new HashMap<>();
        if (memberId == null || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        for (ShowroomVote vote : voteMapper.selectList(new LambdaQueryWrapper<ShowroomVote>()
                .eq(ShowroomVote::getMemberId, memberId)
                .in(ShowroomVote::getShowroomItemId, itemIds))) {
            result.put(vote.getShowroomItemId(), vote.getVote());
        }
        return result;
    }

    /** RM-SHR-053 deleteByItems —— 级联（E-SHR-09 / TX-SHR-003） */
    public void deleteByItems(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        voteMapper.delete(new LambdaQueryWrapper<ShowroomVote>()
                .in(ShowroomVote::getShowroomItemId, itemIds));
    }
}
