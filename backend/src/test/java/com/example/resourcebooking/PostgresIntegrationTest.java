package com.example.resourcebooking;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @LocalServerPort
    int port;

    @Test
    void 애플리케이션을_시작하면_실제_PostgreSQL에_연결되고_최신_마이그레이션이_적용된다() throws Exception {
        assertThat(port).isPositive();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void 기존_V1_스키마를_V2로_업그레이드하면_공간_테이블에_저장할_수_있다() {
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas("migration_upgrade").target("1").load().migrate();
            assertThat(jdbc.queryForObject(
                    "select to_regclass('migration_upgrade.spaces')::text", String.class)).isNull();
            var upgrade = Flyway.configure().dataSource(dataSource).schemas("migration_upgrade").load();
            upgrade.migrate();
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("2");
            jdbc.update("insert into migration_upgrade.spaces(name, capacity) values ('upgrade test', 1)");
            assertThat(jdbc.queryForObject(
                    "select count(*) from migration_upgrade.spaces", Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists migration_upgrade cascade");
        }
    }
}
