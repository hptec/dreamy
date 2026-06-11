package com.dreamy.trading.dto;

import com.dreamy.trading.port.CatalogSnapshotPort.ProductBrief;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * trading 域 DTO 集（MAP-TRD-001~012；JSON snake_case 由全局 SNAKE_CASE 命名策略承载，CP-001）。
 * 契约对齐 trading-api.openapi.yml v1.1.0（37 操作；Paginated 形状由 huihao.page.Paginated 承载）。
 * 脱敏（MAP-TRD-006/007）：client_secret 永不落库不入持久 DTO（PaymentCredential 即取即用）；
 * StoreRefund 视图隐藏 stripe_refund_id/return_tracking_no/customer_*。
 */
public final class TradingDtos {

    private TradingDtos() {
    }

    // ==================== 定制尺寸（契约 CustomSizeData，决策 20.6/24） ====================

    public record CustomSizeData(BigDecimal bust, BigDecimal waist, BigDecimal hips,
                                 BigDecimal hollowToFloor, BigDecimal height) implements Serializable {
    }

    // ==================== 购物车（MAP-TRD-001） ====================

    public record CartItemCreate(Long productId, Long skuId, Integer qty, CustomSizeData customSizeData) {
    }

    public record CartMergeRequest(String anonToken, List<CartItemCreate> items) {
    }

    public record CartItemUpdate(Integer qty) {
    }

    public record SkuView(Long id, String skuCode, String color, String size, Integer stock) {
    }

    public record CartItemDto(Long id, Long productId, Long skuId, Integer qty,
                              CustomSizeData customSizeData, ProductBrief product, SkuView sku) {
    }

    public record CartResponse(List<CartItemDto> items, List<Long> dyeLotProductIds,
                               List<Long> mergedTruncatedItemIds) {
    }

    // ==================== 地址（MAP-TRD-002） ====================

    public record AddressUpsert(String receiver, String phone, String line, String city,
                                String state, String zip, String country, Boolean isDefault) {
    }

    public record AddressDto(Long id, String receiver, String phone, String line, String city,
                             String state, String zip, String country, Boolean isDefault) {
    }

    public record AddressListResponse(List<AddressDto> items) {
    }

    // ==================== 结算（FLOW-P05/P06） ====================

    public record CheckoutQuoteRequest(Long addressId, String country, String currency, String carrier,
                                       String couponCode, Boolean giftWrap, LocalDate weddingDate) {
    }

    public record ShippingOptionDto(String carrier, BigDecimal fee, String leadTime, Boolean selected) {
    }

    public record CheckoutQuoteResponse(String currency, BigDecimal exchangeRate, BigDecimal subtotal,
                                        List<ShippingOptionDto> shippingOptions, BigDecimal shippingFee,
                                        BigDecimal giftWrapFee, BigDecimal discountAmount, BigDecimal totalAmount,
                                        Boolean couponValid, Integer couponReasonCode, Boolean leadTimeWarning,
                                        Integer maxLeadTimeDays, List<Long> dyeLotProductIds) {
    }

    public record OrderCreateRequest(String idempotencyKey, Long addressId, String currency, String carrier,
                                     String couponCode, Boolean giftWrap, LocalDate weddingDate,
                                     String paymentMethod, String locale) {
    }

    public record PaymentCredential(String paymentIntentId, String clientSecret) {
    }

    public record OrderCreateResponse(StoreOrderDetail order, PaymentCredential payment) {
    }

    // ==================== 订单（MAP-TRD-003/004） ====================

    public record OrderLineDto(Long id, Long productId, Long skuId, String productName, String skuCode,
                               String color, String size, Integer qty, BigDecimal unitPrice, String img,
                               CustomSizeData customSizeData, Boolean refundable) {
    }

    public record PaymentSummaryDto(String provider, String paymentIntentId, BigDecimal amount, String currency,
                                    String status, String cardSummary, LocalDateTime paidAt) {
    }

    public record StoreOrderListItem(Long id, String orderNo, String status, String currency,
                                     BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                     BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                     BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                     String paymentMethod, String carrier, String trackingNo,
                                     LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                     LocalDateTime completedAt, LocalDateTime createdAt,
                                     Integer lineCount, String firstLineImg) {
    }

