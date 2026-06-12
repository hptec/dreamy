package com.dreamy.domain.attribute.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.attribute.consts.AttributeSetItemDBConst;
import com.dreamy.enums.AttributeVisibility;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 attribute_set_item（属性集可见性矩阵明细行）。
 * L2 TRACE: catalog-data-detail §9 DDL-6 / IDX-CAT-015 / TASK-004 / TASK-037（三态整单覆盖承载）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "attribute_set_item", comment = "属性集明细行", indexes = {
        @Index(name = "uk_asi", columns = {"attribute_set_id", "attribute_id"}, unique = true, local = false),
        @Index(name = "idx_asi_attribute", columns = {"attribute_id"}, unique = false, local = false)
})
@TableName(value = "attribute_set_item", autoResultMap = true)
public class AttributeSetItem extends LongAuditableEntity {

    @Column(name = AttributeSetItemDBConst.ATTRIBUTE_SET_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 attribute_set.id'")
    private Long attributeSetId;

    @Column(name = AttributeSetItemDBConst.ATTRIBUTE_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 attribute_def.id'")
    private Long attributeId;

    @Column(name = AttributeSetItemDBConst.VISIBILITY, definition = "tinyint NOT NULL COMMENT '可见性：1=显示 2=可选 3=隐藏'")
    private AttributeVisibility visibility;
}
