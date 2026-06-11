package com.dreamy.showroom.domain.member.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dreamy.showroom.domain.member.entity.ShowroomComment;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 留言仓储（RM-SHR-060~062）。
 * nickname 派生在服务层联 member 批查完成（RM-SHR-061：单次 IN 防 N+1，NP-SHR-001）。
 * L2 TRACE: showroom-data-detail §2 ShowroomCommentRepository / IDX-SHR-010。
 */
@Repository
public class ShowroomCommentRepository {

    private final ShowroomCommentMapper commentMapper;

    public ShowroomCommentRepository(ShowroomCommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    /** RM-SHR-060 insert —— E-SHR-11 */
    public void insert(ShowroomComment comment) {
        commentMapper.insert(comment);
    }

    /** RM-SHR-061 listByItems —— 单次 IN 批查，ORDER BY created_at ASC（nickname 联表派生归服务层） */
    public List<ShowroomComment> listByItems(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return commentMapper.selectList(new LambdaQueryWrapper<ShowroomComment>()
                .in(ShowroomComment::getShowroomItemId, itemIds)
                .orderByAsc(ShowroomComment::getCreatedAt)
                .orderByAsc(ShowroomComment::getId));
    }

    /** RM-SHR-062 deleteByItems —— 级联（E-SHR-09 / TX-SHR-003） */
    public void deleteByItems(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        commentMapper.delete(new LambdaQueryWrapper<ShowroomComment>()
                .in(ShowroomComment::getShowroomItemId, itemIds));
    }
}
