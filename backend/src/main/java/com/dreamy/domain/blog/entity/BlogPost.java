package com.dreamy.domain.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.blog.consts.BlogPostDBConst;
import com.dreamy.enums.ContentStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 表 blog_post（Blog 婚礼策划文章）。slug 可空唯一索引（MySQL 多 NULL 共存——draft 未填不冲突，
 * published 必填 CV-MKT-012）；published_at 首次发布记（republish 不刷新）；
 * views 近似计数仅 SCHED-MKT-02 flush 可写（DEC-MKT-6）。
 * L2 TRACE: marketing-data-detail §1.2/§11 DDL-3 / IDX-MKT-004~006 / TASK-008 / TASK-033 blog_post_lifecycle。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "blog_post", comment = "Blog 婚礼策划文章", indexes = {
        @Index(name = "uk_blog_slug", columns = {"slug"}, unique = true, local = false),
        @Index(name = "idx_blog_status_published", columns = {"status", "published_at"}, unique = false, local = false),
        @Index(name = "idx_blog_category", columns = {"category"}, unique = false, local = false)
})
@TableName(value = "blog_post", autoResultMap = true)
public class BlogPost extends LongAuditableEntity {

    @Column(name = BlogPostDBConst.TITLE, definition = "varchar(200) NOT NULL COMMENT '标题(EN 基准)'")
    private String title;

    @Column(name = BlogPostDBConst.COVER, definition = "varchar(512) NULL")
    private String cover;

    @Column(name = BlogPostDBConst.CATEGORY, definition = "varchar(64) NULL COMMENT '文章栏目'")
    private String category;

    @Column(name = BlogPostDBConst.AUTHOR, definition = "varchar(64) NULL")
    private String author;

    @Column(name = BlogPostDBConst.CONTENT, definition = "text NULL COMMENT '正文(EN 基准)'")
    private String content;

    @Column(name = BlogPostDBConst.SLUG, definition = "varchar(128) NULL COMMENT '静态文章页路径 ^[a-z0-9-]+$；published 必填（CV-MKT-012）'")
    private String slug;

    @Column(name = BlogPostDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=草稿 2=已发布 3=已归档'")
    private ContentStatus status;

    @Column(name = BlogPostDBConst.PUBLISHED_AT, definition = "datetime(3) NULL COMMENT '首次发布时间（republish 不刷新）'")
    private LocalDateTime publishedAt;

    @Column(name = BlogPostDBConst.VIEWS, definition = "int NOT NULL DEFAULT 0 COMMENT '阅读数近似计数（SCHED-MKT-02 flush，DEC-MKT-6）'")
    private Integer views;

    @Column(name = BlogPostDBConst.DELETED_AT, definition = "datetime DEFAULT NULL COMMENT '逻辑删除时间'")
    private LocalDateTime deletedAt;
}
