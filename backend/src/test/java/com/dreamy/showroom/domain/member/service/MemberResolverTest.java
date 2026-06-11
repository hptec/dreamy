package com.dreamy.showroom.domain.member.service;

import com.dreamy.showroom.domain.enums.AssignStatus;
import com.dreamy.showroom.domain.member.entity.ShowroomMember;
import com.dreamy.showroom.domain.member.repository.ShowroomMemberRepository;
import com.dreamy.showroom.port.IdentityQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * owner 自动建 member 昵称规则单元测试。
 * L2 TRACE: TC-SHR-005 [P1]（getUserName 截断 32；uk 冲突 `#`+subject 末 4 位 ≤32；二次确定性）/
 * TC-SHR-015 单测面（复用不重建 / 冲突后缀重试，E-SHR-10 STEP-SHR-03）。
 */
@ExtendWith(MockitoExtension.class)
class MemberResolverTest {

    private static final long ROOM = 5L;
    private static final long CUSTOMER = 123456789L;

    @Mock
    ShowroomMemberRepository memberRepository;
    @Mock
    IdentityQueryPort identityPort;

    MemberResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new MemberResolver(memberRepository, identityPort);
    }

    @Test
    @DisplayName("昵称规则：getUserName trim 截断 32；空名回退确定性缺省")
    void baseNicknameRule() {
        when(identityPort.getUserName(CUSTOMER)).thenReturn("  " + "n".repeat(40) + "  ");
        assertThat(resolver.baseNickname(CUSTOMER)).hasSize(32);

        when(identityPort.getUserName(CUSTOMER)).thenReturn(null);
        assertThat(resolver.baseNickname(CUSTOMER)).isEqualTo("Owner");
    }

    @Test
    @DisplayName("冲突后缀规则：`#`+subject 末 4 位、整体 ≤32、同输入同输出（确定性）")
    void suffixRuleDeterministic() {
        String first = resolver.suffixedNickname("n".repeat(32), CUSTOMER);
        String second = resolver.suffixedNickname("n".repeat(32), CUSTOMER);
        assertThat(first).isEqualTo(second).hasSize(32).endsWith("#6789");

        assertThat(resolver.suffixedNickname("Emma", CUSTOMER)).isEqualTo("Emma#6789");
        // subject 不足 4 位：全量
        assertThat(resolver.suffixedNickname("Emma", 12L)).isEqualTo("Emma#12");
    }

    @Test
    @DisplayName("已绑定行复用不重建（再次互动）")
    void reuseExisting() {
        ShowroomMember existing = new ShowroomMember();
        existing.setId(9L);
        when(memberRepository.findByShowroomAndLinkedCustomer(ROOM, CUSTOMER)).thenReturn(existing);

        assertThat(resolver.resolveStoreMember(ROOM, CUSTOMER)).isSameAs(existing);
        verify(memberRepository, times(0)).insert(any());
    }

    @Test
    @DisplayName("首次互动自动建 member：linked_customer_id=owner、assign_status=unassigned；昵称占用后缀重试")
    void autoCreateWithSuffixRetry() {
        when(memberRepository.findByShowroomAndLinkedCustomer(ROOM, CUSTOMER)).thenReturn(null);
        lenient().when(identityPort.getUserName(CUSTOMER)).thenReturn("Emma");
        AtomicLong idGen = new AtomicLong(100);
        // 第一次 insert 冲突（昵称被访客占用），第二次成功
        doThrow(new DuplicateKeyException("uk_sm_room_nickname"))
                .doAnswer(invocation -> {
                    ShowroomMember m = invocation.getArgument(0);
                    m.setId(idGen.incrementAndGet());
                    return null;
                })
                .when(memberRepository).insert(any(ShowroomMember.class));

        ShowroomMember created = resolver.resolveStoreMember(ROOM, CUSTOMER);
        assertThat(created.getNickname()).isEqualTo("Emma#6789");
        assertThat(created.getLinkedCustomerId()).isEqualTo(CUSTOMER);
        assertThat(created.getAssignStatus()).isEqualTo(AssignStatus.UNASSIGNED);
        verify(memberRepository, times(2)).insert(any(ShowroomMember.class));
    }
}
