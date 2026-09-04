package com.dreamy.domain.guide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "user_guide_task", comment = "消费者 Wedding Guide 任务进度")
@TableName("user_guide_task")
public class UserGuideTask extends LongAuditableEntity {
    @Column(name = "user_id", definition = "bigint NOT NULL") private Long userId;
    @Column(name = "guide_id", definition = "bigint NOT NULL") private Long guideId;
    @Column(name = "task_id", definition = "bigint NOT NULL") private Long taskId;
}
