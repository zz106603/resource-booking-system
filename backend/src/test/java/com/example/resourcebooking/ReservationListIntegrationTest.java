package com.example.resourcebooking;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationListIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    long spaceId;
    long otherSpaceId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from reservations");
        jdbc.update("delete from spaces");
        spaceId = space("첫 공간");
        otherSpaceId = space("다른 공간");
    }

    @Test
    void 필터를_생략하면_모든_공간의_과거_예약과_취소_이력을_시작시각_ID순으로_반환한다() {
        long later = insert(spaceId, "CONFIRMED", "2030-01-01T02:00:00Z", "2030-01-01T03:00:00Z");
        long tieFirst = insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        long tieSecond = insert(otherSpaceId, "CONFIRMED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        long past = insert(spaceId, "CONFIRMED", "2010-01-01T00:00:00Z", "2010-01-01T01:00:00Z");

        var body = success(get());
        assertThat(ids(body)).containsExactly(past, tieFirst, tieSecond, later);
        assertThat(body.path("page").asInt()).isZero();
        assertThat(body.path("size").asInt()).isEqualTo(20);
        assertThat(body.path("totalElements").asLong()).isEqualTo(4);
        assertThat(body.path("totalPages").asInt()).isEqualTo(1);
        var cancelled = body.path("content").get(1);
        assertThat(cancelled.path("spaceId").asLong()).isEqualTo(spaceId);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("bookerName").asText()).isEqualTo("조회 예약자");
        assertThat(cancelled.path("startAt").asText()).isEqualTo("2030-01-01T01:00:00Z");
        assertThat(cancelled.path("endAt").asText()).isEqualTo("2030-01-01T02:00:00Z");
        assertThat(cancelled.path("createdAt").asText()).isEqualTo("2000-01-01T00:00:00Z");
        assertThat(cancelled.path("cancelledAt").asText()).isEqualTo("2001-01-01T00:00:00Z");
        assertThat(body.path("content").get(0).path("cancelledAt").isNull()).isTrue();
    }

    @Test
    void 공간과_상태를_각각_지정하면_해당_조건의_예약과_건수만_반환한다() {
        long confirmed = insert(spaceId, "CONFIRMED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        long cancelled = insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        insert(otherSpaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");

        assertThat(ids(success(get("spaceId", "" + spaceId)))).containsExactly(confirmed, cancelled);
        var active = success(get("status", "CONFIRMED"));
        assertThat(ids(active)).containsExactly(confirmed);
        assertThat(active.path("totalElements").asLong()).isEqualTo(1);
        assertThat(ids(success(get("spaceId", "" + spaceId, "status", "CANCELLED")))).containsExactly(cancelled);
    }

    @Test
    void 조회구간과_부분겹침_포함_동일구간만_반환하고_양끝에_맞닿는_예약은_제외한다() {
        insert(spaceId, "CANCELLED", "2030-01-01T00:00:00Z", "2030-01-01T01:00:00Z");
        long left = insert(spaceId, "CANCELLED", "2030-01-01T00:30:00Z", "2030-01-01T01:30:00Z");
        long contains = insert(spaceId, "CANCELLED", "2030-01-01T00:45:00Z", "2030-01-01T02:15:00Z");
        long same = insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        long inside = insert(spaceId, "CANCELLED", "2030-01-01T01:15:00Z", "2030-01-01T01:45:00Z");
        long right = insert(spaceId, "CANCELLED", "2030-01-01T01:30:00Z", "2030-01-01T02:30:00Z");
        insert(spaceId, "CANCELLED", "2030-01-01T02:00:00Z", "2030-01-01T03:00:00Z");

        var result = success(get("from", "2030-01-01T01:00:00Z", "to", "2030-01-01T02:00:00Z"));
        assertThat(ids(result)).containsExactly(left, contains, same, inside, right);
        assertThat(result.path("totalElements").asLong()).isEqualTo(5);
    }

    @Test
    void 모든_필터와_페이지를_함께_적용하면_AND조건의_일부와_전체건수를_반환한다() {
        insert(spaceId, "CONFIRMED", "2030-01-01T00:00:00Z", "2030-01-01T01:00:00Z");
        insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        long second = insert(spaceId, "CANCELLED", "2030-01-01T02:00:00Z", "2030-01-01T03:00:00Z");
        insert(spaceId, "CANCELLED", "2030-01-01T04:00:00Z", "2030-01-01T05:00:00Z");
        insert(otherSpaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");

        var body = success(get("spaceId", "" + spaceId, "status", "CANCELLED",
                "from", "2030-01-01T01:00:00Z", "to", "2030-01-01T03:00:00Z", "page", "1", "size", "1"));
        assertThat(ids(body)).containsExactly(second);
        assertThat(body.path("page").asInt()).isEqualTo(1);
        assertThat(body.path("size").asInt()).isEqualTo(1);
        assertThat(body.path("totalElements").asLong()).isEqualTo(2);
        assertThat(body.path("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    void 기본페이지는_20개이며_다음페이지와_범위밖페이지도_정확한_건수를_반환한다() {
        var expected = new ArrayList<Long>();
        for (int i = 0; i < 21; i++) {
            expected.add(insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z"));
        }
        var first = success(get());
        assertThat(ids(first)).containsExactlyElementsOf(expected.subList(0, 20));
        assertThat(first.path("totalElements").asLong()).isEqualTo(21);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(ids(success(get("page", "1")))).containsExactly(expected.get(20));
        var beyond = success(get("page", "2"));
        assertThat(ids(beyond)).isEmpty();
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(21);
    }

    @Test
    void 데이터가_없거나_필터와_일치하지_않으면_200과_빈페이지를_반환한다() {
        var empty = success(get("spaceId", "" + spaceId));
        assertThat(ids(empty)).isEmpty();
        assertThat(empty.path("totalElements").asLong()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
        insert(spaceId, "CANCELLED", "2030-01-01T01:00:00Z", "2030-01-01T02:00:00Z");
        assertThat(ids(success(get("status", "CONFIRMED")))).isEmpty();
    }

    @Test
    void 없는_공간을_지정하면_빈목록_대신_404를_반환한다() {
        assertError(get("spaceId", "" + Long.MAX_VALUE), 404, "SPACE_NOT_FOUND");
    }

    @Test
    void 오프셋과_마이크로초를_비교하고_과거와_긴기간과_지원연도_경계를_허용한다() {
        long id = insert(spaceId, "CONFIRMED", "2010-01-01T01:00:00.123456Z", "2010-01-01T02:00:00Z");
        assertThat(ids(success(get("from", "2010-01-01T10:00:00.123456+09:00",
                "to", "2010-01-01T11:00:00+09:00")))).containsExactly(id);
        assertThat(ids(success(get("from", "0001-01-01T00:00:00Z", "to", "9999-12-31T23:59:59.999999Z"))))
                .containsExactly(id);
    }

    @Test
    void 최대페이지크기와_최대지원_offset은_빈결과여도_허용한다() {
        assertThat(success(get("size", "100")).path("size").asInt()).isEqualTo(100);
        assertThat(ids(success(get("page", "2147483647", "size", "1")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"spaceId=0", "spaceId=-1", "spaceId=1.5", "spaceId=abc", "spaceId=9223372036854775808",
            "spaceId=", "status=", "status=confirmed", "status=UNKNOWN", "status= CONFIRMED",
            "page=-1", "page=1.5", "page=2147483648", "page=", "page=abc",
            "size=0", "size=-1", "size=101", "size=2147483648", "size=1.5", "size=", "size=abc"})
    void 필터와_페이지의_빈값이나_잘못된_형식이나_범위는_400이다(String query) {
        String[] pair = query.split("=", -1);
        assertError(get(pair), 400, "INVALID_REQUEST");
    }

    @Test
    void 페이지_offset이_정수범위를_초과하면_400이다() {
        assertError(get("page", "2147483647", "size", "2"), 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"from=2030-01-01T01:00:00Z", "to=2030-01-01T02:00:00Z", "from=", "to="})
    void 조회시각을_한쪽만_지정하면_400이다(String query) {
        assertError(get(query.split("=", -1)), 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T01:00:00", "1893459600", "2030-01-01T01:00:00.0000001Z",
            "0000-12-31T23:59:59Z", "+10000-01-01T00:00:00Z", "abc", ""})
    void 조회시각의_형식이나_정밀도나_지원연도가_잘못되면_400이다(String time) {
        assertError(get("from", time, "to", "2030-01-01T02:00:00Z"), 400, "INVALID_REQUEST");
        assertError(get("from", "2030-01-01T00:00:00Z", "to", time), 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2030-01-01T02:00:00Z", "2030-01-01T03:00:00Z"})
    void 조회시작이_종료와_같거나_늦으면_400이다(String from) {
        assertError(get("from", from, "to", "2030-01-01T02:00:00Z"), 400, "INVALID_REQUEST");
    }

    private long space(String name) {
        return jdbc.queryForObject("insert into spaces(name, capacity) values (?, 1) returning id", Long.class, name);
    }

    private long insert(long space, String status, String start, String end) {
        return jdbc.queryForObject("""
                insert into reservations(space_id, booker_name, start_at, end_at, status, created_at, cancelled_at)
                values (?, '조회 예약자', ?, ?, ?, '2000-01-01T00:00:00Z',
                    case when ? = 'CANCELLED' then timestamptz '2001-01-01T00:00:00Z' else null end) returning id
                """, Long.class, space, OffsetDateTime.parse(start), OffsetDateTime.parse(end), status, status);
    }

    private ResponseEntity<JsonNode> get(String... params) {
        StringBuilder url = new StringBuilder(http.getRootUri()).append("/api/reservations");
        for (int i = 0; i < params.length; i += 2) {
            url.append(i == 0 ? '?' : '&').append(params[i]).append('=')
                    .append(URLEncoder.encode(params[i + 1], StandardCharsets.UTF_8));
        }
        return http.getForEntity(URI.create(url.toString()), JsonNode.class);
    }

    private JsonNode success(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().path("content").isArray()).isTrue();
        return response.getBody();
    }

    private List<Long> ids(JsonNode body) {
        var result = new ArrayList<Long>();
        body.path("content").forEach(row -> result.add(row.path("id").asLong()));
        return result;
    }

    private void assertError(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
        assertThat(response.getBody().path("fieldErrors").isArray()).isTrue();
    }
}
