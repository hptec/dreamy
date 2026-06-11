package com.dreamy.analytics.repository.readmodel;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** RM-ANA-004 按日 GMV 聚合行（只读 readmodel，不依赖他域实体——决策 3 例外口径②）。 */
@Data
public class DailyGmvRow {

    /** UTC 日（DATE(paid_at)） */
    private LocalDate day;

    /** 当日 GMV（USD 基准折算） */
    private BigDecimal gmvUsd;
}
