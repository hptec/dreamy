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
@Table(name = "home_page_preview_tokens", comment = "首页私有预览令牌", indexes = {
        @Index(name = "uk_home_preview_token_hash", columns = {"token_hash"}, unique = true, local = false),
        @Index(name = "idx_home_preview_expires_at", columns = {"expires_at"}, unique = false, local = false)
})
@TableName(value = "home_page_preview_tokens", autoResultMap = true)
public class HomePagePreviewToken extends LongAuditableEntity {

    @Column(name = "token_hash", definition = "char(64) NOT NULL COMMENT 'SHA-256，不存明文'")
    private String tokenHash;

    @Column(name = "snapshot_json", definition = "json NOT NULL COMMENT '创建令牌时的草稿配置快照'")
    private String snapshotJson;

    @Column(name = "content_en_json", definition = "json NOT NULL COMMENT 'EN 草稿预览快照'")
    private String contentEnJson;

    @Column(name = "content_es_json", definition = "json NOT NULL COMMENT 'ES 草稿预览快照'")
    private String contentEsJson;

    @Column(name = "content_fr_json", definition = "json NOT NULL COMMENT 'FR 草稿预览快照'")
    private String contentFrJson;

    @Column(name = "expires_at", definition = "datetime(3) NOT NULL COMMENT '过期时间'")
    private LocalDateTime expiresAt;

    @Column(name = "issued_by", definition = "bigint NULL COMMENT '签发管理员 id'")
    private Long issuedBy;
}
