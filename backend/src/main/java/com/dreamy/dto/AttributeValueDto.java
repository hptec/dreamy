package com.dreamy.dto;

import java.util.List;

/**
 * 商品动态属性 entries 数组载荷（admin 提交/回读共用）。
 * entries 数组而非 map：动态 key 作为字段值传输，免疫前端 snake/camel 递归 key 转换。
 * select/text/toggle 单元素数组；multiselect 多元素；toggle 取 "true"/"false"。
 */
public record AttributeValueDto(String key, List<String> values) {
}
