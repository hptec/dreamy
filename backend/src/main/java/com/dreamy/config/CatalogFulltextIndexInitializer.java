package com.dreamy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * FULLTEXT ngram 索引初始化（IDX-CAT-004 ft_product_search / IDX-CAT-010 ft_pt_search）。
 * huihao-mysql @Index 注解不支持 FULLTEXT/WITH PARSER，故在 DdlAuto 建表完成后
 * （ApplicationReadyEvent 晚于 ApplicationRunner）以幂等 DDL 补建（决策 17 全文搜索必含）。
 */
@Component
@Order(10)
public class CatalogFulltextIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogFulltextIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public CatalogFulltextIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureFulltextIndexes() {
        ensureIndex("product", "ft_product_search",
                "ALTER TABLE product ADD FULLTEXT INDEX ft_product_search (name, subtitle) WITH PARSER ngram");
        ensureIndex("product_translation", "ft_pt_search",
                "ALTER TABLE product_translation ADD FULLTEXT INDEX ft_pt_search (name, subtitle) WITH PARSER ngram");
    }

    private void ensureIndex(String table, String indexName, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                    Integer.class, table, indexName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute(ddl);
            log.info("[CATALOG-DDL] FULLTEXT index {} created on {}", indexName, table);
        } catch (Exception ex) {
            // 建索引失败不阻断启动（搜索端点将回退失败 50000，运维按日志补建）
            log.error("[CATALOG-DDL] failed to ensure FULLTEXT index {} on {}", indexName, table, ex);
        }
    }
}
