package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.dreamy.enums.ProductStatus;
import com.dreamy.domain.product.consts.ProductDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表 product（商品主档，现货+定制双模式）。
 * 冗余回写列 sales_30d/sales_refreshed_at/rating_avg/rating_count 仅 EVT-CAT-001/002 消费者与
 * EVT-CAT-003 定时任务可写（RM-CAT-098/099 专用），管理端整单覆盖（TX-CAT-002）不得触碰。
 * FULLTEXT 索引 ft_product_search(name, subtitle) WITH PARSER ngram（IDX-CAT-004）由
 * CatalogFulltextIndexInitializer 落地（huihao @Index 不支持 FULLTEXT）。
 * L2 TRACE: catalog-data-detail §1.2/§9 DDL-11 / IDX-CAT-001~005 / TASK-010 / TASK-040 product_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "product", comment = "商品主档（现货+定制双模式）", indexes = {
        @Index(name = "uk_product_slug", columns = {"slug"}, unique = true, local = false),
        @Index(name = "idx_product_status_category", columns = {"status", "category_id"}, unique = false, local = false),
        @Index(name = "idx_product_status_created", columns = {"status", "created_at"}, unique = false, local = false),
        @Index(name = "idx_product_sales", columns = {"status", "sales_30d"}, unique = false, local = false)
})
@TableName(value = "product", autoResultMap = true)
public class Product extends LongAuditableEntity {

    @Column(name = ProductDBConst.NAME, definition = "varchar(128) NOT NULL COMMENT '商品名(EN 基准)'")
    private String name;

    @Column(name = ProductDBConst.SLUG, definition = "varchar(128) NOT NULL COMMENT 'URL slug ^[a-z0-9-]+$'")
    private String slug;

    @Column(name = ProductDBConst.SUBTITLE, definition = "varchar(255) NULL COMMENT '副标题/卖点'")
    private String subtitle;

    @Column(name = ProductDBConst.CATEGORY_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 category.id（CV-CAT-005）'")
    private Long categoryId;

    @Column(name = ProductDBConst.PRODUCT_TYPE, definition = "varchar(64) NULL")
    private String productType;

    @Column(name = ProductDBConst.DESCRIPTION, definition = "text NULL COMMENT '富文本介绍'")
    private String description;

    @Column(name = ProductDBConst.DESIGNER_NOTE, definition = "text NULL COMMENT '品牌故事'")
    private String designerNote;

    @Column(name = ProductDBConst.PRICE, definition = "decimal(12,2) NOT NULL COMMENT '现价 USD 基准'")
    private BigDecimal price;

    @Column(name = ProductDBConst.COMPARE_AT, definition = "decimal(12,2) NULL COMMENT '划线价 >= price（应用层校验 V-CAT-027）'")
    private BigDecimal compareAt;

    @Column(name = ProductDBConst.INSTALLMENT, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT 'Klarna/Afterpay 分期'")
    private Boolean installment;

    @Column(name = ProductDBConst.MULTI_CURRENCY_PRICES, definition = "json NULL COMMENT '每币种覆盖价 {CAD: 99.0,...}'")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, BigDecimal> multiCurrencyPrices;

    @Column(name = ProductDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已上架'")
    private ProductStatus status;

    @Column(name = ProductDBConst.IS_NEW, definition = "tinyint(1) NOT NULL DEFAULT 0")
    private Boolean isNew;

    @Column(name = ProductDBConst.IS_BEST, definition = "tinyint(1) NOT NULL DEFAULT 0")
    private Boolean isBest;

    @Column(name = ProductDBConst.RECOMMEND, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '手动推荐标记（best_sellers 冷启动回退）'")
    private Boolean recommend;

    @Column(name = ProductDBConst.SORT, definition = "int NOT NULL DEFAULT 0")
    private Integer sort;

    @Column(name = ProductDBConst.LEAD_TIME_DAYS, definition = "int NOT NULL DEFAULT 1 COMMENT '标准发货周期(天) >=1'")
    private Integer leadTimeDays;

    @Column(name = ProductDBConst.RUSH_AVAILABLE, definition = "tinyint(1) NOT NULL DEFAULT 0")
    private Boolean rushAvailable;

    @Column(name = ProductDBConst.CUSTOM_SIZE_AVAILABLE, definition = "tinyint(1) NOT NULL DEFAULT 0 COMMENT '定制尺寸开关（A-007）'")
    private Boolean customSizeAvailable;

    @Column(name = ProductDBConst.STYLE_NO, definition = "varchar(32) NULL COMMENT '款式编号'")
    private String styleNo;

    @Column(name = ProductDBConst.SEO_TITLE, definition = "varchar(128) NULL")
    private String seoTitle;

    @Column(name = ProductDBConst.SEO_DESC, definition = "varchar(255) NULL")
    private String seoDesc;

    /** 冗余回写列：仅 EVT-CAT-001/003 可写（决策 29）。
     * MP camel→underscore 不在字母/数字边界插下划线（sales30d → sales30d），与 DDL 列 sales_30d
     * 不一致，须显式 @TableField 指定列名。 */
    @TableField("sales_30d")
    @Column(name = ProductDBConst.SALES_30D, definition = "int NOT NULL DEFAULT 0 COMMENT '近30天已支付销量（EVT-CAT-001/003 回写，决策29）'")
    private Integer sales30d;

    @Column(name = ProductDBConst.SALES_REFRESHED_AT, definition = "datetime(3) NULL COMMENT '销量窗口刷新时间'")
    private LocalDateTime salesRefreshedAt;

    /** 冗余回写列：仅 EVT-CAT-002 可写（FLOW-P14） */
    @Column(name = ProductDBConst.RATING_AVG, definition = "decimal(3,2) NOT NULL DEFAULT 0 COMMENT '已通过评价均分（EVT-CAT-002 回写）'")
    private BigDecimal ratingAvg;

    @Column(name = ProductDBConst.RATING_COUNT, definition = "int NOT NULL DEFAULT 0 COMMENT '已通过评价数（EVT-CAT-002 回写）'")
    private Integer ratingCount;
}
