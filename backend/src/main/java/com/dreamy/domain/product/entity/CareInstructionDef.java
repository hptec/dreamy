package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.consts.CareInstructionDefDBConst;
import com.dreamy.enums.CareCategory;
import com.dreamy.enums.CareStatus;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 care_instruction_def（护理标签字典，标准化护理说明定义）。
 * code 字段具有唯一性约束 uk_care_instruction_def_code。
 * L2 TRACE: catalog-fabric-care-data-detail §1.2 Entity Design / §9 DDL / IDX-FC-002~004。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "care_instruction_def", comment = "护理标签字典（标准化护理说明定义）", indexes = {
        @Index(name = "uk_care_instruction_def_code", columns = {"code"}, unique = true, local = false),
        @Index(name = "idx_cid_category_sort", columns = {"category", "sort_order"}, unique = false, local = false),
        @Index(name = "idx_cid_status", columns = {"status"}, unique = false, local = false)
})
@TableName(value = "care_instruction_def", autoResultMap = true)
public class CareInstructionDef extends LongAuditableEntity {

    @Column(name = CareInstructionDefDBConst.CODE, definition = "varchar(64) NOT NULL COMMENT '唯一标识码（如 WASH_30C）'")
    private String code;

    @Column(name = CareInstructionDefDBConst.SYMBOL_UNICODE, definition = "varchar(16) NULL COMMENT 'Unicode 符号（如 ♲）'")
    private String symbolUnicode;

    @Column(name = CareInstructionDefDBConst.LABEL_EN, definition = "varchar(128) NOT NULL COMMENT '英文标签'")
    private String labelEn;

    @Column(name = CareInstructionDefDBConst.LABEL_ZH, definition = "varchar(128) NOT NULL COMMENT '中文标签'")
    private String labelZh;

    @Column(name = CareInstructionDefDBConst.CATEGORY, definition = "tinyint NOT NULL COMMENT '类别：1=washing/2=bleaching/3=drying/4=ironing/5=dry_cleaning'")
    private CareCategory category;

    @Column(name = CareInstructionDefDBConst.SORT_ORDER, definition = "int NULL COMMENT '同类别内排序'")
    private Integer sortOrder;

    @Column(name = CareInstructionDefDBConst.STATUS, definition = "tinyint NOT NULL DEFAULT 1 COMMENT '状态：1=active/2=disabled'")
    private CareStatus status;
}
