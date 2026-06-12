package com.dreamy.controller;

import com.dreamy.aspect.RequirePermission;
import com.dreamy.domain.review.service.AdminReviewService;
import com.dreamy.dto.AdminReviewListDTO;
import com.dreamy.dto.ReviewDtos.AdminReviewDto;
import com.dreamy.dto.ReviewDtos.BatchRequest;
import com.dreamy.dto.ReviewDtos.BatchResult;
import com.dreamy.dto.ReviewDtos.FeaturedPatch;
import com.dreamy.dto.ReviewDtos.ImageRejectPatch;
import com.dreamy.dto.ReviewDtos.ReplyPut;
import com.dreamy.dto.ReviewDtos.ReviewImageDto;
import com.dreamy.dto.ReviewDtos.ReviewStatusPatch;
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
 * 后台评价控制器（E-REV-06~12；AdminBearerAuth + RBAC `/reviews`——本 change 新增权限点；不缓存）。
 * 审计三 action 归入规则、CAS 状态机 guard 与失效链在 AdminReviewService 内承载。
 */
@RestController
public class AdminReviewController {

    private static final String PERMISSION = "/reviews";

    private final AdminReviewService adminReviewService;

    public AdminReviewController(AdminReviewService adminReviewService) {
        this.adminReviewService = adminReviewService;
    }

    /** E-REV-06 listAdminReviews（V-REV-014~019；pending_count 平铺 MAP-REV-007） */
    @RequirePermission(PERMISSION)
    @GetMapping("/api/admin/reviews")
    public ResponseEntity<R<AdminReviewListDTO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(R.ok(adminReviewService.listAdminReviews(
                page, pageSize, status, rating, featured, productId, search)));
    }

    /** E-REV-07 patchAdminReviewStatus（TX-REV-002；409802 CAS guard） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/reviews/{id}/status")
    public ResponseEntity<R<AdminReviewDto>> moderate(@PathVariable String id,
                                                      @RequestBody ReviewStatusPatch req) {
        return ResponseEntity.ok(R.ok(adminReviewService.moderate(parseReviewId(id), req.status())));
    }

    /** E-REV-08 patchAdminReviewFeatured（TX-REV-003；409803 guard + 幂等短路） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/reviews/{id}/featured")
    public ResponseEntity<R<AdminReviewDto>> setFeatured(@PathVariable String id,
                                                         @RequestBody FeaturedPatch req) {
        return ResponseEntity.ok(R.ok(adminReviewService.setFeatured(parseReviewId(id), req.featured())));
    }

    /** E-REV-09 batchAdminReviews（TX-REV-004；updated_ids/skipped_ids 语义） */
    @RequirePermission(PERMISSION)
    @PostMapping("/api/admin/reviews/batch")
    public ResponseEntity<R<BatchResult>> batch(@RequestBody BatchRequest req) {
        return ResponseEntity.ok(R.ok(adminReviewService.batch(req.ids(), req.action())));
    }

    /** E-REV-10 putAdminReviewReply（TX-REV-005；409804 guard，PUT=UPSERT 语义） */
    @RequirePermission(PERMISSION)
    @PutMapping("/api/admin/reviews/{id}/reply")
    public ResponseEntity<R<AdminReviewDto>> putReply(@PathVariable String id, @RequestBody ReplyPut req) {
        return ResponseEntity.ok(R.ok(adminReviewService.putReply(parseReviewId(id), req.replyContent())));
    }

    /** E-REV-11 deleteAdminReviewReply（TX-REV-006；清空三字段，幂等 204） */
    @RequirePermission(PERMISSION)
    @DeleteMapping("/api/admin/reviews/{id}/reply")
    public ResponseEntity<Void> deleteReply(@PathVariable String id) {
        adminReviewService.deleteReply(parseReviewId(id));
        return ResponseEntity.noContent().build();
    }

    /** E-REV-12 patchAdminReviewImage（TX-REV-007；404803 归属校验 + 双向幂等） */
    @RequirePermission(PERMISSION)
    @PatchMapping("/api/admin/reviews/{id}/images/{imageId}")
    public ResponseEntity<R<ReviewImageDto>> patchImage(@PathVariable String id,
                                                        @PathVariable String imageId,
                                                        @RequestBody ImageRejectPatch req) {
        Long reviewId = parseReviewId(id);
        Long imgId = parseId(imageId);
        if (imgId == null) {
            // V-REV-029：imageId 非法视同不存在 → 404803
            throw new ReviewException(ReviewErrorCode.REVIEW_IMAGE_NOT_FOUND);
        }
        return ResponseEntity.ok(R.ok(adminReviewService.patchImage(reviewId, imgId, req.rejected())));
    }

    /** V-REV-020/022/026/028/029：id 非法视同不存在 → 404801 */
    private Long parseReviewId(String raw) {
        Long id = parseId(raw);
        if (id == null) {
            throw new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND);
        }
        return id;
    }

    private Long parseId(String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
