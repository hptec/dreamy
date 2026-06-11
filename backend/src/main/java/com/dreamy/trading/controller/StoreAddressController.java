package com.dreamy.trading.controller;

import com.dreamy.trading.domain.address.service.AddressService;
import com.dreamy.trading.dto.TradingDtos.AddressDto;
import com.dreamy.trading.dto.TradingDtos.AddressListResponse;
import com.dreamy.trading.dto.TradingDtos.AddressUpsert;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端地址簿控制器（trading-api-detail §2：listAddresses/createAddress/updateAddress/deleteAddress）。
 */
@RestController
public class StoreAddressController {

    private final AddressService addressService;

    public StoreAddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** E-listAddresses */
    @GetMapping("/api/store/addresses")
    public ResponseEntity<R<AddressListResponse>> list() {
        return ResponseEntity.ok(R.ok(new AddressListResponse(addressService.list(StoreAuth.customerId()))));
    }

    /** E-createAddress（201；TX-TRD-008） */
    @PostMapping("/api/store/addresses")
    public ResponseEntity<R<AddressDto>> create(@RequestBody AddressUpsert request) {
        return ResponseEntity.status(201).body(R.ok(addressService.create(StoreAuth.customerId(), request)));
    }

    /** E-updateAddress */
    @PutMapping("/api/store/addresses/{id}")
    public ResponseEntity<R<AddressDto>> update(@PathVariable Long id, @RequestBody AddressUpsert request) {
        return ResponseEntity.ok(R.ok(addressService.update(StoreAuth.customerId(), id, request)));
    }

    /** E-deleteAddress（204；address_snapshot 快照不波及既有订单） */
    @DeleteMapping("/api/store/addresses/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        addressService.delete(StoreAuth.customerId(), id);
        return ResponseEntity.noContent().build();
    }
}
