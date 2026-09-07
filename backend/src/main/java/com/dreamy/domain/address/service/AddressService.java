package com.dreamy.domain.address.service;

import com.dreamy.domain.address.entity.Address;
import com.dreamy.domain.address.repository.AddressRepository;
import com.dreamy.dto.TradingDtos.AddressDto;
import com.dreamy.dto.TradingDtos.AddressUpsert;
import com.dreamy.error.TradingErrorCode;
import com.dreamy.error.TradingException;
import com.dreamy.infra.TradingTxRunner;
import com.dreamy.support.CountryCatalog;
import com.dreamy.support.TradingFieldErrors;
import com.dreamy.support.TradingParams;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 地址簿服务（trading-api-detail §2；TASK-015）。
 * TX-TRD-008 默认地址切换：clearDefault + insert/update 同事务，保证「恒至多一个 is_default」不变量；
 * 首条地址强制默认（createAddress.STEP-TRD-02）；删除不波及既有订单（address_snapshot 快照）。
 */
@Service
public class AddressService {

    private final AddressRepository addressRepository;
    private final TradingTxRunner txRunner;

    public AddressService(AddressRepository addressRepository, TradingTxRunner txRunner) {
        this.addressRepository = addressRepository;
        this.txRunner = txRunner;
    }

    /** E-listAddresses */
    public List<AddressDto> list(Long customerId) {
        return addressRepository.listByCustomerId(customerId).stream().map(AddressService::toDto).toList();
    }

    /** E-createAddress（V-TRD-011/012 + TX-TRD-008） */
    public AddressDto create(Long customerId, AddressUpsert request) {
        Resolved resolved = validate(request);
        return txRunner.inTx(() -> {
            boolean firstAddress = addressRepository.countByCustomerId(customerId) == 0;
            boolean isDefault = firstAddress || Boolean.TRUE.equals(request.isDefault());
            if (isDefault) {
                addressRepository.clearDefault(customerId);
            }
            Address address = new Address();
            address.setCustomerId(customerId);
            apply(address, request, resolved);
            address.setIsDefault(isDefault);
            addressRepository.insert(address);
            return toDto(address);
        });
    }

    /** E-updateAddress（V-TRD-013 + STEP-TRD-01~03） */
    public AddressDto update(Long customerId, Long id, AddressUpsert request) {
        Resolved resolved = validate(request);
        Address address = addressRepository.findByIdAndCustomerId(id, customerId);
        if (address == null) {
            throw new TradingException(TradingErrorCode.ADDRESS_NOT_FOUND);
        }
        return txRunner.inTx(() -> {
            boolean isDefault = Boolean.TRUE.equals(request.isDefault());
            if (isDefault) {
                addressRepository.clearDefault(customerId);
            }
            apply(address, request, resolved);
            address.setIsDefault(isDefault);
            addressRepository.updateAll(address);
            return toDto(address);
        });
    }

    /** E-deleteAddress（affected=0 → 404602；删除默认地址不自动指定新默认） */
    public void delete(Long customerId, Long id) {
        if (addressRepository.deleteByIdAndCustomerId(id, customerId) == 0) {
            throw new TradingException(TradingErrorCode.ADDRESS_NOT_FOUND);
        }
    }

    /**
     * V-TRD-011/012：必填非空白 + 长度上限（422601 字段级）。
     * order-flow-complete G：country_code 必须在目录内（缺省由 country 文本解析，解析不到 → 422601 country_code）；
     * US/CA/AU 时 region_code（或 state）规范化为标准码（缩写/全称/别名），无法解析 → 422601 region_code。
     */
    private Resolved validate(AddressUpsert request) {
        TradingFieldErrors errors = new TradingFieldErrors();
        TradingParams.requireText(request.receiver(), 64, "receiver", errors);
        TradingParams.checkMaxLength(request.phone(), 32, "phone", errors);
        TradingParams.requireText(request.line(), 255, "line", errors);
        TradingParams.requireText(request.city(), 64, "city", errors);
        TradingParams.checkMaxLength(request.state(), 64, "state", errors);
        TradingParams.requireText(request.zip(), 16, "zip", errors);
        String countryText = TradingParams.trimToNull(request.country());
        String countryCode = TradingParams.trimToNull(request.countryCode());
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase(Locale.ROOT);
            if (!CountryCatalog.isKnownCode(countryCode)) {
                errors.reject("country_code", "invalid_enum");
            }
        } else if (countryText != null) {
            countryCode = CountryCatalog.resolveCode(countryText);
            if (countryCode == null) {
                errors.reject("country_code", "required");
            }
        }
        if (countryText == null) {
            if (countryCode != null && CountryCatalog.byCode(countryCode) != null) {
                countryText = CountryCatalog.byCode(countryCode).name();
            } else {
                errors.reject("country", "required");
            }
        } else if (countryText.length() > 64) {
            errors.reject("country", "too_long");
        }
        String regionCode = null;
        if (countryCode != null && CountryCatalog.hasRegions(countryCode)) {
            String regionInput = TradingParams.trimToNull(request.regionCode()) != null
                    ? request.regionCode().trim() : TradingParams.trimToNull(request.state());
            // 未提供留空（前台后续强制补选，与回填器口径一致）；提供但无法解析 → 422601
            if (regionInput != null) {
                regionCode = CountryCatalog.resolveRegionCode(countryCode, regionInput);
                if (regionCode == null) {
                    errors.reject("region_code", "invalid_enum");
                }
            }
        }
        errors.throwIfAny();
        return new Resolved(countryText, countryCode, regionCode);
    }

    private record Resolved(String country, String countryCode, String regionCode) {
    }

    private void apply(Address address, AddressUpsert request, Resolved resolved) {
        address.setReceiver(request.receiver().trim());
        address.setPhone(TradingParams.trimToNull(request.phone()));
        address.setLine(request.line().trim());
        address.setCity(request.city().trim());
        address.setState(TradingParams.trimToNull(request.state()));
        address.setZip(request.zip().trim());
        address.setCountry(resolved.country());
        address.setCountryCode(resolved.countryCode());
        address.setRegionCode(resolved.regionCode());
    }

    static AddressDto toDto(Address address) {
        return new AddressDto(address.getId(), address.getReceiver(), address.getPhone(), address.getLine(),
                address.getCity(), address.getState(), address.getZip(), address.getCountry(),
                address.getIsDefault(), address.getCountryCode(), address.getRegionCode());
    }
}
