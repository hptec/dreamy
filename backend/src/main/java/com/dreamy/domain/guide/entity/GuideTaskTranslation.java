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
@Table(name = "guide_task_translation", comment = "Wedding Guide 待办任务多语言", indexes = {
        @Index(name = "uk_gtt", columns = {"task_id", "locale"}, unique = true, local = false)
})
@TableName("guide_task_translation")
public class GuideTaskTranslation extends LongAuditableEntity {
    @Column(name = "task_id", definition = "bigint NOT NULL")
    private Long taskId;
    @Column(name = "locale", definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;
    @Column(name = "label", definition = "varchar(256) NOT NULL")
    private String label;
}
