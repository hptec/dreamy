package com.dreamy.it;

import com.dreamy.domain.site_builder.consts.SiteBuilderDBConst;
import com.dreamy.domain.site_builder.service.HomePageSectionService;
import com.dreamy.dto.SiteBuilderDtos.HomePageSectionUpsert;
import com.dreamy.error.SiteBuilderErrorCode;
import com.dreamy.error.SiteBuilderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Hero singleton against real MySQL and Redis, without a database unique index. */
class HomePageSectionConcurrencyIT extends AbstractIT {

    @Autowired
    HomePageSectionService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearHomepageSections() {
        jdbcTemplate.update("DELETE FROM " + SiteBuilderDBConst.HOME_SECTIONS_TABLE);
    }

    @Test
    void concurrentHeroCreatesAreSerializedAndOnlyOneRowCommits() throws Exception {
        assertNoDatabaseHeroConstraint();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> createHero = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent Hero create did not start");
            }
            try {
                service.create(heroUpsert());
                return true;
            } catch (SiteBuilderException ex) {
                assertThat(ex.getErrorCode()).isEqualTo(SiteBuilderErrorCode.HOME_SECTION_DATA_JSON_INVALID);
                assertThat(ex.getDetails()).containsEntry(
                        "reason", "homepage can contain only one hero section");
                return false;
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    executor.submit(createHero),
                    executor.submit(createHero));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            boolean first = results.get(0).get(10, TimeUnit.SECONDS);
            boolean second = results.get(1).get(10, TimeUnit.SECONDS);
            assertThat(List.of(first, second)).containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Long heroCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + SiteBuilderDBConst.HOME_SECTIONS_TABLE
                        + " WHERE section_type = 'hero'", Long.class);
        assertThat(heroCount).isEqualTo(1L);
    }

    private void assertNoDatabaseHeroConstraint() {
        Long indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = 'uk_home_sections_hero_singleton'
                """, Long.class, SiteBuilderDBConst.HOME_SECTIONS_TABLE);
        Long columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = 'hero_singleton'
                """, Long.class, SiteBuilderDBConst.HOME_SECTIONS_TABLE);
        Long uniqueIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND non_unique = 0
                  AND index_name <> 'PRIMARY'
                """, Long.class, SiteBuilderDBConst.HOME_SECTIONS_TABLE);

        assertThat(indexCount).isZero();
        assertThat(columnCount).isZero();
        assertThat(uniqueIndexCount).isZero();
    }

    private static HomePageSectionUpsert heroUpsert() {
        HomePageSectionUpsert upsert = new HomePageSectionUpsert();
        upsert.setSectionType("hero");
        upsert.setEnabled(true);
        upsert.setSortOrder(0);
        return upsert;
    }
}
