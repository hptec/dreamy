package com.dreamy.trading.domain.address.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dreamy.trading.domain.address.entity.Address;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 地址簿仓储（RM-TRD-010~016）。
 * L2 TRACE: trading-data-detail §1 AddressRepository / IDX-TRD-017。
 */
@Repository
public class AddressRepository {

    private final AddressMapper mapper;

    public AddressRepository(AddressMapper mapper) {
        this.mapper = mapper;
    }

    /** RM-TRD-010 listByCustomerId（is_default DESC, id DESC） */
    public List<Address> listByCustomerId(Long customerId) {
        return mapper.selectList(new LambdaQueryWrapper<Address>()
                .eq(Address::getCustomerId, customerId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId));
    }

    /** RM-TRD-011 隔离点查（404602 防探测） */
    public Address findByIdAndCustomerId(Long id, Long customerId) {
        return mapper.selectOne(new LambdaQueryWrapper<Address>()
                .eq(Address::getId, id)
                .eq(Address::getCustomerId, customerId));
    }

    /** RM-TRD-012 clearDefault（TX-TRD-008） */
    public void clearDefault(Long customerId) {
        mapper.update(null, new LambdaUpdateWrapper<Address>()
                .eq(Address::getCustomerId, customerId)
                .eq(Address::getIsDefault, true)
                .set(Address::getIsDefault, false));
    }

    /** RM-TRD-013 insert */
    public void insert(Address address) {
        mapper.insert(address);
    }

    /** RM-TRD-014 全字段覆盖 UPDATE */
    public void updateAll(Address address) {
        mapper.updateById(address);
    }

    /** RM-TRD-015 deleteByIdAndCustomerId（affected=0 → 404602） */
    public int deleteByIdAndCustomerId(Long id, Long customerId) {
        return mapper.delete(new LambdaQueryWrapper<Address>()
                .eq(Address::getId, id)
                .eq(Address::getCustomerId, customerId));
    }

    /** RM-TRD-016 countByCustomerId（首条地址强制默认判定） */
    public long countByCustomerId(Long customerId) {
        return mapper.selectCount(new LambdaQueryWrapper<Address>().eq(Address::getCustomerId, customerId));
    }
}
