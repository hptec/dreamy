package com.dreamy.domain.site_builder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.site_builder.consts.SiteBuilderDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "announcements", comment = "公告", indexes = {
        @Index(name = "idx_announcements_priority_id", columns = {"priority", "id"}, unique = false, local = false),
        @Index(name = "idx_announcements_enabled_time", columns = {"enabled", "start_at", "end_at"}, unique = false, local = false),
        @Index(name = "idx_announcements_priority_time", columns = {"priority", "start_at", "end_at"}, unique = false, local = false)
})
@TableName(value = "announcements", autoResultMap = true)
public class Announcement extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.ENABLED, definition = "tinyint(1) NOT NULL DEFAULT 1")
    private Boolean enabled;

    @Column(name = SiteBuilderDBConst.PRIORITY, definition = "int NOT NULL DEFAULT 0 COMMENT '优先级 DESC'")
    private Integer priority;

    @Column(name = SiteBuilderDBConst.START_AT, definition = "datetime NULL COMMENT '时间窗开始'")
    private LocalDateTime startAt;

    @Column(name = SiteBuilderDBConst.END_AT, definition = "datetime NULL COMMENT '时间窗结束'")
    private LocalDateTime endAt;

    @Column(name = SiteBuilderDBConst.CONTENT, definition = "text NULL COMMENT 'EN 基准内容'")
    private String content;

    @Column(name = SiteBuilderDBConst.CONTENT_I18N_JSON, definition = "json NOT NULL COMMENT '公告内容多语言 {en:{content},es:{},fr:{}}'")
    private String contentI18nJson;

    @Column(name = SiteBuilderDBConst.I18N_JSON, definition = "json NULL COMMENT '其他文案多语言'")
    private String i18nJson;

    @Column(name = SiteBuilderDBConst.VERSION, definition = "int NOT NULL DEFAULT 0")
    private Integer version;
}
