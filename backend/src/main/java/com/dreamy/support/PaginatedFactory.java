package com.dreamy.support;

import huihao.page.Paginated;

import java.util.List;

/**
 * huihao.page.Paginated 装配（线上分页统一六字段 snake_case——契约 R 包络映射章节）。
 */
public final class PaginatedFactory {

    private PaginatedFactory() {
    }

    public static <D> Paginated<D> of(List<D> pageItems, long total, int pageNumber, int pageSize) {
        Paginated<D> paginated = new Paginated<>();
        paginated.setData(pageItems);
        paginated.setTotalElements(total);
        paginated.setPageNumber(pageNumber);
        paginated.setPageSize(pageSize);
        paginated.setNumberOfElements(pageItems.size());
        paginated.setTotalPages(pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0);
        return paginated;
    }
}
