package com.dreamy.controller;

import com.dreamy.domain.gateway.service.AiTranslationService;
import com.dreamy.dto.AiTranslationDtos.TranslateRequest;
import com.dreamy.dto.AiTranslationDtos.TranslateResult;
import huihao.web.R;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 AI 翻译控制器（瘦版，仅 translate 端点）。
 * 鉴权：translate 仅需登录（AdminJwtFilter 兜底，任意后台用户可调用编辑翻译）。
 */
@RestController
public class AdminAiController {

    private final AiTranslationService service;

    public AdminAiController(AiTranslationService service) {
        this.service = service;
    }

    /** AI 翻译请求（后端代理外部网关）。登录即可调用。 */
    @PostMapping("/api/admin/ai/translate")
    public ResponseEntity<R<TranslateResult>> translate(@Valid @RequestBody TranslateRequest req) {
        return ResponseEntity.ok(R.ok(service.translate(req)));
    }
}
