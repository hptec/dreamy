package com.dreamy.it;

import com.dreamy.domain.site_builder.entity.Announcement;
import com.dreamy.domain.site_builder.repository.AnnouncementMapper;
import com.dreamy.domain.site_builder.service.AnnouncementService;
import com.dreamy.dto.SiteBuilderDtos.AnnouncementUpsert;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnouncementWindowIT extends AbstractIT {

    @Autowired AnnouncementService announcementService;
    @Autowired AnnouncementMapper announcementMapper;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void cleanAnnouncements() {
        announcementMapper.delete(null);
    }

    @Test
    void openEndedWindowPersistsAndConflictsWithoutOutOfRangeMysqlDates() throws Exception {
        var created = announcementService.create(request(17, null, null, "Always on"));

        Announcement stored = announcementMapper.selectById(created.getId());
        assertThat(stored.getStartAt()).isNull();
        assertThat(stored.getEndAt()).isNull();

        assertThatThrownBy(() -> announcementService.create(request(17, null, null, "Conflict")))
                .isInstanceOf(SiteBuilderException.class)
                .satisfies(ex -> assertThat(((SiteBuilderException) ex).getErrorCode())
                        .isEqualTo(SiteBuilderErrorCode.ANNOUNCEMENT_TIME_WINDOW_CONFLICT));
    }

    private AnnouncementUpsert request(int priority, java.time.LocalDateTime start,
                                       java.time.LocalDateTime end, String content) throws Exception {
        AnnouncementUpsert upsert = new AnnouncementUpsert();
        upsert.setEnabled(true);
        upsert.setPriority(priority);
        upsert.setStartAt(start);
        upsert.setEndAt(end);
        upsert.setContentI18nJson(objectMapper.readTree("{\"en\":{\"content\":\"" + content + "\"}}"));
        return upsert;
    }
}
