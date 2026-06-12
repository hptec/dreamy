package com.dreamy.domain.review.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dreamy.error.CatalogErrorCode;
import com.dreamy.error.CatalogException;
import com.dreamy.infra.storage.StorageProperties;
import com.dreamy.enums.ReviewSort;
import com.dreamy.enums.ReviewStatus;
import com.dreamy.domain.review.entity.Review;
import com.dreamy.domain.review.entity.ReviewImage;
import com.dreamy.domain.review.repository.ReviewImageRepository;
import com.dreamy.domain.review.repository.ReviewRepository;
import com.dreamy.dto.ReviewDtos.ImageRef;
import com.dreamy.dto.ReviewDtos.ProductCardDto;
import com.dreamy.dto.ReviewDtos.ReviewImageDto;
import com.dreamy.dto.ReviewDtos.ReviewPageSnapshot;
import com.dreamy.dto.ReviewDtos.StoreMyReviewDto;
import com.dreamy.dto.ReviewDtos.StoreReviewCreate;
import com.dreamy.dto.ReviewDtos.StoreReviewDto;
import com.dreamy.dto.StoreReviewListDTO;
import com.dreamy.error.ReviewErrorCode;
import com.dreamy.error.ReviewException;
import com.dreamy.infra.ReviewCacheService;
import com.dreamy.infra.ReviewCacheService.Family;
import com.dreamy.infra.ReviewTxRunner;
import com.dreamy.port.ReviewCatalogSnapshotPort;
import com.dreamy.port.ReviewIdentityQueryPort;
import com.dreamy.port.TradingPurchaseQueryPort;
import com.dreamy.support.ReviewFieldErrors;
import com.dreamy.support.NameMasker;
import com.dreamy.support.PaginatedFactory;
import com.dreamy.support.RatingSummary;
import com.dreamy.support.ReviewParams;
import huihao.page.Paginated;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费端评价服务（E-REV-01 列表 / E-REV-02 提交，FLOW-P14；E-REV-16 我的评价 F-049，L3 修复轮新增）。
 * L2 TRACE: V-REV-001~007 / TX-REV-001 / CACHE-REV-001 / MAP-REV-001/002/006 / CV-REV-004/005/008/010。
 */
