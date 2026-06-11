package com.dreamy.showroom.domain.member.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 成员仓储（RM-SHR-030~042）。
 * 状态机 showroom_member_assignment 全部写路径以 CAS 条件更新承载（CP-016 同型，CV-SHR-010）。
 * L2 TRACE: showroom-data-detail §2 ShowroomMemberRepository / IDX-SHR-005/006/007。
 */
@Repository
public class ShowroomMemberRepository {

    private final ShowroomMemberMapper memberMapper;

    public ShowroomMemberRepository(ShowroomMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    /** RM-SHR-030 findByShowroomAndNickname —— E-SHR-07 复用裁决（uk 点查） */
    public ShowroomMember findByShowroomAndNickname(Long showroomId, String nickname) {
        return memberMapper.selectOne(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getShowroomId, showroomId)
                .eq(ShowroomMember::getNickname, nickname));
    }

    /** RM-SHR-031 insert —— uk_sm_room_nickname 冲突向上抛（调用方回读重裁决/后缀重试） */
    public void insert(ShowroomMember member) {
        memberMapper.insert(member);
    }

    /** RM-SHR-032 findByIdAndShowroom —— 归属校验（404103，E-SHR-12/13） */
    public ShowroomMember findByIdAndShowroom(Long memberId, Long showroomId) {
        if (memberId == null || showroomId == null) {
            return null;
        }
        return memberMapper.selectOne(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getId, memberId)
                .eq(ShowroomMember::getShowroomId, showroomId));
    }

    /** RM-SHR-033 listByShowroom —— E-SHR-03（uk 左前缀覆盖） */
    public List<ShowroomMember> listByShowroom(Long showroomId) {
        return memberMapper.selectList(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getShowroomId, showroomId)
                .orderByAsc(ShowroomMember::getId));
    }

    /** RM-SHR-034 findByShowroomAndLinkedCustomer —— my_member 与互动身份解析（IDX-SHR-006） */
    public ShowroomMember findByShowroomAndLinkedCustomer(Long showroomId, Long customerId) {
        if (showroomId == null || customerId == null) {
            return null;
        }
        return memberMapper.selectOne(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getShowroomId, showroomId)
                .eq(ShowroomMember::getLinkedCustomerId, customerId)
                // linked_customer_id 非 uk：理论多行时取最早建行（确定性）
                .orderByAsc(ShowroomMember::getId)
                .last("LIMIT 1"));
    }

    /**
     * RM-SHR-035 casAssign —— assign/reassign CAS（E-SHR-12，affected=0 → 409103；
     * email 未提供保留原值 COALESCE 语义：null 不 set）。
     */
    public int casAssign(Long memberId, Long itemId, String email) {
        LambdaUpdateWrapper<ShowroomMember> uw = new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getId, memberId)
                .in(ShowroomMember::getAssignStatus,
                        AssignStatus.UNASSIGNED, AssignStatus.ASSIGNED, AssignStatus.REMINDED)
                .set(ShowroomMember::getAssignedItemId, itemId)
                .set(ShowroomMember::getAssignStatus, AssignStatus.ASSIGNED);
        if (email != null) {
            uw.set(ShowroomMember::getEmail, email);
        }
        return memberMapper.update(null, uw);
    }

    /** RM-SHR-036 casRemind —— send_reminder CAS（E-SHR-13，email 非空 guard，affected=0 → 409103） */
    public int casRemind(Long memberId) {
        return memberMapper.update(null, new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getId, memberId)
                .in(ShowroomMember::getAssignStatus, AssignStatus.ASSIGNED, AssignStatus.REMINDED)
                .isNotNull(ShowroomMember::getEmail)
                .set(ShowroomMember::getAssignStatus, AssignStatus.REMINDED));
    }

    /** RM-SHR-037 casOrder —— place_order 推进（EVT-SHR-003；已 ordered affected=0 幂等空操作） */
    public int casOrder(Long memberId) {
        return memberMapper.update(null, new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getId, memberId)
                .in(ShowroomMember::getAssignStatus, AssignStatus.ASSIGNED, AssignStatus.REMINDED)
                .set(ShowroomMember::getAssignStatus, AssignStatus.ORDERED));
    }

    /** RM-SHR-038 listByLinkedCustomer —— EVT-SHR-003 消费侧定位（IDX-SHR-006） */
    public List<ShowroomMember> listByLinkedCustomer(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return memberMapper.selectList(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getLinkedCustomerId, customerId));
    }

    /** RM-SHR-039 unassignByItem —— assigned/reminded 回 unassigned + 清引用（E-SHR-09 STEP-SHR-03） */
    public int unassignByItem(Long itemId) {
        return memberMapper.update(null, new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getAssignedItemId, itemId)
                .in(ShowroomMember::getAssignStatus, AssignStatus.ASSIGNED, AssignStatus.REMINDED)
                .set(ShowroomMember::getAssignedItemId, null)
                .set(ShowroomMember::getAssignStatus, AssignStatus.UNASSIGNED));
    }

    /** RM-SHR-040 clearAssignedItemKeepOrdered —— ordered 终态保持仅清悬挂引用 */
    public int clearAssignedItemKeepOrdered(Long itemId) {
        return memberMapper.update(null, new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getAssignedItemId, itemId)
                .eq(ShowroomMember::getAssignStatus, AssignStatus.ORDERED)
                .set(ShowroomMember::getAssignedItemId, null));
    }

    /** RM-SHR-041 bindCustomer —— 幂等绑定回填（E-SHR-07 STEP-SHR-05；已绑定他人不覆盖） */
    public int bindCustomer(Long memberId, Long customerId) {
        return memberMapper.update(null, new LambdaUpdateWrapper<ShowroomMember>()
                .eq(ShowroomMember::getId, memberId)
                .and(w -> w.isNull(ShowroomMember::getLinkedCustomerId)
                        .or()
                        .eq(ShowroomMember::getLinkedCustomerId, customerId))
                .set(ShowroomMember::getLinkedCustomerId, customerId));
    }

    /** RM-SHR-042 deleteByShowroom —— 级联（E-SHR-05 / TX-SHR-003） */
    public void deleteByShowroom(Long showroomId) {
        memberMapper.delete(new LambdaQueryWrapper<ShowroomMember>()
                .eq(ShowroomMember::getShowroomId, showroomId));
    }

    /** 主键回读（E-SHR-12/13 CAS 后回读装配） */
    public ShowroomMember findById(Long memberId) {
        return memberId == null ? null : memberMapper.selectById(memberId);
    }

    /** 种子幂等判定（showroom 域表非空即跳过，决策 21） */
    public long countAll() {
        return memberMapper.selectCount(null);
    }
}
