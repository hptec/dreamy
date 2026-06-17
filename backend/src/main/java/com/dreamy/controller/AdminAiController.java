package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.aitranslation.service.AiTranslationService;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.dreamy.dto.AiTranslationDtos.TranslateResult;
import com.dreamy.dto.AiTranslationDtos.TranslationLogDto;
import huihao.page.Paginated;
import huihao.web.R;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 AI 翻译控制器（2 端点）。
 * L2 TRACE: i18n-backend-api-detail.md §2 / FUNC-008~013 / EDGE-009（未登录 40100）。
 * 鉴权：translate 仅需登录（AdminJwtFilter 兜底，无 @RequirePermission，任意后台用户可调用编辑翻译）；
 * translation-logs 用 /system/gateways（运维查看，EDGE-022 权限隔离同口径）。
 */
@RestController
public class AdminAiController {

    private static final String LOGS_PERMISSION = "/system/gateways";

    private final AiTranslationService service;

    public AdminAiController(AiTranslationService service) {
        this.service = service;
    }

    /** §2.1 翻译请求（后端代理，决策2/10）。登录即可（AdminJwtFilter 强制鉴权）。 */
    @PostMapping("/api/admin/ai/translate")
    public ResponseEntity<R<TranslateResult>> translate(@Valid @RequestBody TranslateRequest req) {
        return ResponseEntity.ok(R.ok(service.translate(req)));
    }

    /** §2.2 调用记录查询。 */
    @RequirePermission(LOGS_PERMISSION)
    @GetMapping("/api/admin/ai/translation-logs")
    public ResponseEntity<R<Paginated<TranslationLogDto>>> logs(
            @RequestParam(name = "biz_type", required = false) String bizType,
            @RequestParam(name = "biz_ref", required = false) String bizRef,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ResponseEntity.ok(R.ok(service.listLogs(bizType, bizRef, status, page, pageSize)));
    }
}
