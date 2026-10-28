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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void 기존_V1_스키마를_V2로_업그레이드하면_공간_테이블에_저장할_수_있다() {
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas("migration_upgrade").target("1").load().migrate();
            assertThat(jdbc.queryForObject(
                    "select to_regclass('migration_upgrade.spaces')::text", String.class)).isNull();
            var upgrade = Flyway.configure().dataSource(dataSource).schemas("migration_upgrade").target("2").load();
            upgrade.migrate();
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("2");
            jdbc.update("insert into migration_upgrade.spaces(name, capacity) values ('upgrade test', 1)");
            assertThat(jdbc.queryForObject(
                    "select count(*) from migration_upgrade.spaces", Integer.class)).isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists migration_upgrade cascade");
        }
    }

    @Test
    void V2의_공간_데이터를_유지하면서_V3에서_예약을_저장할_수_있다() {
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas("reservation_upgrade").target("2").load().migrate();
            jdbc.update("insert into reservation_upgrade.spaces(name, capacity) values ('기존 공간', 3)");
            var upgrade = Flyway.configure().dataSource(dataSource).schemas("reservation_upgrade").target("3").load();
            upgrade.migrate();
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("3");
            assertThat(jdbc.queryForObject("select name from reservation_upgrade.spaces", String.class))
                    .isEqualTo("기존 공간");
            jdbc.update("insert into reservation_upgrade.reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                    + "select id,'예약자','2030-01-01T01:00:00Z'::timestamptz,'2030-01-01T02:00:00Z'::timestamptz,"
                    + "'CONFIRMED',now() from reservation_upgrade.spaces");
            assertThat(jdbc.queryForObject("select count(*) from reservation_upgrade.reservations", Integer.class))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("drop schema if exists reservation_upgrade cascade");
        }
    }

    @Test
    void V3의_확정_예약과_취소_이력을_보존하면서_V4가_배타_제약을_추가한다() {
        var jdbc = new JdbcTemplate(dataSource);
        String schema = "exclusion_upgrade";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).target("3").load().migrate();
            jdbc.update("insert into exclusion_upgrade.spaces(id,name,capacity) values (1,'기존 공간',3)");
            jdbc.update("insert into exclusion_upgrade.reservations(space_id,booker_name,start_at,end_at,status,created_at,cancelled_at) "
                    + "values (1,'확정','2030-01-01','2030-01-02','CONFIRMED',now(),null),"
                    + "(1,'취소','2030-01-01','2030-01-02','CANCELLED',now(),now())");
            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).load();
            upgrade.migrate();
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("4");
            assertThat(jdbc.queryForObject("select count(*) from exclusion_upgrade.reservations", Integer.class)).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("insert into exclusion_upgrade.reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                    + "values (1,'중복','2030-01-01','2030-01-02','CONFIRMED',now())"))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("drop schema if exists exclusion_upgrade cascade");
        }
    }

    @Test
    void V3에_활성_중복이_있으면_V4는_실패하고_기존_데이터를_고치지_않는다() {
        var jdbc = new JdbcTemplate(dataSource);
        String schema = "exclusion_invalid_upgrade";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).target("3").load().migrate();
            jdbc.update("insert into exclusion_invalid_upgrade.spaces(id,name,capacity) values (1,'기존 공간',3)");
            jdbc.update("insert into exclusion_invalid_upgrade.reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                    + "values (1,'A','2030-01-01','2030-01-02','CONFIRMED',now()),"
                    + "(1,'B','2030-01-01','2030-01-02','CONFIRMED',now())");
            var upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).load();
            assertThatThrownBy(upgrade::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class)
                    .hasStackTraceContaining("Existing confirmed reservations overlap");
            assertThat(jdbc.queryForObject("select count(*) from exclusion_invalid_upgrade.reservations where status='CONFIRMED'",
                    Integer.class)).isEqualTo(2);
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("3");
        } finally {
            jdbc.execute("drop schema if exists exclusion_invalid_upgrade cascade");
        }
    }
}
