package com.dreamy.domain.question.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.enums.QuestionVisibility;
import com.dreamy.domain.question.entity.ProductQuestion;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

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

    /** RM-REV-031 pageByAdminFilter —— answered 映射 answer IS [NOT] NULL（E-REV-13，后台全量视角） */
    public Page<ProductQuestion> pageByAdminFilter(Long productId, Boolean answered, int page, int pageSize) {
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
        qw.orderByDesc(ProductQuestion::getAskedAt);
        return questionMapper.selectPage(new Page<>(page, pageSize), qw);
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
     * RM-REV-034 saveAnswer —— firstAnswer=true 时附加 visible='visible'
     * （E-REV-14 STEP-REV-02 两分支单方法承载；首答翻转原子完成 TX-REV-008）。
     */
    public int saveAnswer(Long id, String answer, LocalDateTime answerTime, boolean firstAnswer) {
        LambdaUpdateWrapper<ProductQuestion> uw = new LambdaUpdateWrapper<ProductQuestion>()
                .eq(ProductQuestion::getId, id)
                .set(ProductQuestion::getAnswer, answer)
                .set(ProductQuestion::getAnswerTime, answerTime);
        if (firstAnswer) {
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

    /** 种子幂等判定（决策 21） */
    public long countAll() {
        return questionMapper.selectCount(null);
    }
}
