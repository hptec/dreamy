package com.dreamy.controller;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.domain.question.service.StoreQuestionService;
import com.dreamy.domain.review.service.StoreReviewService;
import com.dreamy.dto.ReviewDtos.StoreMyReviewDto;
import com.dreamy.dto.ReviewDtos.StoreQuestionCreate;
import com.dreamy.dto.ReviewDtos.StoreQuestionDto;
import com.dreamy.dto.ReviewDtos.StoreReviewCreate;
import com.dreamy.dto.ReviewDtos.StoreReviewDto;
import com.dreamy.dto.StoreReviewListDTO;
import huihao.page.Paginated;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端评价/Q&A 控制器（E-REV-01~04 + E-REV-16 我的评价 F-049，L3 修复轮新增）。
 * 鉴权（§0.1 method-aware 白名单）：GET reviews/questions 匿名公开（登记 GET:/api/store/reviews、
 * GET:/api/store/questions——同路径 POST 仍 StoreBearerAuth 强制鉴权，customer_id=JWT subject，BE-DIM-6；
 * /api/store/reviews/mine 不入白名单，强制鉴权）。
 * 开发阶段所有读端点响应 `Cache-Control: no-store`；提交端点与个人数据端点同样不缓存。
 */
@RestController
public class StoreReviewController {

    private static final String CACHE_60 = "no-store";

    private final StoreReviewService storeReviewService;
    private final StoreQuestionService storeQuestionService;

    public StoreReviewController(StoreReviewService storeReviewService,
                                 StoreQuestionService storeQuestionService) {
        this.storeReviewService = storeReviewService;
        this.storeQuestionService = storeQuestionService;
    }

    /** E-REV-01 listStoreReviews（公开；FLOW-P14 读侧） */
    @GetMapping("/api/store/reviews")
    public ResponseEntity<R<StoreReviewListDTO>> listReviews(
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        StoreReviewListDTO result = storeReviewService.listStoreReviews(productId, sort, page, pageSize);
        return ResponseEntity.ok().header("Cache-Control", CACHE_60).body(R.ok(result));
    }

    /** E-REV-02 createStoreReview（StoreBearerAuth；TX-REV-001；403801/404501/409801） */
    @PostMapping("/api/store/reviews")
    public ResponseEntity<R<StoreReviewDto>> createReview(@RequestBody StoreReviewCreate req) {
        return ResponseEntity.status(201).body(R.ok(storeReviewService.createReview(customerId(), req)));
    }

    /**
     * E-REV-16 listMyReviews（F-049 我的评价，L3 修复轮新增）。
     * StoreBearerAuth：白名单条目 GET:/api/store/reviews 为精确路径不放行 /mine（MethodAwarePathMatcher
     * AntPath 精确匹配），无 token → 过滤器短路 401；customer_id=JWT subject（BE-DIM-6）。
     * 个人数据不缓存。
     */
    @GetMapping("/api/store/reviews/mine")
    public ResponseEntity<R<Paginated<StoreMyReviewDto>>> listMyReviews(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ResponseEntity.ok(R.ok(storeReviewService.listMyReviews(customerId(), page, pageSize)));
    }

    /** E-REV-03 listStoreQuestions（公开） */
    @GetMapping("/api/store/questions")
    public ResponseEntity<R<Paginated<StoreQuestionDto>>> listQuestions(
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        Paginated<StoreQuestionDto> result = storeQuestionService.listStoreQuestions(productId, page, pageSize);
        return ResponseEntity.ok().header("Cache-Control", CACHE_60).body(R.ok(result));
    }

    /** E-REV-04 createStoreQuestion（StoreBearerAuth；TX-REV-010） */
    @PostMapping("/api/store/questions")
    public ResponseEntity<R<StoreQuestionDto>> createQuestion(@RequestBody StoreQuestionCreate req) {
        return ResponseEntity.status(201).body(R.ok(storeQuestionService.createQuestion(customerId(), req)));
    }

    /** JWT sub（store=user_id）转 Long（BE-DIM-6：请求体夹带 user_id 一律忽略，bs-618） */
    private Long customerId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !AuthPrincipal.TYPE_STORE.equals(principal.type())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(principal.subject());
    }
}
