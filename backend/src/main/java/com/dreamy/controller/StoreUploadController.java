package com.dreamy.controller;

import com.dreamy.dto.ReviewDtos.PresignRequest;
import com.dreamy.dto.ReviewDtos.PresignResponse;
import com.dreamy.infra.ReviewPresignService;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端买家秀上传控制器（E-REV-05，决策 9 / FLOW-P17）。
 * StoreBearerAuth（**不入白名单**——§0.1：滥用防护在 Cloudflare WAF 层，后端不实现限流）。
 * scope 固定 review；不写 operation_log、不发 MQ、不缓存。
 */
@RestController
public class StoreUploadController {

    private final ReviewPresignService presignService;

    public StoreUploadController(ReviewPresignService presignService) {
        this.presignService = presignService;
    }

    /** E-REV-05 presignStoreUpload（V-REV-012/013；502801 降级——评价可先不带图提交） */
    @PostMapping("/api/store/uploads/presign")
    public ResponseEntity<R<PresignResponse>> presign(@RequestBody PresignRequest req) {
        return ResponseEntity.ok(R.ok(presignService.presign(req)));
    }
}
