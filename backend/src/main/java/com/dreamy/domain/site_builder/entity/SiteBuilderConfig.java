package com.dreamy.domain.site_builder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.site_builder.consts.SiteBuilderDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站点装修配置单例（id 恒为 1）。存导航/页脚整体版本号用于乐观锁。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "site_builder_config", comment = "站点装修配置单例")
@TableName(value = "site_builder_config", autoResultMap = true)
public class SiteBuilderConfig extends LongAuditableEntity {

    @Column(name = SiteBuilderDBConst.ID, definition = "bigint NOT NULL DEFAULT 1")
    private Long id;

    @Column(name = SiteBuilderDBConst.NAVIGATION_VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '导航整体版本（乐观锁）'")
    private Integer navigationVersion;

    @Column(name = SiteBuilderDBConst.FOOTER_VERSION, definition = "int NOT NULL DEFAULT 0 COMMENT '页脚整体版本（乐观锁）'")
    private Integer footerVersion;

    @Column(name = SiteBuilderDBConst.UPDATED_AT, definition = "datetime NOT NULL")
    private LocalDateTime updatedAt;
}
