package com.dreamy.domain.guide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "guide_task", comment = "Wedding Guide 待办任务", indexes = {
        @Index(name = "idx_guide_task_guide_sort", columns = {"guide_id", "sort_order"}, unique = false, local = false)
})
@TableName("guide_task")
public class GuideTask extends LongAuditableEntity {
    @Column(name = "guide_id", definition = "bigint NOT NULL") private Long guideId;
    @Column(name = "label", definition = "varchar(256) NOT NULL") private String label;
    @Column(name = "sort_order", definition = "int NOT NULL DEFAULT 0") private Integer sortOrder;
}
