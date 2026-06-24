package com.dreamy.domain.site_builder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.site_builder.consts.SiteBuilderDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 导航项实体（navigation_items 表）。KD-6 mega_menu_json JSON 列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "navigation_items", comment = "导航项配置", indexes = {
        @Index(name = "idx_navigation_items_parent_sort", columns = {"parent_id", "sort_order", "id"}, unique = false, local = false),
        @Index(name = "idx_navigation_items_enabled", columns = {"enabled", "sort_order"}, unique = false, local = false),
        @Index(name = "idx_navigation_items_taxonomy", columns = {"taxonomy_id"}, unique = false, local = false)
})
@TableName(value = "navigation_items", autoResultMap = true)
public class NavigationItem extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.PARENT_ID, definition = "bigint NULL COMMENT '父导航项 id，null=顶级'")
    private Long parentId;

    @Column(name = SiteBuilderDBConst.LABEL, definition = "varchar(255) NOT NULL COMMENT 'EN 基准标签'")
    private String label;

    @Column(name = SiteBuilderDBConst.LABEL_I18N_KEY, definition = "varchar(128) NULL COMMENT 'i18n key（可选）'")
    private String labelI18nKey;

    @Column(name = SiteBuilderDBConst.URL, definition = "varchar(512) NULL COMMENT '自定义 URL'")
    private String url;

    @Column(name = SiteBuilderDBConst.TARGET, definition = "varchar(16) NOT NULL DEFAULT 'self' COMMENT 'target: self/blank'")
    private String target;

    @Column(name = SiteBuilderDBConst.LINK_TYPE, definition = "varchar(16) NOT NULL DEFAULT 'custom' COMMENT 'link_type: custom/taxonomy'")
    private String linkType;

    @Column(name = SiteBuilderDBConst.TAXONOMY_ID, definition = "bigint NULL COMMENT 'taxonomy_id（link_type=taxonomy 时非空）'")
    private Long taxonomyId;

    @Column(name = SiteBuilderDBConst.MEGA_MENU_JSON, definition = "json NULL COMMENT 'Mega Menu 列配置'")
    private String megaMenuJson;

    @Column(name = SiteBuilderDBConst.I18N_JSON, definition = "json NULL COMMENT '多语言 {en:{label},es:{},fr:{}}'")
    private String i18nJson;

    @Column(name = SiteBuilderDBConst.SORT_ORDER, definition = "int NOT NULL DEFAULT 0")
    private Integer sortOrder;

    @Column(name = SiteBuilderDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean enabled;

    @Column(name = SiteBuilderDBConst.VERSION, definition = "int NOT NULL DEFAULT 0")
    private Integer version;
}
