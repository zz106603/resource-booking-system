# 예약 목록 조회 API

## 승인한 계약

- `GET /api/reservations`: 200과 `content`, `page`, `size`, `totalElements`, `totalPages`를 반환한다. content 항목은 기존 ReservationResponse와 같다.
- `spaceId`는 선택인 양의 BIGINT다. 생략하면 전체 공간, 지정한 공간이 없으면 404 SPACE_NOT_FOUND다.
- `status`는 선택이며 정확히 CONFIRMED 또는 CANCELLED다. 생략하면 두 상태를 모두 조회한다.
- `from`, `to`는 둘 다 지정하거나 모두 생략한다. 지정하면 `reservation.startAt < to && reservation.endAt > from`을 만족하는 예약을 반환한다. `[from, to)` 양끝에 맞닿기만 하는 예약은 제외한다.
- 시각은 오프셋 포함 ISO-8601 문자열이며 UTC 연도 1~9999, 마이크로초 정밀도까지 지원한다. `from < to`여야 한다. 과거 조회를 허용하고 최대 조회 기간은 두지 않는다. 양의 UTC 오프셋의 `+`는 URL에서 `%2B`로 인코딩한다.
- 모든 필터는 AND로 결합한다. 정렬은 startAt ASC, id ASC로 고정한다. 임의 정렬 기능은 제공하지 않는다.
- page는 0부터, 기본 0이다. size는 기본 20, 1~100이다. 음수·숫자 형식 오류·지원 범위 초과 및 `page * size > Integer.MAX_VALUE`는 400 INVALID_REQUEST다.
- 필터의 잘못된 형식·알 수 없는 상태·빈 값·한쪽 시각 누락·잘못된 구간·정밀도·지원 연도 위반도 400 INVALID_REQUEST다. 파라미터 자체를 생략하는 것과 빈 값을 지정하는 것은 구분한다.
- 결과 없음과 마지막 페이지 이후는 200 및 빈 content다. totalElements는 필터를 적용한 전체 건수이며 totalPages는 해당 건수와 size로 계산한다.

## 구현 선택과 범위

페이지 번호 방식은 기존 공간 API와 응답을 맞추기 위해 선택했다. 커서 방식은 대규모 조회의 후속 대안이다. 최대 조회 기간을 임의로 정하지 않고 페이지 크기만 제한한다.

별도 ReservationQueryService의 읽기 전용 REPEATABLE READ 트랜잭션에서 공간 존재 여부와 필터 조회·건수를 읽는다. 한 응답의 데이터와 건수는 같은 DB 스냅샷을 사용한다. 서로 다른 페이지 요청 사이에 데이터가 바뀌면 결과가 달라질 수 있으며 여러 요청에 걸친 스냅샷은 보장하지 않는다.

Spring Data JPA Specification으로 지정된 조건만 결합하고 DB에서 정렬·페이지·건수 조회를 수행한다. 생성·취소의 NOT_SUPPORTED/REQUIRES_NEW 경계와 재시도 정책은 변경하지 않는다. 목록은 쓰기 잠금을 사용하지 않는다. 기존 V3 인덱스를 유지하며 이번에는 스키마 변경·새 인덱스·성능 보장을 추가하지 않는다.

AC10은 이번 구현의 대상이다. 인증·소유자·예약 변경·프런트엔드·고부하 벤치마크는 제외한다. 실행 방법은 [backend-setup.md](backend-setup.md), 생성·취소 계약은 [reservation-api.md](reservation-api.md)를 따른다.

## 검증 계획

실제 PostgreSQL·HTTP로 전체/각 필터/복합 필터, 과거·취소 이력, 포함·부분 겹침·맞닿음, UTC 오프셋·마이크로초, 동일 시작 시각의 ID 순서, 페이지 기본값·경계·빈 결과, 모든 입력 오류와 없는 공간을 검증한다. 기존 생성·취소·동시성 회귀를 포함해 전체 clean build를 실행한다. 테스트 결과와 독립 리뷰는 수행 후 기록한다.

## 실행 기록 (2026-10-29)

`backend/`에서 `.\gradlew.bat test --tests '*ReservationListIntegrationTest' --no-daemon`을 먼저 실행했다. GET 미구현으로 응답이 405여서 계약 테스트 45개가 모두 실패했다(36초). 구현 후 같은 명령은 26초에 성공했고 45개 모두 통과했다. 실제 Testcontainers PostgreSQL 17.11 Alpine과 HTTP를 사용했으며 건너뜀은 없다.

이후 `.\gradlew.bat clean build --no-daemon`은 1분 37초에 BUILD SUCCESSFUL로 완료했다. 총 155개, 실패·오류·건너뜀 모두 0이다.

| 테스트 | 실행 개수 |
| --- | --- |
| PostgresIntegrationTest | 5 |
| SpaceApiIntegrationTest | 45 |
| ReservationApiIntegrationTest | 48 |
| ReservationListIntegrationTest | 45 |
| ReservationServiceTest (단위) | 12 |

PostgreSQL 통합 143개와 단위 12개를 구분한다. 기존 생성·취소·동시성·교착 복구와 Flyway V1~V4 회귀를 포함하며 마이그레이션 파일은 변경하지 않았다. 테스트 JVM 클래스 공유 경고와 기존 실패 경로 검증에서 의도한 오류 로그는 있었으나 최종 실패는 없다.

`requesting-code-review` Skill의 독립 리뷰어 Subagent가 기준 origin/main·HEAD `cff163e`와 미커밋 tracked diff 및 신규 파일 전체를 승인 계약과 대조했다. Critical·Important·Minor 지적 모두 없으며 리뷰 통과다. 리뷰어는 XML의 155개 성공을 직접 확인했으나 빌드를 다시 실행하지 않았다. 동시 쓰기 중 content/count 스냅샷을 별도 실험한 것은 아니며 REPEATABLE READ 경계와 조회 코드를 근거로 검토했다. 중요한 코드 수정이 없어 중복 리뷰는 하지 않았다.

문서 간 현재 AC10 상태와 과거 제외 이력을 구분했고 상대 링크, `git diff --check`, 신규 파일 공백 검사를 통과했다. 의존성·마이그레이션·AGENTS.md는 변경하지 않았다. 커밋·push는 수행하지 않았다.

고부하 성능·새 인덱스 실행 계획·다중 인스턴스 벤치마크 및 원격 CI는 실행하지 않았다. 최대 조회 기간 제한과 커서 페이지는 이번 계약에 추가하지 않았으며 실제 데이터와 측정 결과를 바탕으로 추후 검토한다.
