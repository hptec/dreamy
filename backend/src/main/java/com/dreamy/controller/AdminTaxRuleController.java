package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.tax.service.TaxRuleService;
import com.dreamy.dto.TradingDtos.TaxDestinationPolicyDto;
import com.dreamy.dto.TradingDtos.TaxDestinationPolicyUpsert;
import com.dreamy.dto.TradingDtos.TaxRuleDto;
import com.dreamy.dto.TradingDtos.TaxRuleEnabledPatch;
import com.dreamy.dto.TradingDtos.TaxRuleUpsert;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台税率规则 / 目的国政策控制器（order-flow-complete §3.2：/api/admin/tax-rules、/api/admin/tax-destination-policies；
 * RBAC `/settings`；不缓存）。
 */
@RestController
public class AdminTaxRuleController {

    private static final String PERMISSION = "/settings";

    private final TaxRuleService taxRuleService;

    public AdminTaxRuleController(TaxRuleService taxRuleService) {
        this.taxRuleService = taxRuleService;
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/tax-rules")
    public ResponseEntity<R<Map<String, List<TaxRuleDto>>>> list(
            @RequestParam(name = "country_code", required = false) String countryCode) {
        return ResponseEntity.ok(R.ok(Map.of("items", taxRuleService.list(countryCode))));
    }

    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/tax-rules")
    public ResponseEntity<R<TaxRuleDto>> create(@RequestBody TaxRuleUpsert req) {
        return ResponseEntity.status(201).body(R.ok(taxRuleService.create(req)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/tax-rules/{id}")
    public ResponseEntity<R<TaxRuleDto>> update(@PathVariable Long id, @RequestBody TaxRuleUpsert req) {
        return ResponseEntity.ok(R.ok(taxRuleService.update(id, req)));
    }

    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/tax-rules/{id}/enabled")
    public ResponseEntity<R<TaxRuleDto>> setEnabled(@PathVariable Long id, @RequestBody TaxRuleEnabledPatch req) {
        return ResponseEntity.ok(R.ok(taxRuleService.setEnabled(id, req == null ? null : req.enabled())));
    }

    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/tax-rules/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taxRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/tax-destination-policies")
    public ResponseEntity<R<Map<String, List<TaxDestinationPolicyDto>>>> listPolicies() {
        return ResponseEntity.ok(R.ok(Map.of("items", taxRuleService.listPolicies())));
    }

    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/tax-destination-policies/{countryCode}")
    public ResponseEntity<R<TaxDestinationPolicyDto>> getPolicy(@PathVariable String countryCode) {
        return ResponseEntity.ok(R.ok(taxRuleService.getPolicy(countryCode)));
    }

    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/tax-destination-policies/{countryCode}")
    public ResponseEntity<R<TaxDestinationPolicyDto>> upsertPolicy(@PathVariable String countryCode,
                                                                   @RequestBody TaxDestinationPolicyUpsert req) {
        return ResponseEntity.ok(R.ok(taxRuleService.upsertPolicy(countryCode, req)));
    }
}
