package com.dreamy.showroom.domain.showroom.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberMapper;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.entity.ShowroomItem;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协作空间仓储（RM-SHR-001~010）。
 * owner 强隔离唯一入口 findByIdAndOwner（CV-SHR-007，跨用户 404101 防探测）。
 * L2 TRACE: showroom-data-detail §2 ShowroomRepository / IDX-SHR-001/002/011。
 */
@Repository
public class ShowroomRepository {

    private final ShowroomMapper showroomMapper;
    private final ShowroomItemMapper itemMapper;
    private final ShowroomMemberMapper memberMapper;

    public ShowroomRepository(ShowroomMapper showroomMapper, ShowroomItemMapper itemMapper,
                              ShowroomMemberMapper memberMapper) {
        this.showroomMapper = showroomMapper;
        this.itemMapper = itemMapper;
        this.memberMapper = memberMapper;
    }

    /** RM-SHR-001 insert —— E-SHR-01（uk_showroom_invite 冲突由调用方重生成 UUID 重插一次） */
    public void insert(Showroom showroom) {
        showroomMapper.insert(showroom);
    }

    /** RM-SHR-002 findByIdAndOwner —— owner 强隔离点查（404101，CV-SHR-007，全部 owner 路径唯一入口） */
    public Showroom findByIdAndOwner(Long id, Long ownerId) {
        if (id == null || ownerId == null) {
            return null;
        }
        return showroomMapper.selectOne(new LambdaQueryWrapper<Showroom>()
                .eq(Showroom::getId, id)
                .eq(Showroom::getOwnerId, ownerId));
    }

    /** RM-SHR-003 findById —— guest 视图装配 + ShowroomGuestValidator 版本校验（0.2-d） */
    public Showroom findById(Long id) {
        return id == null ? null : showroomMapper.selectById(id);
    }

    /** RM-SHR-004 listByOwner —— E-SHR-02，ORDER BY created_at DESC（IDX-SHR-002） */
    public List<Showroom> listByOwner(Long ownerId) {
        return showroomMapper.selectList(new LambdaQueryWrapper<Showroom>()
                .eq(Showroom::getOwnerId, ownerId)
                .orderByDesc(Showroom::getCreatedAt));
    }

    /** RM-SHR-005 updateProfile —— E-SHR-04（PUT 全量覆盖，weddingDate 可置 NULL） */
    public void updateProfile(Long id, String name, LocalDate weddingDate) {
        showroomMapper.update(null, new LambdaUpdateWrapper<Showroom>()
                .eq(Showroom::getId, id)
                .set(Showroom::getName, name)
                .set(Showroom::getWeddingDate, weddingDate));
    }

    /** RM-SHR-006 resetInvite —— 单语句原子（token 轮转 + prev 单代保留 + version 自增，TX-SHR-004） */
    public int resetInvite(Long id, String newToken) {
        return showroomMapper.resetInvite(id, newToken);
    }

    /** RM-SHR-007 findByInviteToken —— E-SHR-07 当前值 uk 点查（IDX-SHR-001） */
    public Showroom findByInviteToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return showroomMapper.selectOne(new LambdaQueryWrapper<Showroom>()
                .eq(Showroom::getInviteToken, token));
    }

    /** RM-SHR-007b existsByInviteTokenPrev —— E-SHR-07 STEP-SHR-02 重置识别（410101；IDX-SHR-011） */
    public boolean existsByInviteTokenPrev(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return showroomMapper.selectCount(new LambdaQueryWrapper<Showroom>()
                .eq(Showroom::getInviteTokenPrev, token)) > 0;
    }

    /** RM-SHR-008 deleteById —— TX-SHR-003 级联尾步 */
    public void deleteById(Long id) {
        showroomMapper.deleteById(id);
    }

    /** 派生计数对（RM-SHR-009 输出形状） */
    public record SummaryCounts(int itemCount, int memberCount) {
    }

    /**
     * RM-SHR-009 countSummary —— 两条 GROUP BY IN 批查派生 item_count/member_count
     * （E-SHR-02 STEP-SHR-02，NP-SHR-001 防 N+1）。
     */
    public Map<Long, SummaryCounts> countSummary(Collection<Long> showroomIds) {
        Map<Long, SummaryCounts> result = new HashMap<>();
        if (showroomIds == null || showroomIds.isEmpty()) {
            return result;
        }
        Map<Long, Integer> itemCounts = groupCount(itemMapper.selectMaps(
                new QueryWrapper<ShowroomItem>()
                        .select("showroom_id", "COUNT(*) AS cnt")
                        .in("showroom_id", showroomIds)
                        .groupBy("showroom_id")));
        Map<Long, Integer> memberCounts = groupCount(memberMapper.selectMaps(
                new QueryWrapper<ShowroomMember>()
                        .select("showroom_id", "COUNT(*) AS cnt")
                        .in("showroom_id", showroomIds)
                        .groupBy("showroom_id")));
        for (Long id : showroomIds) {
            result.put(id, new SummaryCounts(itemCounts.getOrDefault(id, 0), memberCounts.getOrDefault(id, 0)));
        }
        return result;
    }

    /**
     * RM-SHR-010 listIdsByCustomerParticipation —— owner_id=:cid UNION
     * member.linked_customer_id=:cid 的 showroom_id（DyeLotPort 与 EVT-SHR-003 参与域判定）。
     */
    public List<Long> listIdsByCustomerParticipation(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        showroomMapper.selectList(new LambdaQueryWrapper<Showroom>()
                        .select(Showroom::getId)
                        .eq(Showroom::getOwnerId, customerId))
                .forEach(s -> ids.add(s.getId()));
        memberMapper.selectList(new LambdaQueryWrapper<ShowroomMember>()
                        .select(ShowroomMember::getShowroomId)
                        .eq(ShowroomMember::getLinkedCustomerId, customerId))
                .forEach(m -> ids.add(m.getShowroomId()));
        return List.copyOf(ids);
    }

    private Map<Long, Integer> groupCount(List<Map<String, Object>> rows) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("showroom_id");
            Object cnt = row.get("cnt");
            if (id instanceof Number n && cnt instanceof Number c) {
                counts.put(n.longValue(), c.intValue());
            }
        }
        return counts;
    }

    /** 种子幂等判定（showroom 表非空即跳过，决策 21） */
    public long countAll() {
        return showroomMapper.selectCount(null);
    }
}