    public record StoreOrderDetail(Long id, String orderNo, String status, String currency,
                                   BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                   BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                   BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                   String paymentMethod, String carrier, String trackingNo,
                                   LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                   LocalDateTime completedAt, LocalDateTime createdAt,
                                   List<OrderLineDto> lines, Map<String, Object> addressSnapshot,
                                   PaymentSummaryDto payment, Boolean refundEligible,
                                   Integer refundBlockReasonCode, List<StoreRefundDto> refunds) {
    }

    // ==================== 退款（MAP-TRD-007/008） ====================

    public record StoreRefundDto(Long id, String refundNo, Long orderId, BigDecimal amount, String currency,
                                 String reason, String status, LocalDateTime appliedAt) {
    }

    public record AdminRefundDto(Long id, String refundNo, Long orderId, BigDecimal amount, String currency,
                                 String reason, String rejectReason, String status, LocalDateTime appliedAt,
                                 String orderNo, Long customerId, String customerName, String customerEmail,
                                 String stripeRefundId, String returnTrackingNo) {
    }

    public record StoreRefundApply(String reason) {
    }

    public record AdminRefundCreate(BigDecimal amount, String reason) {
    }

    public record AdminRefundApprove(String returnTrackingNo) {
    }

    public record AdminRefundReject(String reason) {
    }

    public record AdminRefundPatch(String returnTrackingNo) {
    }

    // ==================== 后台订单（MAP-TRD-005） ====================

    /** API-TRD-01：追加 country（address_snapshot.country 提取，RM-TRD-01b）/ item_count（SUM(qty) 派生，RM-TRD-01c），非 breaking */
    public record AdminOrderListItem(Long id, String orderNo, String status, String currency,
                                     BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                     BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                     BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                     String paymentMethod, String carrier, String trackingNo,
                                     LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                     LocalDateTime completedAt, LocalDateTime createdAt,
                                     Long customerId, String customerName, String customerEmail,
                                     String country, Integer itemCount) {
    }

    public record AdminOrderDetail(Long id, String orderNo, String status, String currency,
                                   BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                   BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                   BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                   String paymentMethod, String carrier, String trackingNo,
                                   LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                   LocalDateTime completedAt, LocalDateTime createdAt,
                                   Long customerId, String customerName, String customerEmail,
                                   String customerPhone, List<OrderLineDto> lines,
                                   Map<String, Object> addressSnapshot, PaymentSummaryDto payment,
                                   List<AdminRefundDto> refunds) {
    }

    public record AdminOrderShipRequest(String carrier, String trackingNo) {
    }

    public record AdminOrderStatusPatch(String status) {
    }

    // ==================== 收藏 / 浏览历史（MAP-TRD-009） ====================

    public record WishlistItemDto(Long id, Long productId, ProductBrief product) {
    }

    public record WishlistAddRequest(Long productId) {
    }

    public record WishlistMoveToCartRequest(Long skuId, Integer qty, CustomSizeData customSizeData) {
    }

    public record WishlistListResponse(List<WishlistItemDto> items) {
    }

    public record BrowseHistoryItemDto(Long id, Long productId, LocalDateTime viewedAt, ProductBrief product) {
    }

    public record BrowseHistoryRecordRequest(Long productId) {
    }

    public record BrowseHistoryListResponse(List<BrowseHistoryItemDto> items) {
    }

    // ==================== 汇率 / 结算配置（MAP-TRD-010） ====================

    /** store 视图隐藏 updated_by/id；Serializable 供 JetCache java encoder（CACHE-TRD-001） */
    public record StoreExchangeRateDto(String currency, BigDecimal rate, LocalDateTime updatedAt)
            implements Serializable {
    }

    public record AdminExchangeRateDto(Long id, String currency, BigDecimal rate, Long updatedBy,
                                       LocalDateTime updatedAt) {
    }

    public record ExchangeRateUpdateRequest(BigDecimal rate) {
    }

    public record ExchangeRateListResponse<T>(List<T> items) {
    }

    public record CheckoutConfigDto(BigDecimal giftWrapFeeUsd, Integer customRefundGraceHours) {
    }

    // ==================== webhook ====================

    public record WebhookReceived(Boolean received) {
    }
}
