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
 * 首页区块实体（home_sections 表）。
 * 首页草稿工作区；线上首页只读取 HomePageRelease 不可变快照。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "home_sections", comment = "首页区块配置", indexes = {
        @Index(name = "idx_home_sections_sort_order", columns = {"sort_order", "id"}, unique = false, local = false),
        @Index(name = "idx_home_sections_enabled_sort", columns = {"enabled", "sort_order", "id"}, unique = false, local = false),
        @Index(name = "idx_home_sections_type", columns = {"section_type"}, unique = false, local = false)
})
@TableName(value = "home_sections", autoResultMap = true)
public class HomePageSection extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.SECTION_TYPE, definition = "varchar(32) NOT NULL COMMENT '区块类型：hero/theme_cards/product_rail/editorial_feature/newsletter/custom'")
    private String sectionType;

    @Column(name = SiteBuilderDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态'")
    private Boolean enabled;

    @Column(name = SiteBuilderDBConst.SORT_ORDER, definition = "int NOT NULL DEFAULT 0 COMMENT '排序'")
    private Integer sortOrder;

    @Column(name = SiteBuilderDBConst.DATA_JSON, definition = "json NULL COMMENT '按 section_type 区分的配置数据'")
    private String dataJson;

    @Column(name = SiteBuilderDBConst.I18N_JSON, definition = "json NULL COMMENT '多语言文案 {en:{},es:{},fr:{}}'")
    private String i18nJson;

    @Column(name = SiteBuilderDBConst.LABEL, definition = "varchar(255) NULL COMMENT 'EN 基准标题（冗余便于查询）'")
    private String label;

    @Column(name = SiteBuilderDBConst.VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '乐观锁版本'")
    private Integer version;
}
