package com.dreamy.domain.wedding.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.wedding.consts.RealWeddingTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 real_wedding_translation（案例多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-6 / IDX-MKT-013。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "real_wedding_translation", comment = "案例多语言附表", indexes = {
        @Index(name = "uk_rwt", columns = {"real_wedding_id", "locale"}, unique = true, local = false)
})
@TableName(value = "real_wedding_translation", autoResultMap = true)
public class RealWeddingTranslation extends LongAuditableEntity {

    @Column(name = RealWeddingTranslationDBConst.REAL_WEDDING_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 real_wedding.id'")
    private Long realWeddingId;

    @Column(name = RealWeddingTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = RealWeddingTranslationDBConst.TITLE, definition = "varchar(200) NULL")
    private String title;

    @Column(name = RealWeddingTranslationDBConst.STORY, definition = "text NULL")
    private String story;
}
