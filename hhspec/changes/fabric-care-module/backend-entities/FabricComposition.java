package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.enums.FabricMaterial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面料成分实体
 * 存储每层面料的具体材料和百分比
 *
 * @author fabric-care-module
 * @since 2026-06-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("fabric_compositions")
public class FabricComposition {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 面料层级ID
     */
    private Long layerId;

    /**
     * 材料枚举值：POLYESTER/SILK/COTTON等
     */
    private FabricMaterial material;

    /**
     * 百分比：0.00-100.00
     */
    private BigDecimal percentage;

    /**
     * 显示顺序
     */
    private Integer displayOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
