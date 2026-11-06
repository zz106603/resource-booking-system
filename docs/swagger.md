# Swagger UI와 예시 요청

## 접근과 범위

[백엔드 실행 안내](backend-setup.md)에 따라 PostgreSQL과 애플리케이션을 실행한다.

- UI: <http://localhost:8080/swagger-ui/index.html> (`/swagger-ui.html`에서도 이동)
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- OpenAPI YAML: <http://localhost:8080/v3/api-docs.yaml>

공간 등록·목록·상세와 예약 생성·목록·취소의 6개 작업을 제공한다. 각 작업을 펼쳐 **Try it out**을 누르면 요청 본문과 파라미터 예시를 편집할 수 있다. **Execute**는 실제 HTTP 요청을 보내므로 연결된 DB에 등록·취소가 반영된다. 데모 데이터 삽입이나 인증 기능은 추가하지 않는다.

## 처음 실행하는 순서

1. `POST /api/spaces`: 채워진 회의실 이름·설명·수용 인원으로 실행하고 응답의 `id`를 복사한다.
2. `GET /api/spaces/{id}`: 예시 `1`을 실제 공간 ID로 변경한다.
3. `POST /api/reservations`: `spaceId`를 위 ID로 변경한다. 예약자명과 미래의 시작·종료 예시가 채워져 있다. 실행 후 예약 `id`를 복사한다.
4. `GET /api/reservations`: 실제 공간 ID와 원하는 필터를 입력한다. 전체 조회하려면 선택 필터의 예시 값을 제거하고 파라미터를 보내지 않는다. 빈 문자열은 유효한 필터가 아니다. from/to는 모두 지정하거나 모두 생략한다.
5. `POST /api/reservations/{id}/cancel`: 실제 예약 ID로 실행한다. 시작 전 확정 예약만 최초 취소할 수 있고 이미 취소한 예약은 재취소해도 200이다.

예시 ID `1`은 DB에 존재한다는 보장이 없다. 공간을 자동 선택하거나 새로 만들지 않는다. 같은 예약 예시를 반복 실행하면 중복 예약 정책에 따라 409다.

## 시각과 구현 선택

springdoc-openapi starter **2.10.0**을 고정했다. [공식 Boot 3.5 호환 문서](https://springdoc.org/v2/)와 Maven Central 배포를 확인했다. Boot·기존 보안 패치 버전은 유지한다.

`OpenApiConfiguration`은 문서 전용 메타데이터와 예시를 제공한다. 예약 생성과 구간 필터 예시는 주입된 Clock의 현재 시각에서 하루 뒤부터 1시간이며 UTC·마이크로초 정밀도다. 문서 캐시를 비활성화해 OpenAPI 재조회 시 갱신한다. 열린 UI의 편집 값은 자동 갱신되지 않으므로 오래 열어 둔 경우 페이지를 새로고침한다. 직접 변경할 때에도 미래 시작·시작 < 종료 규칙을 지킨다.

선택 필터의 예시는 서버 기본값이 아니다. 상태 미지정은 두 상태 모두, 공간 미지정은 전체 공간이다. 문서의 enum·정수·범위 표시는 실제 API 계약을 설명하며 서버 입력 검증을 대체하지 않는다.

기본 설정에서 문서와 UI가 제공된다. 외부 배포 시 공개 여부를 결정하고 필요하면 `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`로 비활성화한다. 인증·접근 제어와 운영 배포는 이번 범위가 아니다.

## 검증

2026-11-06, `backend/`에서 실행했다.

- `.\gradlew.bat test --tests '*OpenApiIntegrationTest' --no-daemon`: 구현 전 4개 실패(UI·문서 경로 미제공), 구현 후 4개 통과(1분 1초).
- `.\gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL(2분 14초). 기존 155개와 신규 4개, 총 159개이며 실패·오류·건너뜀 0이다. 실제 PostgreSQL 통합·동시성·교착 복구 회귀와 실행 JAR 생성을 포함한다.
- HTTP로 UI HTML·설정·OpenAPI, 6개 작업과 필터 타입, 미래 시각 재생성, 문서의 예시 JSON으로 공간→예약→목록→취소를 검증했다. 실제 ID만 생성 응답으로 교체했다.
- 실행 JAR에 springdoc 2.10.0·Swagger UI 5.34.0이 포함되며 기존 Jackson 2.21.8·Tomcat 10.1.61·PostgreSQL JDBC 42.7.14 선택을 유지함을 확인했다.
- 컴파일 시 Swagger Schema API 관련 unchecked 경고와 기존 테스트 JVM 클래스 공유 경고가 있었으며 빌드 실패는 아니다.
- 기본 샌드박스의 브랜치 잠금 파일·Docker 접근과 Maven DNS 확인은 권한 제한으로 실패했다. 허용된 권한으로 브랜치 생성·공식 메타데이터·Docker 연결을 확인하고 빌드했다. 설정을 우회하거나 테스트를 생략하지 않았다.

독립 리뷰어 Subagent가 origin/main·HEAD `819abd3` 대비 미커밋 변경과 신규 파일을 요구사항과 대조했다. Critical·Minor 없음, Important 1건(공간 등록의 실제 201이 자동 문서에서 200으로 표시됨)을 지적했다. 실제 `/v3/api-docs`의 201 존재·200 부재 단언으로 실패를 재현했다(1개 실패, 31초). 문서 계층에서 기존 성공 응답 스키마를 보존해 201로 교체했다. 리뷰어가 수정 부분을 재검토해 해당 지적의 코드상 해소와 추가 지적 없음을 확인했다. 수정 후 `.\gradlew.bat build --no-daemon`은 1분 16초에 성공했으며 전체 159개, 실패·오류·건너뜀 0이다. 테스트 태스크가 실제 재실행되었고 실행 JAR도 다시 생성했다. 문서 상대 링크·`git diff --check`·신규 파일의 끝 공백 검사도 통과했다.

브라우저에서의 실제 렌더링·클릭, 원격 CI, 새 의존성의 Trivy 재검사는 실행하지 않았다. 리뷰어도 빌드·브라우저·스캐너를 재실행하지 않았다. 커밋·push는 하지 않는다.
