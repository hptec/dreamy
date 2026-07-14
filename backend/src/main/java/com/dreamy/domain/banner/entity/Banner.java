package com.dreamy.domain.banner.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.banner.consts.BannerDBConst;
import com.dreamy.enums.BannerPosition;
import com.dreamy.enums.ContentStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 banner（站点广告位：首页 Hero/推荐位/顶部条）。基类 LongAuditableEntity（决策 12，无逻辑删除——全态可物理删除）。
 * 投放窗口 start_time/end_time：消费端读路径恒按窗口过滤（DEC-MKT-2，状态不随窗口翻转）；
 * clicks 只读统计列（本期无写入端点）；EN 文案列 title/subtitle/cta_text（DEC-MKT-1）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-1 / IDX-MKT-007 / TASK-007 / TASK-032 banner_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "banner", comment = "站点广告位（首页Hero/推荐位/顶部条）", indexes = {
        @Index(name = "idx_banner_status_position", columns = {"status", "position"}, unique = false, local = false)
})
@TableName(value = "banner", autoResultMap = true)
public class Banner extends LongAuditableEntity {

    @Column(name = BannerDBConst.NAME, definition = "varchar(128) NOT NULL COMMENT '内部名称'")
    private String name;

    @Column(name = BannerDBConst.IMAGE_URL, definition = "varchar(512) NOT NULL COMMENT '预签名上传 public_url（scope=banner）'")
    private String imageUrl;

    @Column(name = BannerDBConst.POSITION, definition = "tinyint NOT NULL COMMENT '位置：1=首屏大图 2=精选位 3=顶栏'")
    private BannerPosition position;

    @Column(name = BannerDBConst.START_TIME, definition = "datetime(3) NULL COMMENT '投放开始（空=立即）'")
    private LocalDateTime startTime;

    @Column(name = BannerDBConst.END_TIME, definition = "datetime(3) NULL COMMENT '投放结束（空=长期）；读路径窗口过滤（DEC-MKT-2）'")
    private LocalDateTime endTime;

    @Column(name = BannerDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布 3=已归档'")
    private ContentStatus status;

    @Column(name = BannerDBConst.SORT, definition = "int NOT NULL DEFAULT 0 COMMENT '排序'")
    private Integer sort;

    @Column(name = BannerDBConst.CLICKS, definition = "int NOT NULL DEFAULT 0 COMMENT '点击统计只读（本期无写入端点）'")
    private Integer clicks;

    @Column(name = BannerDBConst.TITLE, definition = "varchar(255) NULL COMMENT '文案标题(EN 基准，DEC-MKT-1)'")
    private String title;

    @Column(name = BannerDBConst.SUBTITLE, definition = "varchar(255) NULL COMMENT '文案副题(EN 基准)'")
    private String subtitle;

    @Column(name = BannerDBConst.CTA_TEXT, definition = "varchar(64) NULL COMMENT 'CTA 文案(EN 基准)'")
    private String ctaText;

    @Column(name = "cta_link", definition = "varchar(512) NULL COMMENT 'CTA 链接(EN 基准)'")
    private String ctaLink;

    @Column(name = "cta_text_secondary", definition = "varchar(64) NULL COMMENT '次要 CTA 文案(EN 基准)'")
    private String ctaTextSecondary;

    @Column(name = "cta_link_secondary", definition = "varchar(512) NULL COMMENT '次要 CTA 链接(EN 基准)'")
    private String ctaLinkSecondary;

}
