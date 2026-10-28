package com.example.resourcebooking;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationApiIntegrationTest {

    static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    static final String START = "2030-01-01T01:00:00Z";
    static final String END = "2030-01-01T02:00:00Z";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoBean Clock clock;
    long spaceId;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        jdbc.update("delete from reservations");
        jdbc.update("delete from spaces");
        spaceId = jdbc.queryForObject("insert into spaces(name, capacity) values ('예약 공간', 10) returning id", Long.class);
    }

    @Test
    void 유효한_미래_예약을_생성하면_201과_확정_상태를_반환하고_DB에_저장된다() {
        var response = create(spaceId, START, END);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        var body = response.getBody();
        assertThat(body.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(body.path("spaceId").asLong()).isEqualTo(spaceId);
        assertThat(body.path("bookerName").asText()).isEqualTo("예약자");
        assertThat(body.path("startAt").asText()).isEqualTo(START);
        assertThat(body.path("cancelledAt").isNull()).isTrue();
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T01:30:00Z/2030-01-01T02:30:00Z",
            "2030-01-01T00:30:00Z/2030-01-01T01:30:00Z",
            "2030-01-01T00:30:00Z/2030-01-01T03:00:00Z",
            "2030-01-01T01:15:00Z/2030-01-01T01:45:00Z",
            "2030-01-01T01:00:00Z/2030-01-01T02:00:00Z"})
    void 같은_공간의_확정_예약과_겹치면_409이고_새_예약은_저장되지_않는다(String range) {
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
        String[] times = range.split("/");
        assertError(create(spaceId, times[0], times[1]), 409, "RESERVATION_CONFLICT");
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @Test
    void 예약_양끝이_맞닿거나_다른_공간이면_같은_시간에도_예약된다() {
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
        assertThat(create(spaceId, END, "2030-01-01T03:00:00Z").getStatusCode().value()).isEqualTo(201);
        assertThat(create(spaceId, "2030-01-01T00:30:00Z", START).getStatusCode().value()).isEqualTo(201);
        long other = jdbc.queryForObject("insert into spaces(name, capacity) values ('다른 공간', 1) returning id", Long.class);
        assertThat(create(other, START, END).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void 서로_다른_오프셋이_동일한_순간을_나타내면_중복으로_거부된다() {
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
        assertError(create(spaceId, "2030-01-01T10:00:00+09:00", "2030-01-01T11:00:00+09:00"),
                409, "RESERVATION_CONFLICT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T02:00:00Z/2030-01-01T01:00:00Z",
            "2030-01-01T01:00:00Z/2030-01-01T01:00:00Z",
            "2030-01-01T00:00:00Z/2030-01-01T01:00:00Z",
            "2029-12-31T23:00:00Z/2030-01-01T01:00:00Z",
            "2030-01-01T01:00:00.0000001Z/2030-01-01T02:00:00Z"})
    void 잘못된_시간_구간이나_과거_시작이나_과도한_정밀도는_400이고_저장하지_않는다(String range) {
        String[] times = range.split("/");
        assertError(create(spaceId, times[0], times[1]), 400, "INVALID_RESERVATION");
        assertThat(count("CONFIRMED")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{", "{\"spaceId\":1,\"bookerName\":\" \"}",
            "{\"spaceId\":0,\"bookerName\":\"A\",\"startAt\":\"2030-01-01T01:00:00Z\",\"endAt\":\"2030-01-01T02:00:00Z\"}",
            "{\"spaceId\":1,\"bookerName\":\"A\",\"startAt\":\"2030-01-01T01:00:00\",\"endAt\":\"2030-01-01T02:00:00Z\"}"})
    void 필수값이나_시각_형식이_잘못되면_400이고_저장하지_않는다(String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        assertThat(http.postForEntity("/api/reservations", new HttpEntity<>(json, headers), JsonNode.class)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(count("CONFIRMED")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1893459600", "1893459600.5"})
    void 시각을_ISO_문자열_대신_숫자로_보내면_400이고_저장하지_않는다(String timestamp) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"spaceId\":" + spaceId + ",\"bookerName\":\"A\",\"startAt\":" + timestamp
                + ",\"endAt\":\"2030-01-01T02:00:00Z\"}";
        assertThat(http.postForEntity("/api/reservations", new HttpEntity<>(json, headers), JsonNode.class)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(count("CONFIRMED")).isZero();
    }

    @Test
    void 마이크로초_시각과_최대_길이_예약자명은_보존되고_길이를_초과하면_400이다() {
        var response = http.postForEntity("/api/reservations", Map.of("spaceId", spaceId,
                "bookerName", "a".repeat(100), "startAt", "2030-01-01T01:00:00.123456Z",
                "endAt", "2030-01-01T02:00:00.123456Z"), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().path("startAt").asText()).isEqualTo("2030-01-01T01:00:00.123456Z");
        assertThat(http.postForEntity("/api/reservations", Map.of("spaceId", spaceId,
                "bookerName", "a".repeat(101), "startAt", START, "endAt", END), JsonNode.class)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @Test
    void 없는_공간이나_예약은_404를_반환한다() {
        assertError(create(Long.MAX_VALUE, START, END), 404, "SPACE_NOT_FOUND");
        assertError(cancel(Long.MAX_VALUE), 404, "RESERVATION_NOT_FOUND");
    }

    @Test
    void 시작_전_예약을_취소하면_이력을_남기고_같은_구간을_재예약할_수_있다() {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        var response = cancel(id);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("status").asText()).isEqualTo("CANCELLED");
        assertThat(response.getBody().path("cancelledAt").asText()).isEqualTo(NOW.toString());
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
        assertThat(count("CANCELLED")).isEqualTo(1);
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T00:30:00Z", START, "2030-01-01T03:00:00Z"})
    void 이미_취소한_예약은_시작_경과와_관계없이_200이고_최초_취소_시각을_유지한다(String now) {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        var first = cancel(id).getBody();
        when(clock.instant()).thenReturn(Instant.parse(now));
        var again = cancel(id);
        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(again.getBody()).isEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {START, "2030-01-01T03:00:00Z"})
    void 확정_예약의_시작에_도달했거나_지났으면_취소는_409이고_상태를_유지한다(String now) {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        when(clock.instant()).thenReturn(Instant.parse(now));
        assertError(cancel(id), 409, "RESERVATION_ALREADY_STARTED");
        assertThat(count("CONFIRMED")).isEqualTo(1);
        assertThat(count("CANCELLED")).isZero();
    }

    @RepeatedTest(3)
    void 같은_공간의_동일_구간을_동시에_예약하면_정확히_하나만_성공한다() throws Exception {
        List<Integer> statuses = race(8, () -> create(spaceId, START, END).getStatusCode().value());
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(code -> code == 201).count()).isEqualTo(1);
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @Test
    void 서로_다른_공간의_동일_구간_동시_예약은_모두_성공한다() throws Exception {
        long other = jdbc.queryForObject("insert into spaces(name, capacity) values ('다른 공간', 1) returning id", Long.class);
        var statuses = race(2, new java.util.concurrent.Callable<Integer>() {
            final java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
            public Integer call() {
                return create(index.getAndIncrement() == 0 ? spaceId : other, START, END).getStatusCode().value();
            }
        });
        assertThat(statuses).containsOnly(201);
        assertThat(count("CONFIRMED")).isEqualTo(2);
    }

    @Test
    void 같은_예약을_동시에_취소하면_모두_200이고_취소_이력은_하나다() throws Exception {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        assertThat(race(8, () -> cancel(id).getStatusCode().value())).containsOnly(200);
        assertThat(count("CANCELLED")).isEqualTo(1);
    }

    @Test
    void 같은_공간의_서로_겹치지_않는_동시_예약은_모두_성공한다() throws Exception {
        var index = new java.util.concurrent.atomic.AtomicInteger();
        assertThat(race(2, () -> index.getAndIncrement() == 0
                ? create(spaceId, START, END).getStatusCode().value()
                : create(spaceId, END, "2030-01-01T03:00:00Z").getStatusCode().value())).containsOnly(201);
        assertThat(count("CONFIRMED")).isEqualTo(2);
    }

    @RepeatedTest(3)
    void 취소와_동일_구간_생성이_경쟁해도_확정_예약은_하나를_넘지_않는다() throws Exception {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        var index = new java.util.concurrent.atomic.AtomicInteger();
        var statuses = race(2, () -> index.getAndIncrement() == 0
                ? cancel(id).getStatusCode().value() : create(spaceId, START, END).getStatusCode().value());
        assertThat(statuses).contains(200).allSatisfy(code -> assertThat(code).isIn(200, 201, 409));
        assertThat(count("CANCELLED")).isEqualTo(1);
        assertThat(count("CONFIRMED")).isEqualTo(statuses.contains(201) ? 1 : 0);
    }

    @Test
    void 공간_잠금이_시간을_초과하면_503이고_잠금_해제_후에는_예약된다() throws Exception {
        try (Connection connection = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from spaces where id = ? for update")) {
                statement.setLong(1, spaceId);
                statement.executeQuery().close();
            }
            var pending = workers.submit(() -> create(spaceId, START, END));
            try {
                assertError(pending.get(25, TimeUnit.SECONDS), 503, "TEMPORARY_DATABASE_FAILURE");
            } finally {
                connection.rollback();
            }
            assertThat(count("CONFIRMED")).isZero();
        }
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void 공간_잠금_대기_중_시작_시각이_지나면_생성은_400이고_저장되지_않는다() throws Exception {
        try (Connection connection = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from spaces where id = ? for update")) {
                statement.setLong(1, spaceId);
                statement.executeQuery().close();
            }
            Future<ResponseEntity<JsonNode>> pending = workers.submit(() -> create(spaceId, START, END));
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
                        Integer.class) > 0);
                when(clock.instant()).thenReturn(Instant.parse(START));
            } finally {
                connection.rollback();
            }
            assertError(pending.get(15, TimeUnit.SECONDS), 400, "INVALID_RESERVATION");
            assertThat(count("CONFIRMED")).isZero();
        }
    }

    @Test
    void DB에_잘못된_구간이나_공간을_저장하면_제약으로_거부된다() {
        assertThatThrownBy(() -> jdbc.update("insert into reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                + "values (?,'A',?::timestamptz,?::timestamptz,'CONFIRMED',now())", spaceId, END, START))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                + "values (?,'A',?::timestamptz,?::timestamptz,'CONFIRMED',now())", Long.MAX_VALUE, START, END))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void 직접_SQL도_확정_예약의_겹침은_23P01로_거부하고_취소_예약은_제외한다() {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        String insert = "insert into reservations(space_id,booker_name,start_at,end_at,status,created_at,cancelled_at) "
                + "values (?,'직접 SQL',?::timestamptz,?::timestamptz,?,now(),?::timestamptz)";
        assertThatThrownBy(() -> jdbc.update(insert, spaceId, START, END, "CONFIRMED", null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(error -> assertThat(((java.sql.SQLException)
                        ((org.springframework.dao.DataIntegrityViolationException) error).getMostSpecificCause())
                        .getSQLState()).isEqualTo("23P01"));
        assertThat(count("CONFIRMED")).isEqualTo(1);
        assertThat(cancel(id).getStatusCode().value()).isEqualTo(200);
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
        jdbc.update(insert, spaceId, START, END, "CANCELLED", NOW.toString());
        assertThat(count("CANCELLED")).isEqualTo(2);
        assertThat(count("CONFIRMED")).isEqualTo(1);
    }

    @Test
    void 공간의_키를_바꾸지_않는_잠금이_있어도_예약_생성과_취소가_완료된다() throws Exception {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        try (Connection connection = dataSource.getConnection(); var workers = Executors.newFixedThreadPool(2)) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("select id from spaces where id = ? for no key update")) {
                statement.setLong(1, spaceId);
                statement.executeQuery().close();
            }
            var creation = workers.submit(() -> create(spaceId, END, "2030-01-01T03:00:00Z"));
            var cancellation = workers.submit(() -> cancel(id));
            try {
                assertThat(creation.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(201);
                assertThat(cancellation.get(5, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(200);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void 실제_교착으로_첫_시도가_롤백되면_새_트랜잭션에서_재시도하여_한_건만_저장한다() throws Exception {
        String priorLogs = postgres.getLogs();
        installRetryProbe("INSERT", false);
        try (Connection blocker = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("set local deadlock_timeout='10s'");
                statement.execute("select pg_advisory_xact_lock(90102)");
                var pending = workers.submit(() -> create(spaceId, START, END));
                try {
                    await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                            "select count(*) from pg_stat_activity where application_name='reservation_deadlock_probe' "
                                    + "and wait_event_type='Lock'", Integer.class) == 1);
                    statement.setQueryTimeout(5);
                    // API holds 90101 and waits for our 90102; complete the cycle.
                    statement.execute("select pg_advisory_xact_lock(90101)");
                    blocker.commit();
                    assertThat(pending.get(15, TimeUnit.SECONDS).getStatusCode().value()).isEqualTo(201);
                } finally {
                    blocker.rollback();
                }
            }
            assertThat(probeValue("attempts")).isEqualTo(2);
            assertThat(probeValue("first_xid")).isNotEqualTo(probeValue("last_xid"));
            assertThat(count("CONFIRMED")).isEqualTo(1);
            String newLogs = postgres.getLogs().substring(priorLogs.length());
            assertThat(newLogs).contains("deadlock detected", "advisory lock");
            System.out.printf("Actual deadlock recovered: transaction IDs %d -> %d%n%s%n",
                    probeValue("first_xid"), probeValue("last_xid"), newLogs);
        } finally {
            removeRetryProbe();
        }
    }

    @Test
    void DB의_40P01이_계속되면_새_트랜잭션_3회에서_503으로_종료하고_삽입은_롤백된다() {
        installRetryProbe("INSERT", true);
        try {
            assertError(create(spaceId, START, END), 503, "TEMPORARY_DATABASE_FAILURE");
            assertThat(probeValue("attempts")).isEqualTo(3);
            assertThat(probeValue("first_xid")).isNotEqualTo(probeValue("last_xid"));
            assertThat(count("CONFIRMED")).isZero();
        } finally {
            removeRetryProbe();
        }
        assertThat(create(spaceId, START, END).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void 취소가_40P01을_반복하면_503이고_확정_상태와_취소시각은_롤백된다() {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        installRetryProbe("UPDATE", true);
        try {
            assertError(cancel(id), 503, "TEMPORARY_DATABASE_FAILURE");
            assertThat(probeValue("attempts")).isEqualTo(3);
            assertThat(probeValue("first_xid")).isNotEqualTo(probeValue("last_xid"));
            assertThat(count("CONFIRMED")).isEqualTo(1);
            assertThat(jdbc.queryForObject("select cancelled_at from reservations where id=?", Object.class, id)).isNull();
        } finally {
            removeRetryProbe();
        }
        assertThat(cancel(id).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 다른_무결성_위반은_예약_중복_409로_오인하지_않는다() {
        create(spaceId, START, END);
        jdbc.execute("alter table reservations add constraint test_booker_unique unique(booker_name)");
        try {
            assertError(create(spaceId, END, "2030-01-01T03:00:00Z"), 500, "INTERNAL_ERROR");
            assertThat(count("CONFIRMED")).isEqualTo(1);
        } finally {
            jdbc.execute("alter table reservations drop constraint test_booker_unique");
        }
    }

    @Test
    void 배타_검사_대기_후_시작에_도달했으면_새_예약을_롤백한다() throws Exception {
        try (Connection connection = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("insert into reservations(space_id,booker_name,start_at,end_at,status,created_at) "
                    + "values (?,'미커밋 예약',?::timestamptz,?::timestamptz,'CONFIRMED',now())")) {
                statement.setLong(1, spaceId);
                statement.setString(2, START);
                statement.setString(3, END);
                statement.executeUpdate();
            }
            var pending = workers.submit(() -> create(spaceId, START, END));
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
                        Integer.class) > 0);
                when(clock.instant()).thenReturn(Instant.parse(START));
            } finally {
                connection.rollback();
            }
            assertError(pending.get(15, TimeUnit.SECONDS), 400, "INVALID_RESERVATION");
            assertThat(count("CONFIRMED")).isZero();
        }
    }

    @Test
    void 취소_행_잠금_대기_후_이미_취소된_상태이면_최초_시각을_유지한다() throws Exception {
        long id = create(spaceId, START, END).getBody().path("id").asLong();
        try (Connection connection = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "update reservations set status='CANCELLED',cancelled_at=?::timestamptz where id=?")) {
                statement.setString(1, NOW.toString());
                statement.setLong(2, id);
                statement.executeUpdate();
            }
            var pending = workers.submit(() -> cancel(id));
            try {
                await().atMost(10, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                        "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'",
                        Integer.class) > 0);
                when(clock.instant()).thenReturn(Instant.parse(END));
                connection.commit();
            } finally {
                connection.rollback();
            }
            var response = pending.get(15, TimeUnit.SECONDS);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().path("cancelledAt").asText()).isEqualTo(NOW.toString());
        }
    }

    private void installRetryProbe(String operation, boolean alwaysFail) {
        jdbc.execute("""
                CREATE SEQUENCE retry_probe_attempts;
                CREATE SEQUENCE retry_probe_first_xid;
                CREATE SEQUENCE retry_probe_last_xid;
                CREATE FUNCTION retry_probe() RETURNS trigger LANGUAGE plpgsql AS $$
                DECLARE attempt bigint;
                BEGIN
                    attempt := nextval('retry_probe_attempts');
                    IF attempt=1 THEN PERFORM setval('retry_probe_first_xid',txid_current()); END IF;
                    PERFORM setval('retry_probe_last_xid',txid_current());
                    IF TG_ARGV[0]='always' THEN
                        RAISE EXCEPTION 'injected 40P01 for bounded retry verification' USING ERRCODE='40P01';
                    END IF;
                    IF attempt=1 THEN
                        PERFORM set_config('application_name','reservation_deadlock_probe',true);
                        PERFORM set_config('deadlock_timeout','500ms',true);
                        PERFORM pg_advisory_xact_lock(90101);
                        PERFORM pg_advisory_xact_lock(90102);
                    END IF;
                    RETURN NEW;
                END $$;
                """);
        jdbc.execute("CREATE TRIGGER retry_probe_trigger AFTER " + operation
                + " ON reservations FOR EACH ROW EXECUTE FUNCTION retry_probe('" + (alwaysFail ? "always" : "once") + "')");
    }

    private long probeValue(String sequence) {
        return jdbc.queryForObject("select last_value from retry_probe_" + sequence, Long.class);
    }

    private void removeRetryProbe() {
        jdbc.execute("DROP TRIGGER IF EXISTS retry_probe_trigger ON reservations; DROP FUNCTION IF EXISTS retry_probe(); "
                + "DROP SEQUENCE IF EXISTS retry_probe_attempts,retry_probe_first_xid,retry_probe_last_xid");
    }

    private List<Integer> race(int threads, java.util.concurrent.Callable<Integer> action) throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(threads)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < threads; i++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 출발 시간 초과");
                    return action.call();
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("작업 준비 시간 초과");
            start.countDown();
            var result = new ArrayList<Integer>();
            for (var future : futures) result.add(future.get(30, TimeUnit.SECONDS));
            return result;
        } finally {
            start.countDown();
        }
    }

    private ResponseEntity<JsonNode> create(long id, String start, String end) {
        return http.postForEntity("/api/reservations", Map.of("spaceId", id, "bookerName", "예약자",
                "startAt", start, "endAt", end), JsonNode.class);
    }

    private ResponseEntity<JsonNode> cancel(long id) {
        return http.postForEntity("/api/reservations/" + id + "/cancel", null, JsonNode.class);
    }

    private int count(String status) {
        return jdbc.queryForObject("select count(*) from reservations where status = ?", Integer.class, status);
    }

    private void assertError(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
    }
}
