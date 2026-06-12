package com.dreamy.dto;

/**
 * 通用分页查询入参。
 * 约束: shared-contracts pagination.query_params（page>=1, page_size default 20 max 100）。
 */
public record PageQuery(Integer page, Integer pageSize) {

    public int safePage() {
        return (page == null || page < 1) ? 1 : page;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
