package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.domain.product.enums.FabricLayerType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 面料层级实体
 * 存储商品的多层面料信息（Shell/Lining/Overlay/Trim）
 *
 * @author fabric-care-module
 * @since 2026-06-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("fabric_layers")
public class FabricLayer {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品ID
     */
    private Long productId;

    /**
     * 层级类型：shell/lining/overlay/trim
     */
    private FabricLayerType layerType;

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
