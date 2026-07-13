package com.dreamy.domain.site_builder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "home_page_releases", comment = "首页不可变发布快照", indexes = {
        @Index(name = "uk_home_release_no", columns = {"release_no"}, unique = true, local = false),
        @Index(name = "idx_home_release_published_at", columns = {"published_at", "id"}, unique = false, local = false)
})
@TableName(value = "home_page_releases", autoResultMap = true)
public class HomePageRelease extends LongAuditableEntity {

    @Column(name = "release_no", definition = "int NOT NULL COMMENT '单调递增发布序号'")
    private Integer releaseNo;

    @Column(name = "name", definition = "varchar(128) NOT NULL COMMENT '发布说明'")
    private String name;

    @Column(name = "snapshot_json", definition = "json NOT NULL COMMENT '工作区配置快照'")
    private String snapshotJson;

    @Column(name = "content_en_json", definition = "json NOT NULL COMMENT 'EN 最终渲染快照'")
    private String contentEnJson;

    @Column(name = "content_es_json", definition = "json NOT NULL COMMENT 'ES 最终渲染快照'")
    private String contentEsJson;

    @Column(name = "content_fr_json", definition = "json NOT NULL COMMENT 'FR 最终渲染快照'")
    private String contentFrJson;

    @Column(name = "source_release_id", definition = "bigint NULL COMMENT '回滚来源版本'")
    private Long sourceReleaseId;

    @Column(name = "published_by", definition = "bigint NULL COMMENT '发布管理员 id'")
    private Long publishedBy;

    @Column(name = "published_at", definition = "datetime(3) NOT NULL COMMENT '发布时间'")
    private LocalDateTime publishedAt;
}
