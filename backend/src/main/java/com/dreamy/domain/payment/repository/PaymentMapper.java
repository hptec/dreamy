package com.dreamy.domain.payment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dreamy.domain.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/** PaymentMapper。表 payment。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
