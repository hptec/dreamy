package com.dreamy.showroom.domain.member.service;

import com.dreamy.identity.security.JwtTokenProvider;
import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.domain.showroom.entity.Showroom;
import com.dreamy.showroom.domain.showroom.repository.ShowroomRepository;
import com.dreamy.showroom.dto.ShowroomDtos.GuestSessionCreate;
import com.dreamy.showroom.dto.ShowroomDtos.GuestSessionDto;
import com.dreamy.showroom.error.ShowroomErrorCode;
import com.dreamy.showroom.error.ShowroomException;
import com.dreamy.showroom.testsupport.ImmediateShowroomTxRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * E-SHR-07 guest-session 裁决单元测试。
 * L2 TRACE: TC-SHR-011 [P0]（CV-SHR-009 受保护昵称 409101 / 本人 store token 复用 / 未绑定复用）/
 * TC-SHR-012 [P0]（绑定回填幂等）/ TC-SHR-013 单测面（invite 失效双通道：prev→410101 / 未知→401101）/
 * TC-SHR-010 单测面（uk 冲突回读重裁决）。
 */
@ExtendWith(MockitoExtension.class)
class GuestSessionServiceTest {

    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";
    private static final long ROOM = 8L;

    @Mock
    ShowroomRepository showroomRepository;
    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;

    GuestSessionService service;

    Showroom room;

    @BeforeEach
    void setUp() {
        service = new GuestSessionService(showroomRepository, memberRepository, jwtTokenProvider,
                new ImmediateShowroomTxRunner());
        room = new Showroom();
        room.setId(ROOM);
        room.setOwnerId(1L);
        room.setName("Sarah's Bridal Party");
        room.setInviteToken(TOKEN);
        room.setInviteVersion(3);
        lenient().when(jwtTokenProvider.issueShowroomGuestToken(anyLong(), anyLong(), anyLong()))
                .thenReturn(new JwtTokenProvider.GuestToken("jwt-token", "jti-1",
                        LocalDateTime.of(2026, 6, 11, 12, 0)));
    }

    private ShowroomMember member(long id, String nickname, Long linked) {
        ShowroomMember m = new ShowroomMember();
        m.setId(id);
        m.setShowroomId(ROOM);
        m.setNickname(nickname);
        m.setLinkedCustomerId(linked);
        m.setAssignStatus(AssignStatus.UNASSIGNED);
        return m;
    }

    @Test
    @DisplayName("token 当前值未命中且 prev 命中 → 410101 INVITE_TOKEN_REVOKED（重置识别）")
    void revokedTokenGone() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(null);
        when(showroomRepository.existsByInviteTokenPrev(TOKEN)).thenReturn(true);
        assertThatThrownBy(() -> service.createSession(new GuestSessionCreate(TOKEN, "Emma"), null))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.INVITE_TOKEN_REVOKED));
    }

    @Test
    @DisplayName("token 双侧皆未命中 → 401101 GUEST_TOKEN_INVALID（防探测不区分更多细节）")
    void unknownTokenUnauthorized() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(null);
        when(showroomRepository.existsByInviteTokenPrev(TOKEN)).thenReturn(false);
        assertThatThrownBy(() -> service.createSession(new GuestSessionCreate(TOKEN, "Emma"), null))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.GUEST_TOKEN_INVALID));
    }

    @Test
    @DisplayName("新昵称 → INSERT member(unassigned) + 签发 JWT（claims=member/room/inv_ver）")
    void newNicknameCreatesMember() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(room);
        when(memberRepository.findByShowroomAndNickname(ROOM, "Emma")).thenReturn(null);
        doAnswer(invocation -> {
            ShowroomMember m = invocation.getArgument(0);
            m.setId(77L);
            return null;
        }).when(memberRepository).insert(any(ShowroomMember.class));

        GuestSessionDto dto = service.createSession(new GuestSessionCreate(TOKEN, " Emma "), null);

        assertThat(dto.guestToken()).isEqualTo("jwt-token");
        assertThat(dto.showroomId()).isEqualTo(ROOM);
        assertThat(dto.member().id()).isEqualTo(77L);
        assertThat(dto.member().assignStatus()).isEqualTo("unassigned");
        // MAP-SHR-006 本人回执：不含 linked_customer_id
        assertThat(dto.member().linkedCustomerId()).isNull();
        verify(jwtTokenProvider).issueShowroomGuestToken(77L, ROOM, 3L);
    }

    @Test
    @DisplayName("受保护昵称：已绑定他人 → 匿名/他人复用 409101；本人 store token 重放 → 200 复用")
    void protectedNickname() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(room);
        when(memberRepository.findByShowroomAndNickname(ROOM, "Emma"))
                .thenReturn(member(77L, "Emma", 555L));

        // 匿名 → 409101
        assertThatThrownBy(() -> service.createSession(new GuestSessionCreate(TOKEN, "Emma"), null))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.NICKNAME_TAKEN));
        // 他人 → 409101
        assertThatThrownBy(() -> service.createSession(new GuestSessionCreate(TOKEN, "Emma"), 666L))
                .isInstanceOfSatisfying(ShowroomException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ShowroomErrorCode.NICKNAME_TAKEN));
        // 本人 → 复用
        GuestSessionDto dto = service.createSession(new GuestSessionCreate(TOKEN, "Emma"), 555L);
        assertThat(dto.member().id()).isEqualTo(77L);
        verify(memberRepository, never()).insert(any());
    }

    @Test
    @DisplayName("绑定回填：登录态首访未绑定行 → bindCustomer 幂等条件更新（TC-SHR-012）")
    void bindBackfill() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(room);
        when(memberRepository.findByShowroomAndNickname(ROOM, "Emma"))
                .thenReturn(member(77L, "Emma", null));

        service.createSession(new GuestSessionCreate(TOKEN, "Emma"), 555L);
        verify(memberRepository).bindCustomer(77L, 555L);
    }

    @Test
    @DisplayName("并发 uk 冲突 → 回读按复用规则重新裁决（CV-SHR-004，两请求同 member）")
    void concurrentInsertReread() {
        when(showroomRepository.findByInviteToken(TOKEN)).thenReturn(room);
        when(memberRepository.findByShowroomAndNickname(ROOM, "Emma"))
                .thenReturn(null)
                .thenReturn(member(77L, "Emma", null));
        doThrow(new DuplicateKeyException("uk_sm_room_nickname"))
                .when(memberRepository).insert(any(ShowroomMember.class));

        GuestSessionDto dto = service.createSession(new GuestSessionCreate(TOKEN, "Emma"), null);
        assertThat(dto.member().id()).isEqualTo(77L);
    }
}
