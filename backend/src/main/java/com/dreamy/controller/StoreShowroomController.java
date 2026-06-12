package com.dreamy.controller;

import com.dreamy.error.BizException;
import com.dreamy.error.ErrorCode;
import com.dreamy.security.AuthContext;
import com.dreamy.security.AuthPrincipal;
import com.dreamy.security.GuestContext;
import com.dreamy.domain.member.service.GuestSessionService;
import com.dreamy.domain.member.service.ShowroomInteractionService;
import com.dreamy.domain.member.service.ShowroomInteractionService.Interactor;
import com.dreamy.domain.member.service.ShowroomMemberService;
import com.dreamy.domain.showroom.service.ShowroomItemService;
import com.dreamy.domain.showroom.service.StoreShowroomService;
import com.dreamy.dto.ShowroomDtos.AssignRequest;
import com.dreamy.dto.ShowroomDtos.CommentCreate;
import com.dreamy.dto.ShowroomDtos.GuestSessionCreate;
import com.dreamy.dto.ShowroomDtos.GuestSessionDto;
import com.dreamy.dto.ShowroomDtos.InviteTokenDto;
import com.dreamy.dto.ShowroomDtos.ItemCreate;
import com.dreamy.dto.ShowroomDtos.ShowroomCommentDto;
import com.dreamy.dto.ShowroomDtos.ShowroomDetailDto;
import com.dreamy.dto.ShowroomDtos.ShowroomItemDto;
import com.dreamy.dto.ShowroomDtos.ShowroomListDto;
import com.dreamy.dto.ShowroomDtos.ShowroomMemberDto;
import com.dreamy.dto.ShowroomDtos.ShowroomUpsert;
import com.dreamy.dto.ShowroomDtos.VoteRequest;
import com.dreamy.dto.ShowroomDtos.VoteResultDto;
import com.dreamy.support.ShowroomFieldErrors;
import com.dreamy.support.ShowroomValidation;
import huihao.web.R;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费端 Showroom 控制器（E-SHR-01~13，13 端点，F-066~071）。
 * 鉴权（§0 双态矩阵）：StoreJwtFilter 四段裁决前置——匿名仅 guest-session（白名单
 * POST:/api/store/showrooms/guest-session，principal 可选注入）；guest 旁路放行 3 端点
 * （GET 详情 / PUT vote / POST comments，403102 越权由过滤器层产出）；其余 StoreBearerAuth。
 * 服务层 owner 强隔离 404101 防探测 + 403101 双保险口径见各 Service。
 * 缓存（CACHE-SHR-001）：13 端点全部不缓存，响应 `Cache-Control: private, no-store`。
 * L2 TRACE: SHR-IMPL-API / showroom-api-detail E-SHR-01~13。
 */
@RestController
public class StoreShowroomController {

    private static final String NO_STORE = "private, no-store";

    private final StoreShowroomService showroomService;
    private final GuestSessionService guestSessionService;
    private final ShowroomItemService itemService;
    private final ShowroomInteractionService interactionService;
    private final ShowroomMemberService memberService;

    public StoreShowroomController(StoreShowroomService showroomService,
                                   GuestSessionService guestSessionService,
                                   ShowroomItemService itemService,
                                   ShowroomInteractionService interactionService,
                                   ShowroomMemberService memberService) {
        this.showroomService = showroomService;
        this.guestSessionService = guestSessionService;
        this.itemService = itemService;
        this.interactionService = interactionService;
        this.memberService = memberService;
    }

    // ==================== SHOWROOMS ====================

    /** E-SHR-01 createShowroom（StoreBearerAuth；TX-SHR-001） */
    @PostMapping("/api/store/showrooms")
    public ResponseEntity<R<ShowroomDetailDto>> create(@RequestBody ShowroomUpsert req) {
        return noStore(201, R.ok(showroomService.create(customerId(), req)));
    }

    /** E-SHR-02 listShowrooms（StoreBearerAuth；仅自己的） */
    @GetMapping("/api/store/showrooms")
    public ResponseEntity<R<ShowroomListDto>> list() {
        return noStore(200, R.ok(showroomService.list(customerId())));
    }

    /** E-SHR-03 getShowroom（双态：StoreBearerAuth 或 ShowroomGuestAuth；V-SHR-004 locale） */
    @GetMapping("/api/store/showrooms/{id}")
    public ResponseEntity<R<ShowroomDetailDto>> get(@PathVariable Long id,
                                                    @RequestParam(required = false) String locale) {
        ShowroomFieldErrors errors = new ShowroomFieldErrors();
        String parsedLocale = ShowroomValidation.validateLocale(locale, errors);
        errors.throwIfAny();
        GuestContext guest = GuestContext.get();
        if (guest != null) {
            // STEP-SHR-01 guest 分支（过滤器已校验 {id}=claims.showroom_id 与 invite_version）
            return noStore(200, R.ok(showroomService.getForGuest(
                    guest.showroomId(), guest.memberId(), parsedLocale)));
        }
        return noStore(200, R.ok(showroomService.getForOwner(customerId(), id, parsedLocale)));
    }

    /** E-SHR-04 updateShowroom（仅 owner；TX-SHR-002） */
    @PutMapping("/api/store/showrooms/{id}")
    public ResponseEntity<R<ShowroomDetailDto>> update(@PathVariable Long id, @RequestBody ShowroomUpsert req) {
        return noStore(200, R.ok(showroomService.update(customerId(), id, req)));
    }

