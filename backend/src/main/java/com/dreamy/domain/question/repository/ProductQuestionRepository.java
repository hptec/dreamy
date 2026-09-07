package com.dreamy.domain.question.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.entity.ProductQuestion;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 商品 Q&A 仓储（RM-REV-030~035）。
 * L2 TRACE: review-data-detail §2 ProductQuestionRepository / IDX-REV-005。
 */
@Repository
public class ProductQuestionRepository {

    private final ProductQuestionMapper questionMapper;

    public ProductQuestionRepository(ProductQuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    /**
     * RM-REV-030 pageVisibleAnsweredByProduct —— 前台双条件过滤（CV-REV-009：
     * visible='visible' AND answer IS NOT NULL，未回答即使 visible 也不出前台）ORDER BY asked_at DESC（E-REV-03）。
     */
    public Page<ProductQuestion> pageVisibleAnsweredByProduct(Long productId, int page, int pageSize) {
        return questionMapper.selectPage(new Page<>(page, pageSize), new LambdaQueryWrapper<ProductQuestion>()
                .eq(ProductQuestion::getProductId, productId)
                .eq(ProductQuestion::getVisible, QuestionVisibility.VISIBLE)
                .isNotNull(ProductQuestion::getAnswer)
                .orderByDesc(ProductQuestion::getAskedAt));
    }

    /**
     * RM-REV-031 pageByAdminFilter —— answered 映射 answer IS [NOT] NULL（E-REV-13，后台全量视角）；
     * search 四路 OR（asker/question/answer LIKE + product_id IN 商品名命中集——与评价三路同型）。
     */
    public Page<ProductQuestion> pageByAdminFilter(Long productId, Boolean answered, String search,
                                                   Collection<Long> searchProductIds, int page, int pageSize) {
        LambdaQueryWrapper<ProductQuestion> qw = new LambdaQueryWrapper<>();
        if (productId != null) {
            qw.eq(ProductQuestion::getProductId, productId);
        }
        if (answered != null) {
            if (answered) {
                qw.isNotNull(ProductQuestion::getAnswer);
            } else {
                qw.isNull(ProductQuestion::getAnswer);
            }
        }
        if (search != null && !search.isBlank()) {
            String s = search.trim();
            boolean hasProductIds = searchProductIds != null && !searchProductIds.isEmpty();
            qw.and(w -> {
                w.like(ProductQuestion::getAsker, s)
                        .or().like(ProductQuestion::getQuestion, s)
                        .or().like(ProductQuestion::getAnswer, s);
                if (hasProductIds) {
                    w.or().in(ProductQuestion::getProductId, searchProductIds);
                }
            });
        }
        qw.orderByDesc(ProductQuestion::getAskedAt);
        return questionMapper.selectPage(new Page<>(page, pageSize), qw);
    }

    /** 未回答总数（chips 角标，不随筛选变化——对齐评价 countPending） */
    public long countUnanswered() {
        return questionMapper.selectCount(new LambdaQueryWrapper<ProductQuestion>()
                .isNull(ProductQuestion::getAnswer));
    }

    /** RM-REV-032 findById —— 404802 */
    public ProductQuestion findById(Long id) {
        return id == null ? null : questionMapper.selectById(id);
    }

    /** RM-REV-033 insert —— visible='hidden', answer=NULL（E-REV-04） */
    public void insert(ProductQuestion question) {
        questionMapper.insert(question);
    }

    /**
     * RM-REV-034 saveAnswer —— CAS 并发防护（对齐评价 bs-591 同型）：
     * expectAnswer=null（首答）→ WHERE answer IS NULL，原子附加 visible='visible'
     * （首答翻转单 UPDATE 完成 TX-REV-008）；非 null（编辑）→ WHERE answer=expect 旧值，
     * 他人先写 → affected=0 → 409805 冲突；edit_answer 保持 visible 现值（手动隐藏不被覆盖）。
     */
    public int saveAnswer(Long id, String answer, LocalDateTime answerTime, String expectAnswer) {
        LambdaUpdateWrapper<ProductQuestion> uw = new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, id);
        if (expectAnswer == null) {
            uw.isNull(ProductQuestion::getAnswer);
        } else {
            uw.eq(ProductQuestion::getAnswer, expectAnswer);
        }
        uw.set(ProductQuestion::getAnswer, answer)
                .set(ProductQuestion::getAnswerTime, answerTime);
        if (expectAnswer == null) {
            uw.set(ProductQuestion::getVisible, QuestionVisibility.VISIBLE);
        }
        return questionMapper.update(null, uw);
    }

    /** RM-REV-035 updateVisible —— E-REV-15（未回答提问允许置 visible，前台双条件过滤兜底） */
    public void updateVisible(Long id, QuestionVisibility visible) {
        questionMapper.update(null, new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, id)
                .set(ProductQuestion::getVisible, visible));
    }

    /** 清空回答（撤回——对齐评价 clearReply；幂等由 service 层 answer==null 短路兜底） */
    public void clearAnswer(Long id) {
        questionMapper.update(null, new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, id)
                .set(ProductQuestion::getAnswer, null)
                .set(ProductQuestion::getAnswerTime, null));
    }

    /** 批量读取（批量可见性分拣——对齐评价 listByIds） */
    public List<ProductQuestion> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectList(new LambdaQueryWrapper<ProductQuestion>().in(ProductQuestion::getId, ids));
    }

    /** 批量可见性逐条 CAS（并发漂移 affected=0 → skipped——对齐评价 casBatchTransit） */
    public int casBatchVisible(Long id, QuestionVisibility from, QuestionVisibility to) {
        return questionMapper.update(null, new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, id)
                .eq(ProductQuestion::getVisible, from)
                .set(ProductQuestion::getVisible, to));
    }

    /** 种子幂等判定（决策 21） */
    public long countAll() {
        return questionMapper.selectCount(null);
    }
}
