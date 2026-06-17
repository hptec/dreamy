package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.glossary.service.GlossaryService;
import com.dreamy.dto.GlossaryDtos.GlossaryTermDto;
import com.dreamy.dto.GlossaryDtos.GlossaryTermUpsert;
import com.dreamy.error.GatewayErrorCode;
import com.dreamy.error.GatewayException;
import huihao.page.Paginated;
import huihao.web.R;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台翻译术语表控制器（5 端点，RBAC /system/glossary）。
 * L2 TRACE: i18n-backend-api-detail.md §3 / FUNC-022 / EDGE-022（无权限 40300）。
 */
@RestController
public class AdminGlossaryController {

    private static final String PERMISSION = "/system/glossary";

    private final GlossaryService service;

    public AdminGlossaryController(GlossaryService service) {
        this.service = service;
    }

    /** §3.1 新增（201）。 */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/glossary/terms")
    public ResponseEntity<R<GlossaryTermDto>> create(@Valid @RequestBody GlossaryTermUpsert req) {
        return ResponseEntity.status(201).body(R.ok(service.create(req)));
    }

    /** §3.2 列表分页（category/enabled 过滤）。 */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/glossary/terms")
    public ResponseEntity<R<Paginated<GlossaryTermDto>>> list(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "enabled", required = false) Boolean enabled,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ResponseEntity.ok(R.ok(service.list(category, enabled, page, pageSize)));
    }

    /** §3.3 详情。 */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/glossary/terms/{id}")
    public ResponseEntity<R<GlossaryTermDto>> get(@PathVariable String id) {
        return ResponseEntity.ok(R.ok(service.getById(parseId(id))));
    }

    /** §3.4 更新。 */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/glossary/terms/{id}")
    public ResponseEntity<R<GlossaryTermDto>> update(@PathVariable String id,
                                                     @Valid @RequestBody GlossaryTermUpsert req) {
        return ResponseEntity.ok(R.ok(service.update(parseId(id), req)));
    }

    /** §3.5 删除（204）。 */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/glossary/terms/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** id 非法视同不存在 → 404401。 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new GatewayException(GatewayErrorCode.TERM_NOT_FOUND);
    }
}
