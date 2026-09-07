package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.question.service.AdminQuestionService;
import com.dreamy.dto.AdminQuestionListDTO;
import com.dreamy.dto.ReviewDtos.AdminQuestionDto;
import com.dreamy.dto.ReviewDtos.AnswerPut;
import com.dreamy.dto.ReviewDtos.BatchRequest;
import com.dreamy.dto.ReviewDtos.BatchResult;
import com.dreamy.dto.ReviewDtos.VisibilityPatch;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
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

/**
 * 后台 Q&A 控制器（E-REV-13~15；AdminBearerAuth + RBAC `/reviews`——契约 security 同 key；不缓存）。
 */
@RestController
public class AdminQuestionController {

    private static final String PERMISSION = "/reviews";

    private final AdminQuestionService adminQuestionService;

    public AdminQuestionController(AdminQuestionService adminQuestionService) {
        this.adminQuestionService = adminQuestionService;
    }

    /** E-REV-13 listAdminQuestions（V-REV-031~033；含未回答与 hidden——后台全量视角；unanswered_count 平铺） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/questions")
    public ResponseEntity<R<AdminQuestionListDTO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) String answered,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(adminQuestionService.listAdminQuestions(
                page, pageSize, productId, answered, search)));
    }

    /** E-REV-14 putAdminQuestionAnswer（TX-REV-008；首答自动 visible——question_answer_flow；并发冲突 409805） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/questions/{id}/answer")
    public ResponseEntity<R<AdminQuestionDto>> putAnswer(@PathVariable String id, @RequestBody AnswerPut req) {
        return ResponseEntity.ok(R.ok(adminQuestionService.putAnswer(parseId(id), req.answer())));
    }

    /** 撤回回答（清空 answer/answer_time；幂等 204；对齐评价 DELETE /reply） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/questions/{id}/answer")
    public ResponseEntity<Void> deleteAnswer(@PathVariable String id) {
        adminQuestionService.deleteAnswer(parseId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-REV-15 patchAdminQuestionVisibility（TX-REV-009；幂等短路） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/questions/{id}/visibility")
    public ResponseEntity<R<AdminQuestionDto>> patchVisibility(@PathVariable String id,
                                                               @RequestBody VisibilityPatch req) {
        return ResponseEntity.ok(R.ok(adminQuestionService.patchVisibility(parseId(id), req.visible())));
    }

    /** 批量可见性（hide/show；updated_ids/skipped_ids 语义——对齐评价 POST /reviews/batch） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/questions/batch")
    public ResponseEntity<R<BatchResult>> batch(@RequestBody BatchRequest req) {
        return ResponseEntity.ok(R.ok(adminQuestionService.batchVisibility(req.ids(), req.action())));
    }

    /** V-REV-034/036：id 非法视同不存在 → 404802 */
    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new ReviewException(ReviewErrorCode.QUESTION_NOT_FOUND);
    }
}
