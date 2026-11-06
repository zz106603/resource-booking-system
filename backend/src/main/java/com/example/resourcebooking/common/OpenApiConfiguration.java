package com.example.resourcebooking.common;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI bookingOpenApi() {
        return new OpenAPI().info(new Info().title("공유 공간 예약 API").version("v1")
                .description("공간 등록 → 반환된 공간 ID로 예약 생성 → 목록 조회 → 반환된 예약 ID로 취소. "
                        + "예시 ID는 실제 데이터가 아닙니다. Try it out 실행은 연결된 DB를 변경합니다. "
                        + "예약 예시 날짜는 문서를 새로 조회할 때 갱신됩니다."));
    }

    @Bean
    OpenApiCustomizer bookingExamples(Clock clock) {
        return api -> {
            ModelConverters.getInstance().read(ApiError.class)
                    .forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
            var paths = api.getPaths();
            var spaces = paths.get("/api/spaces");
            var detail = paths.get("/api/spaces/{id}");
            var reservations = paths.get("/api/reservations");
            var cancel = paths.get("/api/reservations/{id}/cancel");
            // ResponseEntity.created() is not inferred from the method's return type.
            var created = spaces.getPost().getResponses().remove("200");
            if (created != null) {
                spaces.getPost().getResponses().addApiResponse("201", created.description("공간 등록 성공"));
            }
            describe(spaces.getPost(), "공간", "공간 등록", "이름 100자·설명 2000자 이내, 수용 인원은 양의 정수입니다.", "400", "500");
            describe(spaces.getGet(), "공간", "공간 목록 조회", "ID 오름차순. page × size는 2147483647 이하입니다.", "400", "500");
            describe(detail.getGet(), "공간", "공간 상세 조회", "등록 응답에서 받은 실제 공간 ID를 입력하세요.", "400", "404", "500");
            describe(reservations.getPost(), "예약", "예약 생성", "실제 공간 ID가 필요합니다. 미래의 [시작, 종료) 구간이며 "
                    + "오프셋 포함 ISO 시각·마이크로초까지 지원합니다. 같은 구간 재실행은 409입니다.", "400", "404", "409", "503", "500");
            describe(reservations.getGet(), "예약", "예약 목록 조회", "선택 필터는 AND 결합합니다. from/to는 함께 지정하며 "
                    + "겹치는 구간을 조회합니다. startAt·id 오름차순. 없는 공간은 404입니다. page × size는 2147483647 이하입니다.", "400", "404", "500");
            describe(cancel.getPost(), "예약", "예약 취소", "실제 예약 ID를 입력하세요. 시작 전 확정 예약만 취소할 수 있습니다. "
                    + "이미 취소한 예약은 시작 시각과 관계없이 200입니다.", "400", "404", "409", "503", "500");
            spaces.getPost().getRequestBody().getContent().get("application/json").setExample(
                    Map.of("name", "회의실 A", "description", "창문이 있는 회의실", "capacity", 6));
            var start = clock.instant().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
            reservations.getPost().getRequestBody().getContent().get("application/json").setExample(
                    Map.of("spaceId", 1L, "bookerName", "홍길동", "startAt", start.toString(),
                            "endAt", start.plus(1, ChronoUnit.HOURS).toString()));
            paths.values().forEach(path -> path.readOperations().forEach(operation -> {
                if (operation.getParameters() == null) return;
                operation.getParameters().forEach(parameter -> {
                    String name = parameter.getName();
                    switch (name) {
                        case "id", "spaceId" -> parameter.schema(new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE))
                                .example(1L).description("실제 등록된 ID로 변경하세요.");
                        case "page" -> parameter.schema(new IntegerSchema().minimum(java.math.BigDecimal.ZERO)._default(0)).example(0);
                        case "size" -> parameter.schema(new IntegerSchema().minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(100))._default(20)).example(20);
                        case "status" -> parameter.schema(new StringSchema()._enum(List.of("CONFIRMED", "CANCELLED")))
                                .example("CONFIRMED").description("생략하면 두 상태 모두 조회합니다.");
                        case "from", "to" -> parameter.schema(new StringSchema().format("date-time"))
                                .example((name.equals("from") ? start : start.plus(1, ChronoUnit.HOURS)).toString())
                                .description("from/to를 함께 지정하거나 모두 생략하세요. 오프셋 포함, 마이크로초까지 지원합니다.");
                        default -> { }
                    }
                });
            }));
        };
    }

    private static void describe(Operation operation, String tag, String summary, String description, String... errors) {
        operation.setTags(List.of(tag));
        operation.setSummary(summary);
        operation.setDescription(description);
        for (String code : errors) {
            operation.getResponses().addApiResponse(code, new ApiResponse().description(switch (code) {
                case "400" -> "입력 형식·필드·시간·페이지 검증 실패";
                case "404" -> "공간 또는 예약이 존재하지 않음";
                case "409" -> "예약 시간 중복 또는 시작한 확정 예약 취소";
                case "503" -> "일시적인 DB 경합 실패. 잠시 후 재시도";
                default -> "내부 서버 오류";
            }).content(new Content().addMediaType("application/json", new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
        }
    }
}
