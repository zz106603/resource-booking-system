package com.example.resourcebooking;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaceApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanSpaces() {
        jdbc.update("delete from spaces");
    }

    @Test
    void 유효한_공간을_등록하면_저장되고_동일한_상세를_조회한다() {
        var response = create("회의실 A", "창문이 있는 회의실", 6);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = response.getBody();
        long id = body.path("id").asLong();
        assertThat(id).isPositive();
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/spaces/" + id);
        assertThat(body.path("name").asText()).isEqualTo("회의실 A");
        assertThat(body.path("description").asText()).isEqualTo("창문이 있는 회의실");
        assertThat(body.path("capacity").asInt()).isEqualTo(6);
        assertThat(java.time.Instant.parse(body.path("createdAt").asText()).getNano() % 1000).isZero();
        assertThat(jdbc.queryForObject("select name from spaces where id = ?", String.class, id))
                .isEqualTo("회의실 A");
        var detail = http.getForEntity("/api/spaces/" + id, JsonNode.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).isEqualTo(body);
    }

    @Test
    void 이름이_중복되고_설명이_없어도_공간이_등록된다() {
        var first = post("{\"name\":\"중복 이름\",\"capacity\":1}");
        var second = post("{\"name\":\"중복 이름\",\"description\":null,\"capacity\":1}");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().path("description").isNull()).isTrue();
        assertThat(first.getBody().path("id")).isNotEqualTo(second.getBody().path("id"));
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void 문자열_길이와_수용_인원이_허용_최댓값이면_등록된다() {
        assertThat(create("a".repeat(100), "b".repeat(2000), Integer.MAX_VALUE).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void 여러_공간이_있으면_ID_오름차순으로_페이지_조회된다() {
        long first = create("Z", "", 1).getBody().path("id").asLong();
        long second = create("A", "", 2).getBody().path("id").asLong();
        var response = http.getForEntity("/api/spaces?page=0&size=1", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = response.getBody();
        assertThat(page.path("content").size()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(first);
        assertThat(page.path("page").asInt()).isZero();
        assertThat(page.path("size").asInt()).isEqualTo(1);
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        var next = http.getForEntity("/api/spaces?page=1&size=1", JsonNode.class).getBody();
        assertThat(next.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(http.getForEntity("/api/spaces?page=2&size=1", JsonNode.class)
                .getBody().path("content").isEmpty()).isTrue();
    }

    @Test
    void 공간이_없으면_기본_크기의_빈_페이지를_반환한다() {
        var response = http.getForEntity("/api/spaces", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("content").isEmpty()).isTrue();
        assertThat(response.getBody().path("size").asInt()).isEqualTo(20);
        assertThat(response.getBody().path("totalElements").asLong()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"capacity\":1}", "{\"name\":null,\"capacity\":1}",
            "{\"name\":\"\",\"capacity\":1}", "{\"name\":\"   \",\"capacity\":1}",
            "{\"name\":\"A\"}", "{\"name\":\"A\",\"capacity\":null}",
            "{\"name\":\"A\",\"capacity\":0}", "{\"name\":\"A\",\"capacity\":-1}"
    })
    void 필수값이_없거나_수용_인원이_양수가_아니면_400을_반환하고_저장하지_않는다(String json) {
        var response = post(json);
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(response.getBody().path("fieldErrors").isEmpty()).isFalse();
        assertThat(count()).isZero();
    }

    @Test
    void 이름이나_설명이_최대_길이를_초과하면_400을_반환하고_저장하지_않는다() {
        assertError(create("a".repeat(101), "", 1), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertError(create("valid", "b".repeat(2001), 1), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "null", "[]", "{\"name\":\"A\",\"capacity\":1.5}",
            "{\"name\":\"A\",\"capacity\":2147483648}", "{\"name\":\"A\",\"capacity\":\"abc\"}",
            "{\"name\":123,\"capacity\":1}", "{\"name\":\"A\",\"description\":123,\"capacity\":1}",
            "{\"name\":\"A\",\"capacity\":\"1\"}"})
    void 요청_본문의_JSON_형식이나_타입이_잘못되면_400을_반환하고_저장하지_않는다(String json) {
        assertError(post(json), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        assertThat(count()).isZero();
    }

    @Test
    void 존재하지_않는_공간을_조회하면_404를_반환한다() {
        assertError(http.getForEntity("/api/spaces/9223372036854775807", JsonNode.class),
                HttpStatus.NOT_FOUND, "SPACE_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void 공간_ID의_형식이나_값이_잘못되면_400을_반환한다(String id) {
        var response = http.getForEntity("/api/spaces/" + id, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=1.5", "page=2147483648"})
    void 페이지_파라미터의_형식이나_범위가_잘못되면_400을_반환한다(String query) {
        var response = http.getForEntity("/api/spaces?" + query, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("code").asText()).isNotBlank();
    }

    @Test
    void DB에_필수값이나_양수_제약을_위반한_공간을_넣으면_저장되지_않는다() {
        assertThatThrownBy(() -> jdbc.update("insert into spaces(name, capacity) values ('A', 0)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into spaces(name, capacity) values (null, 1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into spaces(name, capacity) values ('   ', 1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count()).isZero();
    }

    @Test
    void DB_장애로_조회가_실패하면_내부_정보_없이_500을_반환한다() {
        jdbc.execute("alter table spaces rename to spaces_temporarily_unavailable");
        try {
            var response = http.getForEntity("/api/spaces", JsonNode.class);
            assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
            assertThat(response.getBody().toString()).doesNotContain("SQLException", "select", "stackTrace", "spaces");
        } finally {
            jdbc.execute("alter table spaces_temporarily_unavailable rename to spaces");
        }
    }

    private ResponseEntity<JsonNode> create(String name, String description, int capacity) {
        return http.postForEntity("/api/spaces",
                Map.of("name", name, "description", description, "capacity", capacity), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String json) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/spaces", HttpMethod.POST, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from spaces", Integer.class);
    }

    private void assertError(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("message").asText()).isNotBlank();
        assertThat(response.getBody().has("fieldErrors")).isTrue();
    }
}
