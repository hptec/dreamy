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
@Table(name = "footer_links", comment = "页脚链接", indexes = {
        @Index(name = "idx_footer_links_column_sort", columns = {"column_id", "sort_order", "id"}, unique = false, local = false),
        @Index(name = "idx_footer_links_url", columns = {"url"}, unique = false, local = false)
})
@TableName(value = "footer_links", autoResultMap = true)
public class FooterLink extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.COLUMN_ID, definition = "bigint NOT NULL COMMENT '所属栏目 id'")
    private Long columnId;

    @Column(name = SiteBuilderDBConst.LABEL, definition = "varchar(255) NOT NULL COMMENT 'EN 基准标签'")
    private String label;

    @Column(name = SiteBuilderDBConst.URL, definition = "varchar(512) NOT NULL COMMENT 'HTTP(S) URL'")
    private String url;

    @Column(name = SiteBuilderDBConst.TARGET, definition = "varchar(16) NOT NULL DEFAULT 'self'")
    private String target;

    @Column(name = SiteBuilderDBConst.I18N_JSON, definition = "json NULL")
    private String i18nJson;

    @Column(name = SiteBuilderDBConst.SORT_ORDER, definition = "int NOT NULL DEFAULT 0")
    private Integer sortOrder;

    @Column(name = SiteBuilderDBConst.VERSION, definition = "int NOT NULL DEFAULT 0")
    private Integer version;
}
