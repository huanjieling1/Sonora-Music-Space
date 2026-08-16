package com.example.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseDocumentationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void everyApplicationTableAndColumnHasDocumentation() {
        Integer undocumentedTables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE() AND COALESCE(TABLE_COMMENT, '') = ''
                """, Integer.class);
        Integer undocumentedColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND COALESCE(COLUMN_COMMENT, '') = ''
                """, Integer.class);

        assertThat(undocumentedTables).isZero();
        assertThat(undocumentedColumns).isZero();
    }
}
