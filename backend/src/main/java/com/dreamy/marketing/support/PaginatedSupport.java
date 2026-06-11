package com.dreamy.marketing.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import huihao.page.Paginated;

import java.util.List;
import java.util.function.Function;

/**
 * huihao.page.Paginated 装配（线上分页统一六字段 snake_case——MAP-MKT-014 / 契约 R 包络映射章节）。
 */
public final class PaginatedSupport {

    private PaginatedSupport() {
    }

    /** MyBatis-Plus Page → Paginated（行映射） */
    public static <E, D> Paginated<D> of(Page<E> page, Function<E, D> mapper) {
        List<D> items = page.getRecords().stream().map(mapper).toList();
        return of(items, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    /** 内存分页结果 → Paginated */
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
