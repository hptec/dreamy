package com.dreamy.security;

import com.dreamy.domain.showroom.entity.Showroom;
import com.dreamy.domain.showroom.repository.ShowroomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * ShowroomGuestValidator 实现单元测试（0.2-d：DB 点查 invite 版本 + 行存在性，fail-closed 401101）。
 * L2 TRACE: SHR-IMPL-FILTER-GUEST / TC-SHR-013 单测面（inv_ver 不等失效）/ TC-SHR-018 单测面（行不存在失效）。
 */
@ExtendWith(MockitoExtension.class)
class ShowroomGuestValidatorImplTest {

    @Mock
    ShowroomRepository showroomRepository;
    @InjectMocks
    ShowroomGuestValidatorImpl validator;

    private Showroom room(int version) {
        Showroom s = new Showroom();
        s.setId(8L);
        s.setInviteVersion(version);
        return s;
    }

    @Test
    @DisplayName("行存在且 invite_version 等值 → 有效")
    void validSession() {
        when(showroomRepository.findById(8L)).thenReturn(room(3));
        assertThat(validator.isGuestSessionValid(8L, 3L)).isTrue();
    }

    @Test
    @DisplayName("invite_version 不等（邀请已重置）→ 无效（401101 通道②）")
    void versionMismatch() {
        when(showroomRepository.findById(8L)).thenReturn(room(4));
        assertThat(validator.isGuestSessionValid(8L, 3L)).isFalse();
    }

    @Test
    @DisplayName("行不存在（Showroom 已删除）→ 无效（E-SHR-05 即时失效闭环）")
    void rowMissing() {
        when(showroomRepository.findById(8L)).thenReturn(null);
        assertThat(validator.isGuestSessionValid(8L, 3L)).isFalse();
    }
}
