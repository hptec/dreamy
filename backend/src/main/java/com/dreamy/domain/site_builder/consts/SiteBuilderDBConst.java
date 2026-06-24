package com.dreamy.domain.site_builder.consts;

/**
 * site_builder 域表列名常量。
 */
public interface SiteBuilderDBConst {

    String HOME_SECTIONS_TABLE = "home_sections";
    String NAVIGATION_ITEMS_TABLE = "navigation_items";
    String FOOTER_COLUMNS_TABLE = "footer_columns";
    String FOOTER_LINKS_TABLE = "footer_links";
    String ANNOUNCEMENTS_TABLE = "announcements";
    String SITE_BUILDER_CONFIG_TABLE = "site_builder_config";

    // 通用列
    String ID = "id";
    String ENABLED = "enabled";
    String SORT_ORDER = "sort_order";
    String VERSION = "version";
    String CREATED_AT = "created_at";
    String UPDATED_AT = "updated_at";
    String CREATED_BY = "created_by";
    String UPDATED_BY = "updated_by";

    // home_sections
    String SECTION_TYPE = "section_type";
    String DATA_JSON = "data_json";
    String I18N_JSON = "i18n_json";
    String LABEL = "label";

    // navigation_items
    String PARENT_ID = "parent_id";
    String LABEL_I18N_KEY = "label_i18n_key";
    String URL = "url";
    String TARGET = "target";
    String LINK_TYPE = "link_type";
    String TAXONOMY_ID = "taxonomy_id";
    String MEGA_MENU_JSON = "mega_menu_json";

    // footer_columns
    String TITLE = "title";

    // footer_links
    String COLUMN_ID = "column_id";

    // announcements
    String PRIORITY = "priority";
    String START_AT = "start_at";
    String END_AT = "end_at";
    String CONTENT = "content";
    String CONTENT_I18N_JSON = "content_i18n_json";

    // site_builder_config
    String NAVIGATION_VERSION = "navigation_version";
    String FOOTER_VERSION = "footer_version";
}