@Service
public class StoreReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository imageRepository;
    private final ReviewCacheService cache;
    private final ReviewTxRunner tx;
    private final TradingPurchaseQueryPort purchasePort;
    private final ReviewCatalogSnapshotPort catalogPort;
    private final ReviewIdentityQueryPort identityPort;
    private final StorageProperties storageProperties;

    public StoreReviewService(ReviewRepository reviewRepository, ReviewImageRepository imageRepository,
                              ReviewCacheService cache, ReviewTxRunner tx,
                              TradingPurchaseQueryPort purchasePort, ReviewCatalogSnapshotPort catalogPort,
                              ReviewIdentityQueryPort identityPort, StorageProperties storageProperties) {
        this.reviewRepository = reviewRepository;
        this.imageRepository = imageRepository;
        this.cache = cache;
        this.tx = tx;
        this.purchasePort = purchasePort;
        this.catalogPort = catalogPort;
        this.identityPort = identityPort;
        this.storageProperties = storageProperties;
    }

    // ==================== E-REV-01 listStoreReviews ====================

    /**
     * 公开端点（白名单 GET:/api/store/reviews）。
     * 商品不存在/未发布 → 空页（rating_count=0，与「商品无评价」同口径防探测语义）；空页同样缓存（穿透保护）。
     */
    public StoreReviewListDTO listStoreReviews(Long productId, String sort, Integer page, Integer pageSize) {
        // V-REV-001~003
        ReviewFieldErrors errors = new ReviewFieldErrors();
        Long pid = ReviewParams.parseRequiredProductId(productId, errors);
        ReviewSort parsedSort = ReviewParams.parseSort(sort, errors);
        int parsedPage = ReviewParams.parsePage(page, errors);
        int parsedSize = ReviewParams.parsePageSize(pageSize, errors);
        errors.throwIfAny();

        // STEP-REV-01 查缓存（key 不含 locale——评价内容不翻译）
        String cacheKey = pid + ":" + parsedSort.getKey() + ":" + parsedPage + ":" + parsedSize;
        Object cached = cache.get(Family.REVIEWS, cacheKey);
        if (cached instanceof ReviewPageSnapshot snapshot) {
            return toListDto(snapshot);
        }

        // STEP-REV-02 approved 过滤 + 排序映射分页（IDX-REV-002）
        Page<Review> reviewPage = reviewRepository.pageApprovedByProduct(pid, parsedSort, parsedPage, parsedSize);
        // STEP-REV-03 批查图片（前台排除驳回图，单次 IN 防 N+1）
        List<Long> reviewIds = reviewPage.getRecords().stream().map(Review::getId).toList();
        Map<Long, List<ReviewImageDto>> imagesByReview = groupImages(
                imageRepository.listByReviewIds(reviewIds, true));
        // STEP-REV-04 聚合派生（单条 GROUP BY，RM-REV-002）
        RatingSummary summary = RatingSummary.fromCounts(reviewRepository.countApprovedByRating(pid));
        // STEP-REV-05 customer_name 脱敏输出（MAP-REV-001）
        List<StoreReviewDto> items = reviewPage.getRecords().stream()
                .map(r -> toStoreDto(r, imagesByReview.getOrDefault(r.getId(), List.of()), true))
                .toList();
        // STEP-REV-06 装配 + 写缓存 TTL 300s（空页同样缓存）
        ReviewPageSnapshot snapshot = new ReviewPageSnapshot(items, reviewPage.getTotal(), parsedPage,
                parsedSize, summary.avg(), summary.count(), summary.breakdown());
        cache.put(Family.REVIEWS, cacheKey, snapshot);
        return toListDto(snapshot);
    }

    // ==================== E-REV-02 createStoreReview ====================

    /**
     * StoreBearerAuth；customer_id=JWT subject（BE-DIM-6，请求体不接收 user_id）。
     * 单事务 TX-REV-001；不失效缓存不发 MQ（pending 前台不可见）；不写 operation_log（非后台操作）。
     */
    public StoreReviewDto createReview(Long customerId, StoreReviewCreate req) {
        // V-REV-004 商品存在且 published（否则 404501 透传 catalog）
        ReviewFieldErrors errors = new ReviewFieldErrors();
        Long pid = ReviewParams.parseRequiredProductId(req.productId(), errors);
        // V-REV-005 rating 必填整数 1..5（bs-508/509）
        if (req.rating() == null) {
            errors.reject("rating", "required");
        } else if (req.rating() < 1 || req.rating() > 5) {
            errors.reject("rating", "out_of_range");
        }
        // V-REV-006 content 可选 ≤5000；trim 后空串视为未提供（存 null）
        String content = req.content() == null ? null : req.content().trim();
        if (content != null && content.isEmpty()) {
            content = null;
        }
        if (content != null && content.length() > 5000) {
            errors.reject("content", "too_long");
        }
        // V-REV-007 images ≤9，url 必填 ≤512 且命中本站 public_url 前缀 + review/ 段（CV-REV-008）
        List<String> imageUrls = validateImages(req.images(), errors);
        errors.throwIfAny();

        ReviewCatalogSnapshotPort.ProductBrief brief = catalogPort.getProductBrief(pid);
        if (brief == null || !brief.published()) {
            throw new CatalogException(CatalogErrorCode.PRODUCT_NOT_FOUND);
        }

        // TX-REV-001 单事务（购买资格校验为事务内首步只读）
        return tx.inTx(() -> {
            // STEP-REV-01 越权防护（403801，跨 trading 领域服务接口，禁止直查 orders 表）
            if (!purchasePort.hasCompletedOrderContaining(customerId, pid)) {
                throw new ReviewException(ReviewErrorCode.REVIEW_NOT_ALLOWED);
            }
            // STEP-REV-02 唯一性预检（uk_review_user_product 兜底并发双提交）
            if (reviewRepository.existsByUserAndProduct(customerId, pid)) {
                throw new ReviewException(ReviewErrorCode.ALREADY_REVIEWED);
            }
            // STEP-REV-03 姓名快照（CV-REV-010 提交时一次性，改名不回溯）
            String customerName = identityPort.getUserName(customerId);
            // STEP-REV-04 INSERT review(pending) + 批量 INSERT review_image(rejected=0)
            Review review = new Review();
            review.setProductId(pid);
            review.setUserId(customerId);
            review.setCustomerName(customerName);
            review.setRating(req.rating());
            review.setContent(req.content() == null || req.content().trim().isEmpty()
                    ? null : req.content().trim());
            review.setStatus(ReviewStatus.PENDING);
            review.setFeatured(false);
            review.setSubmittedAt(LocalDateTime.now());
            try {
                reviewRepository.insert(review);
            } catch (DuplicateKeyException ex) {
                // 并发双提交唯一索引兜底（CV-REV-004）
                throw new ReviewException(ReviewErrorCode.ALREADY_REVIEWED);
            }
            List<ReviewImage> images = new ArrayList<>();
            for (String url : imageUrls) {
                ReviewImage image = new ReviewImage();
                image.setReviewId(review.getId());
                image.setUrl(url);
                image.setRejected(false);
                images.add(image);
            }
            imageRepository.batchInsert(images);
            // MAP-REV-002 本人回执：customer_name 原样、images 全量（STEP-REV-05 无缓存/MQ/审计副作用）
            List<ReviewImageDto> imageDtos = images.stream()
                    .map(i -> new ReviewImageDto(i.getId(), i.getUrl(), Boolean.TRUE.equals(i.getRejected())))
                    .toList();
            return toStoreDto(review, imageDtos, false);
        });
    }

    // ==================== E-REV-16 listMyReviews（F-049 我的评价，L3 修复轮新增） ====================

    /**
     * StoreBearerAuth（不入公开白名单——GET:/api/store/reviews 为精确条目不前缀放行 /mine）。
     * 按 JWT customer_id 过滤（BE-DIM-6），本人全状态（pending/approved/rejected）可见；
     * images 全量含 rejected 标记（本人视角，与 MAP-REV-002 回执同口径）；
     * product 卡片简况批量装配（ReviewCatalogSnapshotPort，NP-REV-001 防 N+1，商品已删除容忍 null）；
     * 个人数据不缓存（缓存矩阵：鉴权数据不入 CDN/JetCache 共享面）。
     */
    public Paginated<StoreMyReviewDto> listMyReviews(Long customerId, Integer page, Integer pageSize) {
        ReviewFieldErrors errors = new ReviewFieldErrors();
        int parsedPage = ReviewParams.parsePage(page, errors);
        int parsedSize = ReviewParams.parsePageSize(pageSize, errors);
        errors.throwIfAny();

        Page<Review> reviewPage = reviewRepository.pageByUser(customerId, parsedPage, parsedSize);
        List<Review> records = reviewPage.getRecords();
        // images 单次 IN 批查（excludeRejected=false：本人可见全量含驳回标记）
        List<Long> reviewIds = records.stream().map(Review::getId).toList();
        Map<Long, List<ReviewImageDto>> imagesByReview = groupImages(
                imageRepository.listByReviewIds(reviewIds, false));
        // product 卡片简况批量装配
        Map<Long, ReviewCatalogSnapshotPort.ProductBrief> briefs = catalogPort.getProductBriefs(
                records.stream().map(Review::getProductId).distinct().toList());
        List<StoreMyReviewDto> items = records.stream()
                .map(r -> toMyDto(r, imagesByReview.getOrDefault(r.getId(), List.of()),
                        briefs.get(r.getProductId())))
                .toList();
        return PaginatedFactory.of(items, reviewPage.getTotal(), parsedPage, parsedSize);
    }

    /** E-REV-16 装配：product 卡片（商品已删除 → 仅 id，slug/name=null 容忍） */
    private StoreMyReviewDto toMyDto(Review review, List<ReviewImageDto> images,
                                     ReviewCatalogSnapshotPort.ProductBrief brief) {
        ProductCardDto product = brief == null
                ? new ProductCardDto(review.getProductId(), null, null)
                : new ProductCardDto(brief.id(), brief.slug(), brief.name());
        return new StoreMyReviewDto(review.getId(), review.getProductId(), product, review.getRating(),
                review.getContent(), review.getStatus() == null ? null : review.getStatus().getKey(),
                review.getFeatured(), review.getSubmittedAt(), images,
                review.getReplyAuthor(), review.getReplyContent(), review.getReplyTime());
    }

    /** V-REV-007 校验并提取 url 列表 */
    private List<String> validateImages(List<ImageRef> images, ReviewFieldErrors errors) {
        List<String> urls = new ArrayList<>();
        if (images == null || images.isEmpty()) {
            return urls;
        }
        if (images.size() > 9) {
            errors.reject("images", "too_many");
            return urls;
        }
        String prefix = reviewPublicUrlPrefix();
        for (ImageRef ref : images) {
            String url = ref == null ? null : ref.url();
            if (url == null || url.isBlank()) {
                errors.reject("images", "invalid_url");
                return List.of();
            }
            if (url.length() > 512 || !url.startsWith(prefix)) {
                // 外链/越权前缀（product/ 等其他 scope）→ 422801 fields.images=invalid_url（决策 9 归类）
                errors.reject("images", "invalid_url");
                return List.of();
            }
            urls.add(url);
        }
        return urls;
    }

    /** 本站对象存储 public_url 前缀 + review/ 对象 key 段（CV-REV-008，防外链注入） */
    String reviewPublicUrlPrefix() {
        String base = storageProperties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            return base + "review/";
        }
        return base + "/review/";
    }

    // ==================== 装配 ====================

    private Map<Long, List<ReviewImageDto>> groupImages(List<ReviewImage> images) {
        Map<Long, List<ReviewImageDto>> result = new HashMap<>();
        for (ReviewImage image : images) {
            result.computeIfAbsent(image.getReviewId(), k -> new ArrayList<>())
                    .add(new ReviewImageDto(image.getId(), image.getUrl(),
                            Boolean.TRUE.equals(image.getRejected())));
        }
        return result;
    }

    /** MAP-REV-001（mask=true 列表脱敏）/ MAP-REV-002（mask=false 本人回执原样） */
    private StoreReviewDto toStoreDto(Review review, List<ReviewImageDto> images, boolean mask) {
        String name = mask ? NameMasker.mask(review.getCustomerName()) : review.getCustomerName();
        return new StoreReviewDto(review.getId(), review.getProductId(), name, review.getRating(),
                review.getContent(), review.getStatus() == null ? null : review.getStatus().getKey(),
                review.getFeatured(), review.getSubmittedAt(), images,
                review.getReplyAuthor(), review.getReplyContent(), review.getReplyTime());
    }

    /** MAP-REV-006 Paginated 子类平铺装配 */
    private StoreReviewListDTO toListDto(ReviewPageSnapshot snapshot) {
        StoreReviewListDTO dto = new StoreReviewListDTO();
        dto.setData(snapshot.items());
        dto.setTotalElements(snapshot.total());
        dto.setPageNumber(snapshot.page());
        dto.setPageSize(snapshot.pageSize());
        dto.setNumberOfElements(snapshot.items().size());
        dto.setTotalPages(snapshot.pageSize() > 0
                ? (int) Math.ceil((double) snapshot.total() / snapshot.pageSize()) : 0);
        dto.setRatingAvg(snapshot.ratingAvg());
        dto.setRatingCount(snapshot.ratingCount());
        dto.setRatingBreakdown(snapshot.ratingBreakdown());
        return dto;
    }
}
