package com.dreamy.domain.lookbook.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.lookbook.entity.Lookbook;
import org.apache.ibatis.annotations.Mapper;

/** LookbookMapper（RM-MKT-060~074 由 LookbookRepository 封装）。 */
@Mapper
public interface LookbookMapper extends BaseMapper<Lookbook> {
}
