package com.dreamy.domain.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.blog.consts.BlogPostTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 blog_post_translation（博客多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-4 / IDX-MKT-012。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "blog_post_translation", comment = "博客多语言附表", indexes = {
        @Index(name = "uk_bpt", columns = {"blog_post_id", "locale"}, unique = true, local = false)
})
@TableName(value = "blog_post_translation", autoResultMap = true)
public class BlogPostTranslation extends LongAuditableEntity {

    @Column(name = BlogPostTranslationDBConst.BLOG_POST_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 blog_post.id'")
    private Long blogPostId;

    @Column(name = BlogPostTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = BlogPostTranslationDBConst.TITLE, definition = "varchar(200) NULL")
    private String title;

    @Column(name = BlogPostTranslationDBConst.EXCERPT, definition = "varchar(500) NULL")
    private String excerpt;

    @Column(name = BlogPostTranslationDBConst.BODY, definition = "text NULL")
    private String body;

    @Column(name = BlogPostTranslationDBConst.SEO_TITLE, definition = "varchar(128) NULL")
    private String seoTitle;

    @Column(name = BlogPostTranslationDBConst.SEO_DESCRIPTION, definition = "varchar(255) NULL")
    private String seoDescription;
}
