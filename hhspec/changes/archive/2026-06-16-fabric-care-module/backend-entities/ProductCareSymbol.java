package com.dreamy.domain.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品护理标签实体
 * 存储商品关联的 ISO 3758 护理符号
 *
 * @author fabric-care-module
 * @since 2026-06-14
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("product_care_symbols")
public class ProductCareSymbol {

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
     * 护理符号ID：washing_machine_30等
     */
    private String symbolId;

    /**
     * 显示顺序
     */
    private Integer displayOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
