package com.dreamy.domain.site_builder.service;

import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.repository.AnnouncementRepository;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementDto;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementUpsert;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AnnouncementService 单元测试（TC-U036~U050）。
 * 覆盖 acceptance s-005~s-006（状态机）+ bs-201~bs-280（边界场景）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnnouncementService 单元测试")
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository repository;
    @Mock
    private SiteBuilderCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(repository, objectMapper, cacheService);
    }

    @Test
    @DisplayName("TC-A001: create 正常路径")
    void create_success() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(1);
        upsert.setStartAt(LocalDateTime.of(2026, 6, 23, 0, 0));
        upsert.setEndAt(LocalDateTime.of(2026, 6, 30, 0, 0));
        JsonNode contentI18n = objectMapper.readTree("{\"en\":{\"content\":\"Free shipping\"},\"es\":{\"content\":\"Envío gratis\"}}");
        upsert.setContentI18nJson(contentI18n);

        when(repository.findOverlapByPriorityAndTimeForUpdate(any(), any(), any())).thenReturn(List.of());
        when(repository.insert(any(Announcement.class))).thenAnswer(i -> {
            ((Announcement) i.getArgument(0)).setId(1L);
            return 1;
        });

        AnnouncementDto result = service.create(upsert);

        assertThat(result).isNotNull();
        assertThat(result.getPriority()).isEqualTo(1);
        verify(cacheService).invalidateAnnouncementFamily();
    }

    @Test
    @DisplayName("TC-A002: create 时间窗冲突 → 409804")
    void create_timeWindowConflict_throwsConflict() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(1);
        JsonNode contentI18n = objectMapper.readTree("{\"en\":{\"content\":\"Test\"}}");
        upsert.setContentI18nJson(contentI18n);

        Announcement existing = new Announcement();
        existing.setId(99L);
        when(repository.findOverlapByPriorityAndTimeForUpdate(any(), isNull(), isNull())).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.ANNOUNCEMENT_TIME_WINDOW_CONFLICT));
    }

    @Test
    @DisplayName("TC-A003: create start_at >= end_at → 422805")
    void create_invalidTimeWindow_throwsInvalid() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(1);
        upsert.setStartAt(LocalDateTime.of(2026, 6, 30, 0, 0));
        upsert.setEndAt(LocalDateTime.of(2026, 6, 23, 0, 0));  // 倒置
        JsonNode contentI18n = objectMapper.readTree("{\"en\":{\"content\":\"Test\"}}");
        upsert.setContentI18nJson(contentI18n);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.ANNOUNCEMENT_TIME_WINDOW_INVALID));
    }

    @Test
    @DisplayName("TC-A004: create priority 为负 → 422808")
    void create_negativePriority_throwsMismatch() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(-1);
        JsonNode contentI18n = objectMapper.readTree("{\"en\":{\"content\":\"Test\"}}");
        upsert.setContentI18nJson(contentI18n);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class);
    }

    @Test
    @DisplayName("TC-A005: create contentI18nJson 缺失 → 422807")
    void create_missingContentI18n_throwsInvalid() {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(0);
        upsert.setContentI18nJson(null);

        assertThatThrownBy(() -> service.create(upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.I18N_JSON_INVALID));
    }

    @Test
    @DisplayName("TC-A006: update 不存在 → 404804")
    void update_notFound_throwsNotFound() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(0);
        upsert.setContentI18nJson(objectMapper.readTree("{\"en\":{\"content\":\"x\"}}"));
        upsert.setVersion(0);

        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, upsert))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.ANNOUNCEMENT_NOT_FOUND));
    }

    @Test
    @DisplayName("TC-A007: toggle 启停切换")
    void toggle_success() {
        Announcement existing = new Announcement();
        existing.setId(1L);
        existing.setEnabled(false);
        existing.setVersion(0);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.updateEnabled(1L, true, 0)).thenReturn(1);

        AnnouncementDto result = service.toggle(1L, true);

        assertThat(result.getEnabled()).isTrue();
        verify(cacheService).invalidateAnnouncementFamily();
    }

    @Test
    @DisplayName("TC-A008: delete 正常删除")
    void delete_success() {
        Announcement existing = new Announcement();
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(repository).deleteById(1L);
        verify(cacheService).invalidateAnnouncementFamily();
    }

    @Test
    @DisplayName("TC-A009: create 无时间窗（永久）合法")
    void create_noTimeWindow_success() throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(0);
        upsert.setStartAt(null);
        upsert.setEndAt(null);
        upsert.setContentI18nJson(objectMapper.readTree("{\"en\":{\"content\":\"Welcome\"}}"));

        when(repository.findOverlapByPriorityAndTimeForUpdate(0, null, null)).thenReturn(List.of());
        when(repository.insert(any())).thenAnswer(i -> {
            ((Announcement) i.getArgument(0)).setId(1L);
            return 1;
        });

        AnnouncementDto result = service.create(upsert);
        assertThat(result).isNotNull();
        verify(repository).findOverlapByPriorityAndTimeForUpdate(0, null, null);
    }

    @Test
    @DisplayName("TC-A010: update 排除自身的时间窗冲突检查")
    void update_excludesSelf_noConflict() throws Exception {
        Announcement existing = new Announcement();
        existing.setId(1L);
        existing.setVersion(0);

        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(1);
        upsert.setContentI18nJson(objectMapper.readTree("{\"en\":{\"content\":\"x\"}}"));
        upsert.setVersion(0);

        // 模拟数据库返回包含自身的冲突记录（应被排除）
        Announcement self = new Announcement();
        self.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findOverlapByPriorityAndTimeForUpdate(1, null, null)).thenReturn(List.of(self));
        when(repository.updateByIdAndVersion(any())).thenReturn(1);

        AnnouncementDto result = service.update(1L, upsert);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("连续两次更新使用响应版本，均成功并返回数据库下一版本")
    void update_consecutiveUpdatesReturnNextVersion() throws Exception {
        Announcement existing = new Announcement();
        existing.setId(1L);
        existing.setVersion(0);

        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(1);
        upsert.setContentI18nJson(objectMapper.readTree("{\"en\":{\"content\":\"x\"}}"));
        upsert.setVersion(0);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findOverlapByPriorityAndTimeForUpdate(1, null, null)).thenReturn(List.of(existing));
        when(repository.updateByIdAndVersion(same(existing))).thenAnswer(invocation -> {
            existing.setVersion(existing.getVersion() + 1);
            return 1;
        });

        AnnouncementDto first = service.update(1L, upsert);
        upsert.setVersion(first.getVersion());
        AnnouncementDto second = service.update(1L, upsert);

        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(second.getVersion()).isEqualTo(2);
        verify(cacheService, times(2)).invalidateAnnouncementFamily();
    }
}
