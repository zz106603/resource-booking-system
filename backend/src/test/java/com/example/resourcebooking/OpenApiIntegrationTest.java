package com.example.resourcebooking;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.11-alpine");

    @Autowired TestRestTemplate http;

    @Test
    void 스웨거_UI와_설정이_제공된다() {
        var page = http.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("Swagger UI");
        var config = http.getForObject("/v3/api-docs/swagger-config", JsonNode.class);
        assertThat(config.path("url").asText()).isEqualTo("/v3/api-docs");
    }

    @Test
    void 문서에_여섯_API와_실제_예약_필터_타입이_표시된다() {
        var paths = docs().path("paths");
        assertThat(paths.size()).isEqualTo(4);
        assertThat(paths.path("/api/spaces").has("post")).isTrue();
        assertThat(paths.path("/api/spaces").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/spaces").path("post").path("responses").has("200")).isFalse();
        assertThat(paths.path("/api/spaces").has("get")).isTrue();
        assertThat(paths.path("/api/spaces/{id}").has("get")).isTrue();
        assertThat(paths.path("/api/reservations").has("post")).isTrue();
        assertThat(paths.path("/api/reservations/{id}/cancel").has("post")).isTrue();
        var parameters = paths.path("/api/reservations").path("get").path("parameters");
        assertThat(parameters.size()).isEqualTo(6);
        for (var parameter : parameters) {
            if (parameter.path("name").asText().equals("spaceId")) {
                assertThat(parameter.path("schema").path("type").asText()).isEqualTo("integer");
            }
        }
        assertThat(paths.path("/api/reservations").path("post").path("responses").has("409")).isTrue();
    }

    @Test
    void 문서의_예시로_공간_예약_조회_취소를_실행할_수_있다() {
        var doc = docs();
        var space = http.postForEntity("/api/spaces", example(doc, "/api/spaces"), JsonNode.class);
        assertThat(space.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var booking = example(doc, "/api/reservations").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) booking).put("spaceId", space.getBody().path("id").asLong());
        var reservation = http.postForEntity("/api/reservations", booking, JsonNode.class);
        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = reservation.getBody().path("id").asLong();
        var listed = http.getForObject("/api/reservations?spaceId=" + space.getBody().path("id").asLong(), JsonNode.class);
        assertThat(listed.path("totalElements").asInt()).isEqualTo(1);
        assertThat(http.postForEntity("/api/reservations/{id}/cancel", null, JsonNode.class, Map.of("id", id))
                .getBody().path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void 문서를_재조회하면_미래_예약_시각이_다시_생성된다() {
        var first = example(docs(), "/api/reservations");
        var second = example(docs(), "/api/reservations");
        Instant start = Instant.parse(second.path("startAt").asText());
        assertThat(start).isAfter(Instant.now());
        assertThat(Instant.parse(second.path("endAt").asText())).isAfter(start);
        assertThat(second.path("startAt")).isNotEqualTo(first.path("startAt"));
    }

    private JsonNode docs() {
        var response = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private JsonNode example(JsonNode doc, String path) {
        return doc.path("paths").path(path).path("post").path("requestBody")
                .path("content").path("application/json").path("example");
    }
}
