package com.dreamy.showroom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * showroom 域 DTO 集（MAP-SHR-001~008）。
 * JSON 字段 snake_case（CP-001，全局 SNAKE_CASE 策略）；可选字段 NON_NULL 省略
 * （契约「guest 视图不含 invite_token」「members[].email 仅 owner 视图」等以字段不存在而非 null 承载，
 * TC-SHR-033 视图裁剪断言）。
 * L2 TRACE: showroom-data-detail §3 / showroom-api.openapi.yml components.schemas。
 */
public final class ShowroomDtos {

    private ShowroomDtos() {
    }

    // ==================== 请求 ====================

    /** ShowroomUpsert（E-SHR-01/04；wedding_date 字符串承载以落 V-SHR-002 invalid_date 口径） */
    public record ShowroomUpsert(String name, String weddingDate) {
    }

    /** E-SHR-07 guest-session 请求 */
    public record GuestSessionCreate(String inviteToken, String nickname) {
    }

    /** E-SHR-08 添加款式请求 */
    public record ItemCreate(Long productId, String color) {
    }

    /** E-SHR-10 投票请求（vote 字符串承载以落 V-SHR-017 invalid_enum 口径） */
    public record VoteRequest(String vote) {
    }

    /** E-SHR-11 留言请求 */
    public record CommentCreate(String content) {
    }

    /** E-SHR-12 指派请求 */
    public record AssignRequest(Long assignedItemId, String email) {
    }

    // ==================== 响应 ====================

    /** ProductRef 内嵌商品卡片（MAP-SHR-004 经 CatalogSnapshotPort，文案已按 locale 解析） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProductRefDto(Long id, String slug, String name, BigDecimal price, String imageUrl,
                                Boolean customSizeAvailable, Integer leadTimeDays) {
    }

    /** ShowroomSummary（MAP-SHR-001：不含 invite_token/invite_version/invite_token_prev） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShowroomSummaryDto(Long id, Long ownerId, String name, LocalDate weddingDate,
                                     Integer itemCount, Integer memberCount) {
    }

    /** ShowroomComment（MAP-SHR-005：nickname 联 member 派生） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShowroomCommentDto(Long id, Long showroomItemId, Long memberId, String nickname,
                                     String content, LocalDateTime createdAt) {
    }

    /** ShowroomItem（MAP-SHR-004：color 空串省略；my_vote 未投省略；不暴露 last_ordered_at 原始值） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShowroomItemDto(Long id, Long productId, String color, ProductRefDto product,
                                  Integer likeCount, Integer dislikeCount, String myVote,
                                  List<ShowroomCommentDto> comments, Boolean dyeLotNotice) {
    }

    /** ShowroomMember（MAP-SHR-006：email/linked_customer_id 仅 owner 视图非 null 输出） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShowroomMemberDto(Long id, Long showroomId, String nickname, String email,
                                    Long assignedItemId, String assignStatus, Long linkedCustomerId) {
    }

    /** ShowroomDetail（MAP-SHR-002 owner 视图 / MAP-SHR-003 guest 视图，allOf 平铺） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShowroomDetailDto(Long id, Long ownerId, String name, LocalDate weddingDate,
                                    Integer itemCount, Integer memberCount, String inviteToken,
                                    Boolean isOwner, Long myMemberId, List<ShowroomItemDto> items,
                                    List<ShowroomMemberDto> members) {
    }

    /** GuestSession（MAP-SHR-007：guest_token 裸 JWT 仅响应体出现，日志 [REDACTED]） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GuestSessionDto(String guestToken, LocalDateTime expiresAt, Long showroomId,
                                  ShowroomMemberDto member) {
    }

    /** E-SHR-02 列表包装 */
    public record ShowroomListDto(List<ShowroomSummaryDto> items) {
    }

    /** E-SHR-06 重置邀请响应（新 token，仅 owner 可见） */
    public record InviteTokenDto(String inviteToken) {
    }

    /** E-SHR-10 投票响应（实时聚合，FLOW-P12） */
    public record VoteResultDto(Integer likeCount, Integer dislikeCount, String myVote) {
    }
}
