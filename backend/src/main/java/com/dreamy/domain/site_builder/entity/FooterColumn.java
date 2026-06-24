package com.dreamy.domain.site_builder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.site_builder.consts.SiteBuilderDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "footer_columns", comment = "页脚栏目", indexes = {
        @Index(name = "idx_footer_columns_sort", columns = {"sort_order", "id"}, unique = false, local = false)
})
@TableName(value = "footer_columns", autoResultMap = true)
public class FooterColumn extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.TITLE, definition = "varchar(255) NOT NULL COMMENT 'EN 基准标题'")
    private String title;

    @Column(name = SiteBuilderDBConst.I18N_JSON, definition = "json NULL COMMENT '多语言 {en:{title},es:{},fr:{}}'")
    private String i18nJson;

    @Column(name = SiteBuilderDBConst.SORT_ORDER, definition = "int NOT NULL DEFAULT 0")
    private Integer sortOrder;

    @Column(name = SiteBuilderDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean enabled;

    @Column(name = SiteBuilderDBConst.VERSION, definition = "int NOT NULL DEFAULT 0")
    private Integer version;
}
