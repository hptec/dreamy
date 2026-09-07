package com.dreamy.dto;

import com.dreamy.port.TradingCatalogSnapshotPort.ProductBrief;

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

    /** order-flow-complete G：尾部追加 country_code / region_code（country_code 必须在 CountryCatalog 内） */
    public record AddressUpsert(String receiver, String phone, String line, String city,
                                String state, String zip, String country, Boolean isDefault,
                                String countryCode, String regionCode) {
        /** 兼容旧八参构造 */
        public AddressUpsert(String receiver, String phone, String line, String city, String state, String zip,
                             String country, Boolean isDefault) {
            this(receiver, phone, line, city, state, zip, country, isDefault, null, null);
        }
    }

    public record AddressDto(Long id, String receiver, String phone, String line, String city,
                             String state, String zip, String country, Boolean isDefault,
                             String countryCode, String regionCode) {
    }

    public record AddressListResponse(List<AddressDto> items) {
    }

    // ==================== 结算（FLOW-P05/P06） ====================

    /** order-flow-complete H：尾部追加 service_level（1=STANDARD 2=EXPRESS；缺省 STANDARD 最便宜）；carrier 可传 code 或 name */
    public record CheckoutQuoteRequest(Long addressId, String country, String currency, String carrier,
                                       String couponCode, Boolean giftWrap, LocalDate weddingDate,
                                       Integer serviceLevel) {
        /** 兼容旧七参构造 */
        public CheckoutQuoteRequest(Long addressId, String country, String currency, String carrier,
                                    String couponCode, Boolean giftWrap, LocalDate weddingDate) {
            this(addressId, country, currency, carrier, couponCode, giftWrap, weddingDate, null);
        }
    }

    /** order-flow-complete E：尾部追加 carrier_code/carrier_name/service_level/transit_days/estimated_delivery */
    public record ShippingOptionDto(String carrier, BigDecimal fee, String leadTime, Boolean selected,
                                    String carrierCode, String carrierName, Integer serviceLevel,
                                    Integer transitDaysMin, Integer transitDaysMax,
                                    LocalDate estimatedDeliveryFrom, LocalDate estimatedDeliveryTo) {
    }

    /**
     * order-flow-complete §3.1：尾部追加 tax_amount / tax_breakdown / incoterm / duties_notice / duties_notice_text /
     * exchange_rate_locked_note / service_level（选中） / carrier_code（选中） / estimated_delivery_from/to / production_days /
     * country_code / region_code。
     */
    public record CheckoutQuoteResponse(String currency, BigDecimal exchangeRate, BigDecimal subtotal,
                                        List<ShippingOptionDto> shippingOptions, BigDecimal shippingFee,
                                        BigDecimal giftWrapFee, BigDecimal discountAmount, BigDecimal totalAmount,
                                        Boolean couponValid, Integer couponReasonCode, Boolean leadTimeWarning,
                                        Integer maxLeadTimeDays, List<Long> dyeLotProductIds,
                                        BigDecimal taxAmount, List<TaxBreakdownDto> taxBreakdown, Integer incoterm,
                                        Boolean dutiesNotice, String dutiesNoticeText, Boolean exchangeRateLockedNote,
                                        Integer serviceLevel, String carrierCode, LocalDate estimatedDeliveryFrom,
                                        LocalDate estimatedDeliveryTo, Integer productionDays, String countryCode,
                                        String regionCode) {
    }

    /** order-flow-complete H：尾部追加 service_level / carrier_code（carrier 名保留兼容；二者至少其一） */
    public record OrderCreateRequest(String idempotencyKey, Long addressId, String currency, String carrier,
                                     String couponCode, Boolean giftWrap, LocalDate weddingDate,
                                     String paymentMethod, String locale, Integer serviceLevel, String carrierCode) {
        /** 兼容旧九参构造 */
        public OrderCreateRequest(String idempotencyKey, Long addressId, String currency, String carrier,
                                  String couponCode, Boolean giftWrap, LocalDate weddingDate,
                                  String paymentMethod, String locale) {
            this(idempotencyKey, addressId, currency, carrier, couponCode, giftWrap, weddingDate, paymentMethod,
                    locale, null, null);
        }
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

    /** order-flow-complete：追加 refunded_amount（每次批准仅退 delta 的累计） */
    public record PaymentSummaryDto(String provider, String paymentIntentId, BigDecimal amount, String currency,
                                    Integer status, String cardSummary, LocalDateTime paidAt,
                                    BigDecimal refundedAmount) {
    }

    public record StoreOrderListItem(Long id, String orderNo, Integer status, String currency,
                                     BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                     BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                     BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                     String paymentMethod, String carrier, String trackingNo,
                                     LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                     LocalDateTime completedAt, LocalDateTime createdAt,
                                     Integer lineCount, String firstLineImg,
                                     Integer productionStage, LocalDateTime deliveredAt, BigDecimal taxAmount,
                                     Integer incoterm, BigDecimal refundedAmount, Integer amountVersion,
                                     LocalDate estimatedDeliveryFrom, LocalDate estimatedDeliveryTo,
                                     Integer shippingServiceLevel) {
    }

    public record StoreOrderDetail(Long id, String orderNo, Integer status, String currency,
                                   BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                   BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                   BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                   String paymentMethod, String carrier, String trackingNo,
                                   LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                   LocalDateTime completedAt, LocalDateTime createdAt,
                                   List<OrderLineDto> lines, Map<String, Object> addressSnapshot,
                                   PaymentSummaryDto payment, Boolean refundEligible,
                                   Integer refundBlockReasonCode, List<StoreRefundDto> refunds,
                                   Integer productionStage, LocalDateTime deliveredAt, BigDecimal taxAmount,
                                   List<TaxBreakdownDto> taxBreakdown, Integer incoterm, BigDecimal refundedAmount,
                                   Integer amountVersion, LocalDate estimatedDeliveryFrom,
                                   LocalDate estimatedDeliveryTo, Integer shippingServiceLevel,
                                   List<OrderEventDto> events, List<ShipmentDto> shipments) {
    }

    // ==================== 退款（MAP-TRD-007/008） ====================

    /** order-flow-complete：追加 reject_reason / from_status（还原目标快照）/ resolved_at */
    public record StoreRefundDto(Long id, String refundNo, Long orderId, BigDecimal amount, String currency,
                                 String reason, Integer status, LocalDateTime appliedAt,
                                 String rejectReason, Integer fromStatus, LocalDateTime updatedAt) {
    }

    public record AdminRefundDto(Long id, String refundNo, Long orderId, BigDecimal amount, String currency,
                                 String reason, String rejectReason, Integer status, LocalDateTime appliedAt,
                                 String orderNo, Long customerId, String customerName, String customerEmail,
                                 String stripeRefundId, String returnTrackingNo,
                                 Integer fromStatus, Integer fromStage, LocalDateTime updatedAt) {
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
    public record AdminOrderListItem(Long id, String orderNo, Integer status, String currency,
                                     BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                     BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                     BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                     String paymentMethod, String carrier, String trackingNo,
                                     LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                     LocalDateTime completedAt, LocalDateTime createdAt,
                                     Long customerId, String customerName, String customerEmail,
                                     String country, Integer itemCount,
                                     Integer productionStage, Long weddingDaysLeft, LocalDateTime deliveredAt,
                                     BigDecimal taxAmount, BigDecimal refundedAmount, Integer amountVersion,
                                     Integer shippingServiceLevel) {
    }

    public record AdminOrderDetail(Long id, String orderNo, Integer status, String currency,
                                   BigDecimal exchangeRate, LocalDate weddingDate, BigDecimal subtotal,
                                   BigDecimal shippingFee, Boolean giftWrap, BigDecimal giftWrapFee,
                                   BigDecimal discountAmount, BigDecimal totalAmount, Long couponId,
                                   String paymentMethod, String carrier, String trackingNo,
                                   LocalDateTime expiresAt, LocalDateTime paidAt, LocalDateTime shippedAt,
                                   LocalDateTime completedAt, LocalDateTime createdAt,
                                   Long customerId, String customerName, String customerEmail,
                                   String customerPhone, List<OrderLineDto> lines,
                                   Map<String, Object> addressSnapshot, PaymentSummaryDto payment,
                                   List<AdminRefundDto> refunds,
                                   Integer productionStage, LocalDateTime deliveredAt, BigDecimal taxAmount,
                                   List<TaxBreakdownDto> taxBreakdown, Integer incoterm, BigDecimal refundedAmount,
                                   Integer amountVersion, LocalDate estimatedDeliveryFrom,
                                   LocalDate estimatedDeliveryTo, Integer shippingServiceLevel,
                                   Long weddingDaysLeft, String localeSnapshot,
                                   List<OrderEventDto> events, List<ShipmentDto> shipments) {
    }

    public record AdminOrderShipRequest(String carrier, String trackingNo) {
    }

    public record AdminOrderStatusPatch(Integer status) {
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

    /** order-flow-complete G：尾部追加 source/synced_at/manual_override/effective_rate(含 spread)/spread_scaled */
    public record AdminExchangeRateDto(Long id, String currency, BigDecimal rate, Long updatedBy,
                                       LocalDateTime updatedAt, Integer source, LocalDateTime syncedAt,
                                       Boolean manualOverride, BigDecimal effectiveRate, Integer spreadScaled) {
    }

    /** order-flow-complete G：尾部追加 manual_override（null=保持现值） */
    public record ExchangeRateUpdateRequest(BigDecimal rate, Boolean manualOverride) {
    }

    public record ExchangeRateListResponse<T>(List<T> items) {
    }

    /** order-flow-complete §2.3：追加 5 个新字段（范围校验见 CheckoutConfigService） */
    public record CheckoutConfigDto(BigDecimal giftWrapFeeUsd, Integer customRefundGraceHours,
                                    Integer autoCompleteDays, Integer autoDeliverDays,
                                    Integer pendingTimeoutMinutes, Integer exchangeRateSpreadScaled,
                                    Integer productionDaysDefault) {
    }

    // ==================== 订单时间线 / 制作阶段（order-flow-complete §2.2/§3） ====================

    public record OrderEventDto(Long id, Integer type, Integer actorType, Long actorId, String actorName,
                                String title, String detail, Map<String, Object> payload,
                                Boolean customerVisible, LocalDateTime createdAt) {
    }

    public record OrderNoteCreate(String content, Boolean customerVisible) {
    }

    public record ProductionStagePatch(Integer stage) {
    }

    public record ReorderSkipped(Long orderLineId, Integer reasonCode) {
    }

    public record ReorderResponse(Integer addedCount, List<ReorderSkipped> skipped) {
    }

    // ==================== 包裹 / 轨迹（order-flow-complete §2.2 shipment） ====================

    public record ShipmentLineCreate(Long orderLineId, Integer qty) {
    }

    public record ShipmentLineDto(Long orderLineId, String productName, String skuCode, String color, String size,
                                  Integer qty) {
    }

    public record ShipmentEventDto(Long id, LocalDateTime occurredAt, Integer status, String location,
                                   String description, Integer source) {
    }

    public record ShipmentDto(Long id, String shipmentNo, String carrierCode, String carrierName, String trackingNo,
                              String trackingUrl, Integer status, LocalDateTime shippedAt, LocalDateTime deliveredAt,
                              LocalDateTime lastEventAt, String lastEventDesc, List<ShipmentLineDto> lines,
                              List<ShipmentEventDto> events) {
    }

    public record ShipmentCreateRequest(String carrierCode, String trackingNo, List<ShipmentLineCreate> lines) {
    }

    public record ShipmentPatch(String carrierCode, String trackingNo) {
    }

    public record ShipmentEventCreate(Integer status, LocalDateTime occurredAt, String location, String description) {
    }

    /** 游客查单（订单号 + 邮箱；脱敏视图） */
    public record OrderTrackRequest(String orderNo, String email) {
    }

    public record OrderTrackView(String orderNo, Integer status, Integer productionStage, String currency,
                                 BigDecimal totalAmount, LocalDateTime createdAt, LocalDateTime paidAt,
                                 LocalDateTime shippedAt, LocalDateTime deliveredAt, LocalDateTime completedAt,
                                 LocalDate estimatedDeliveryFrom, LocalDate estimatedDeliveryTo,
                                 String receiverMasked, String countryCode, List<ShipmentDto> shipments,
                                 List<OrderEventDto> events) {
    }

    // ==================== 税费（order-flow-complete §2.2 tax_rule / tax_destination_policy） ====================

    public record TaxBreakdownDto(Integer type, String label, Integer rateScaled, BigDecimal base, BigDecimal amount)
            implements Serializable {
    }

    public record TaxRuleDto(Long id, String countryCode, String region, Integer taxType, Integer rateScaled,
                             Boolean appliesToShipping, BigDecimal thresholdUsd, LocalDate effectiveFrom,
                             LocalDate effectiveTo, Boolean enabled, String label, LocalDateTime updatedAt) {
    }

    public record TaxRuleUpsert(String countryCode, String region, Integer taxType, Integer rateScaled,
                                Boolean appliesToShipping, BigDecimal thresholdUsd, LocalDate effectiveFrom,
                                LocalDate effectiveTo, Boolean enabled, String label) {
    }

    public record TaxRuleEnabledPatch(Boolean enabled) {
    }

    public record TaxDestinationPolicyDto(String countryCode, Integer incoterm, Boolean dutiesNotice,
                                          String noticeText, LocalDateTime updatedAt) {
    }

    public record TaxDestinationPolicyUpsert(Integer incoterm, Boolean dutiesNotice, String noticeText) {
    }

    // ==================== 国家 / 分区 / 运费选项（order-flow-complete §2.3 shipping_option） ====================

    public record RegionDto(String code, String name) implements Serializable {
    }

    public record CountryDto(String code, String name, String zone, Boolean supported, List<RegionDto> regions)
            implements Serializable {
    }

    public record CountryListResponse(List<CountryDto> items) implements Serializable {
    }

    public record ShippingOptionAdminDto(Long id, String zone, String carrierCode, String carrierName,
                                         Integer serviceLevel, BigDecimal feeUnder, BigDecimal feeOver,
                                         BigDecimal threshold, Integer transitDaysMin, Integer transitDaysMax,
                                         Boolean enabled, LocalDateTime updatedAt) {
    }

    public record ShippingOptionUpsert(String zone, String carrierCode, Integer serviceLevel, BigDecimal feeUnder,
                                       BigDecimal feeOver, BigDecimal threshold, Integer transitDaysMin,
                                       Integer transitDaysMax, Boolean enabled) {
    }

    public record ShippingQuotePreviewRequest(String countryCode, String regionCode, BigDecimal subtotalUsd,
                                              Integer serviceLevel) {
    }

    public record ShippingQuotePreviewResponse(String zone, List<ShippingOptionDto> options, BigDecimal taxAmountUsd,
                                               List<TaxBreakdownDto> taxBreakdown, Integer incoterm,
                                               Boolean dutiesNotice) {
    }

    // ==================== 汇率历史 / 刷新（order-flow-complete §2.2 exchange_rate_history） ====================

    public record ExchangeRateHistoryDto(String currency, BigDecimal rate, Integer source, LocalDate quoteDate,
                                         LocalDateTime recordedAt) {
    }

    public record ExchangeRateRefreshResponse(Integer updatedCount, List<String> skippedCurrencies,
                                              LocalDateTime syncedAt) {
    }

    // ==================== webhook ====================

    public record WebhookReceived(Boolean received) {
    }
}
