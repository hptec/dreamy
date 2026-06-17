package com.dreamy.domain.glossary.entity;

import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.glossary.consts.GlossaryDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 翻术语表实体（ai_translation_glossary 表）。
 * L2 TRACE: i18n-backend-data-detail.md §1.3 / 决策6/14 / FUNC-022。
 * 约束：uk_term_en 唯一、category 分类过滤、enabled 控制注入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "ai_translation_glossary", comment = "翻译术语表")
@TableName(value = "ai_translation_glossary", autoResultMap = true)
public class AiTranslationGlossary extends LongAuditableEntity {

    @Column(name = GlossaryDBConst.TERM_EN, definition = "varchar(128) NOT NULL COMMENT '英文术语'")
    private String termEn;

    @Column(name = GlossaryDBConst.TERM_ES, definition = "varchar(128) DEFAULT NULL COMMENT '西语译法'")
    private String termEs;

    @Column(name = GlossaryDBConst.TERM_FR, definition = "varchar(128) DEFAULT NULL COMMENT '法语译法'")
    private String termFr;

    @Column(name = GlossaryDBConst.CATEGORY, definition = "varchar(32) DEFAULT NULL COMMENT '术语分类'")
    private String category;

    @Column(name = GlossaryDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用'")
    private Boolean enabled;

    @Column(name = GlossaryDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