    /** E-SHR-05 deleteShowroom（仅 owner；TX-SHR-003 级联） */
    @DeleteMapping("/api/store/showrooms/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        showroomService.delete(customerId(), id);
        return ResponseEntity.status(204).header("Cache-Control", NO_STORE).build();
    }

    /** E-SHR-06 resetShowroomInvite（仅 owner；TX-SHR-004 级联失效核心） */
    @PostMapping("/api/store/showrooms/{id}/invite/reset")
    public ResponseEntity<R<InviteTokenDto>> resetInvite(@PathVariable Long id) {
        return noStore(200, R.ok(showroomService.resetInvite(customerId(), id)));
    }

    // ==================== GUEST SESSION ====================

    /** E-SHR-07 createShowroomGuestSession（公开白名单；principal 可选注入做绑定回填；TX-SHR-005） */
    @PostMapping("/api/store/showrooms/guest-session")
    public ResponseEntity<R<GuestSessionDto>> createGuestSession(@RequestBody GuestSessionCreate req) {
        return noStore(200, R.ok(guestSessionService.createSession(req, optionalCustomerId())));
    }

    // ==================== ITEMS ====================

    /** E-SHR-08 addShowroomItem（仅 owner；TX-SHR-006；409102/404501） */
    @PostMapping("/api/store/showrooms/{id}/items")
    public ResponseEntity<R<ShowroomItemDto>> addItem(@PathVariable Long id, @RequestBody ItemCreate req) {
        return noStore(201, R.ok(itemService.add(customerId(), id, req)));
    }

    /** E-SHR-09 removeShowroomItem（仅 owner；TX-SHR-007 级联回退） */
    @DeleteMapping("/api/store/showrooms/{id}/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        itemService.remove(customerId(), id, itemId);
        return ResponseEntity.status(204).header("Cache-Control", NO_STORE).build();
    }

    // ==================== VOTES & COMMENTS ====================

    /** E-SHR-10 voteShowroomItem（双态；PUT 幂等去重；TX-SHR-008） */
    @PutMapping("/api/store/showrooms/{id}/items/{itemId}/vote")
    public ResponseEntity<R<VoteResultDto>> vote(@PathVariable Long id, @PathVariable Long itemId,
                                                 @RequestBody VoteRequest req) {
        return noStore(200, R.ok(interactionService.vote(interactor(), id, itemId, req)));
    }

    /** E-SHR-11 commentShowroomItem（双态；TX-SHR-009） */
    @PostMapping("/api/store/showrooms/{id}/items/{itemId}/comments")
    public ResponseEntity<R<ShowroomCommentDto>> comment(@PathVariable Long id, @PathVariable Long itemId,
                                                         @RequestBody CommentCreate req) {
        return noStore(201, R.ok(interactionService.comment(interactor(), id, itemId, req)));
    }

    // ==================== MEMBERS ====================

    /** E-SHR-12 assignShowroomMember（仅 owner；TX-SHR-010；CAS 409103 + showroom.invite 事件） */
    @PostMapping("/api/store/showrooms/{id}/members/{memberId}/assign")
    public ResponseEntity<R<ShowroomMemberDto>> assign(@PathVariable Long id, @PathVariable Long memberId,
                                                       @RequestBody AssignRequest req) {
        return noStore(200, R.ok(memberService.assign(customerId(), id, memberId, req)));
    }

    /** E-SHR-13 remindShowroomMember（仅 owner；TX-SHR-011；showroom.remind 真发邮件事件） */
    @PostMapping("/api/store/showrooms/{id}/members/{memberId}/remind")
    public ResponseEntity<R<ShowroomMemberDto>> remind(@PathVariable Long id, @PathVariable Long memberId) {
        return noStore(200, R.ok(memberService.remind(customerId(), id, memberId)));
    }

    // ==================== 主体解析 ====================

    /** JWT sub（store=user_id）转 Long（BE-DIM-6：请求体夹带身份字段一律忽略） */
    private Long customerId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !AuthPrincipal.TYPE_STORE.equals(principal.type())) {
            // guest 主体落到 owner 专属分支的服务层双保险外层兜底（0.2-b 过滤器已拦 403102，此处理论不可达）
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return Long.parseLong(principal.subject());
    }

    /** E-SHR-07 白名单可选注入（catalog §0 / showroom 0.1 口径）：有效 store principal → id，匿名 → null */
    private Long optionalCustomerId() {
        AuthPrincipal principal = AuthContext.get();
        if (principal == null || !AuthPrincipal.TYPE_STORE.equals(principal.type())) {
            return null;
        }
        return Long.parseLong(principal.subject());
    }

    /** 双态端点互动主体分流（guest 经 0.2-e 旁路上下文；服务层不信任请求体身份字段） */
    private Interactor interactor() {
        GuestContext guest = GuestContext.get();
        if (guest != null) {
            return Interactor.guest(guest.memberId());
        }
        return Interactor.store(customerId());
    }

    private <T> ResponseEntity<R<T>> noStore(int status, R<T> body) {
        return ResponseEntity.status(status).header("Cache-Control", NO_STORE).body(body);
    }
}
