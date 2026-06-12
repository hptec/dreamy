package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.SizeChartRowDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 表 size_chart_row（商品尺码对照表行，决策 20.3 尺码推荐数据源）。
 * L2 TRACE: catalog-data-detail §9 DDL-15 / IDX-CAT-009 / TASK-013。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "size_chart_row", comment = "商品尺码对照表行（决策20.3 推荐数据源）", indexes = {
        @Index(name = "idx_scr_product", columns = {"product_id"}, unique = false, local = false)
})
@TableName(value = "size_chart_row", autoResultMap = true)
public class SizeChartRow extends LongAuditableEntity {

    @Column(name = SizeChartRowDBConst.PRODUCT_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 product.id'")
    private Long productId;

    @Column(name = SizeChartRowDBConst.US, definition = "varchar(8) NOT NULL")
    private String us;

    @Column(name = SizeChartRowDBConst.UK, definition = "varchar(8) NULL")
    private String uk;

    @Column(name = SizeChartRowDBConst.AU, definition = "varchar(8) NULL")
    private String au;

    @Column(name = SizeChartRowDBConst.BUST, definition = "decimal(6,2) NULL COMMENT '胸围(in)'")
    private BigDecimal bust;

    @Column(name = SizeChartRowDBConst.WAIST, definition = "decimal(6,2) NULL COMMENT '腰围(in)'")
    private BigDecimal waist;

    @Column(name = SizeChartRowDBConst.HIPS, definition = "decimal(6,2) NULL COMMENT '臀围(in)'")
    private BigDecimal hips;

    @Column(name = SizeChartRowDBConst.HOLLOW_TO_FLOOR, definition = "decimal(6,2) NULL COMMENT '中空到地(in)'")
    private BigDecimal hollowToFloor;
}
