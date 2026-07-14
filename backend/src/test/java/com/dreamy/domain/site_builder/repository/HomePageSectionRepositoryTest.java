package com.dreamy.domain.site_builder.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dreamy.domain.site_builder.entity.HomePageSection;
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
class HomePageSectionRepositoryTest {

    @Mock
    private HomePageSectionMapper mapper;

    private HomePageSectionRepository repository;

    @BeforeAll
    static void initMybatisPlusCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, HomePageSection.class);
    }

    @BeforeEach
    void setUp() {
        repository = new HomePageSectionRepository(mapper);
    }

    @Test
    void findAllOrderByIdReliesOnApplicationLockWithoutDatabaseRowLock() {
        when(mapper.selectList(any())).thenReturn(List.of());

        assertThat(repository.findAllOrderById()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<HomePageSection>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("ORDER BY")
                .doesNotContainIgnoringCase("FOR UPDATE");
    }

    @Test
    void updateByIdAndVersionIncrementsDatabaseAndEntityVersionOnConsecutiveSaves() {
        HomePageSection section = new HomePageSection();
        section.setId(9L);
        section.setVersion(4);
        when(mapper.update(same(section), any())).thenReturn(1, 1);

        assertThat(repository.updateByIdAndVersion(section)).isEqualTo(1);
        assertThat(section.getVersion()).isEqualTo(5);
        assertThat(repository.updateByIdAndVersion(section)).isEqualTo(1);
        assertThat(section.getVersion()).isEqualTo(6);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<LambdaUpdateWrapper> wrappers = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper, times(2)).update(same(section), wrappers.capture());
        List<LambdaUpdateWrapper> values = wrappers.getAllValues();
        assertThat(values).extracting(LambdaUpdateWrapper::getSqlSet)
                .containsExactly("version = version + 1", "version = version + 1");
        values.forEach(LambdaUpdateWrapper::getSqlSegment);
        assertThat(values.get(0).getParamNameValuePairs().values()).contains(9L, 4);
        assertThat(values.get(1).getParamNameValuePairs().values()).contains(9L, 5);
    }
}
