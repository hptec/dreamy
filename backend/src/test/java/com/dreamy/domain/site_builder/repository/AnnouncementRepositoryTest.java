package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dreamy.domain.site_builder.entity.Announcement;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementRepositoryTest {

    @Mock
    private AnnouncementMapper mapper;

    private AnnouncementRepository repository;

    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, Announcement.class);
    }

    @BeforeEach
    void setUp() {
        repository = new AnnouncementRepository(mapper);
    }

    @Test
    void updateByIdAndVersionIncrementsDatabaseAndEntityVersionOnConsecutiveUpdates() {
        Announcement announcement = new Announcement();
        announcement.setId(7L);
        announcement.setVersion(2);
        when(mapper.update(same(announcement), any())).thenReturn(1, 1);

        assertThat(repository.updateByIdAndVersion(announcement)).isEqualTo(1);
        assertThat(announcement.getVersion()).isEqualTo(3);
        assertThat(repository.updateByIdAndVersion(announcement)).isEqualTo(1);
        assertThat(announcement.getVersion()).isEqualTo(4);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> wrappers = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper, times(2)).update(same(announcement), wrappers.capture());
        List<LambdaUpdateWrapper> values = wrappers.getAllValues();
        assertThat(values).extracting(LambdaUpdateWrapper::getSqlSet)
                .containsExactly("version = version + 1", "version = version + 1");
        values.forEach(LambdaUpdateWrapper::getSqlSegment);
        assertThat(values.get(0).getParamNameValuePairs().values()).contains(7L, 2);
        assertThat(values.get(1).getParamNameValuePairs().values()).contains(7L, 3);
    }

    @Test
    void overlapQueryKeepsNullBoundariesOutOfSqlAndLocksRows() {
        when(mapper.selectList(any())).thenReturn(List.of());

        repository.findOverlapByPriorityAndTimeForUpdate(7, null, null);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> wrappers =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectList(wrappers.capture());
        var query = wrappers.getValue();
        assertThat(query.getSqlSegment()).contains("ORDER BY").contains("FOR UPDATE");
        assertThat(query.getParamNameValuePairs().values())
                .contains(7, true)
                .doesNotContain(java.time.LocalDateTime.MIN, java.time.LocalDateTime.MAX);
    }

    @Test
    void overlapQueryUsesOnlyProvidedHalfOpenBoundaries() {
        when(mapper.selectList(any())).thenReturn(List.of());
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 7, 1, 0, 0);
        java.time.LocalDateTime end = java.time.LocalDateTime.of(2026, 8, 1, 0, 0);

        repository.findOverlapByPriorityAndTimeForUpdate(7, start, end);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper> wrappers =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectList(wrappers.capture());
        var query = wrappers.getValue();
        assertThat(query.getSqlSegment()).contains("FOR UPDATE");
        assertThat(query.getParamNameValuePairs().values())
                .contains(7, true, start, end)
                .doesNotContain(java.time.LocalDateTime.MIN, java.time.LocalDateTime.MAX);
    }
}
