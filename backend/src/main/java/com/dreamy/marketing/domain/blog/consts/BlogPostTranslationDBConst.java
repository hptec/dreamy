package com.dreamy.marketing.domain.blog.consts;

import com.dreamy.marketing.domain.consts.MarketingCommonDBConst;

/** blog_post_translation 表列名常量（CP-015）。L2 TRACE: marketing-data-detail §11 DDL-4 */
public interface BlogPostTranslationDBConst extends MarketingCommonDBConst {

    String TABLE = "blog_post_translation";

    String BLOG_POST_ID = "blog_post_id";
    String EXCERPT = "excerpt";
    String SEO_TITLE = "seo_title";
    String SEO_DESCRIPTION = "seo_description";
}
