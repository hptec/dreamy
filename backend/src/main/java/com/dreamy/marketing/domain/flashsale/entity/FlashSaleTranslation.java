package com.dreamy.marketing.domain.flashsale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dreamy.marketing.domain.flashsale.consts.FlashSaleTranslationDBConst;
import huihao.mysql.annotation.Column;
import huihao.mysql.annotation.Index;
import huihao.mysql.annotation.Table;
import huihao.mysql.auditable.LongAuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 表 flash_sale_translation（闪购多语言附表，locale ∈ {es,fr}）。
 * L2 TRACE: marketing-data-detail §11 DDL-16 / IDX-MKT-017。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "flash_sale_translation", comment = "闪购多语言附表", indexes = {
        @Index(name = "uk_fst", columns = {"flash_sale_id", "locale"}, unique = true, local = false)
})
@TableName(value = "flash_sale_translation", autoResultMap = true)
public class FlashSaleTranslation extends LongAuditableEntity {

    @Column(name = FlashSaleTranslationDBConst.FLASH_SALE_ID, definition = "bigint NOT NULL COMMENT '逻辑外键 flash_sale.id'")
    private Long flashSaleId;

    @Column(name = FlashSaleTranslationDBConst.LOCALE, definition = "varchar(8) NOT NULL COMMENT 'es|fr'")
    private String locale;

    @Column(name = FlashSaleTranslationDBConst.NAME, definition = "varchar(64) NULL")
    private String name;
}
