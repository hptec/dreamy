package com.dreamy.domain.blog.consts;

import com.dreamy.consts.MarketingCommonDBConst;

/** blog_post 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-3 */
public interface BlogPostDBConst extends MarketingCommonDBConst {

    String TABLE = "blog_post";

    String CATEGORY = "category";
    String AUTHOR = "author";
    String CONTENT = "content";
    String SLUG = "slug";
    String PUBLISHED_AT = "published_at";
    String VIEWS = "views";

    String EXCERPT = "excerpt";
    String SEO_TITLE = "seo_title";
    String SEO_DESCRIPTION = "seo_description";
    String WORD_COUNT = "word_count";
    String READING_MINUTES = "reading_minutes";
    String VERSION = "version";
}
