# 초기 아키텍처

## 상태와 구조

공간 등록·조회는 `space` 패키지와 Flyway V2의 `spaces`, 예약 생성·취소는 `reservation` 패키지와 V3의 `reservations`로 구현했다. V4는 CONFIRMED 한정 배타 제약을 추가하며 V1~V3는 보존한다. 실행 안내는 [backend-setup.md](backend-setup.md), API 계약은 [space-api.md](space-api.md) 및 [reservation-api.md](reservation-api.md), 최신 동시성 설계·검증은 [reservation-concurrency.md](reservation-concurrency.md), 수용 기준은 [requirements.md](requirements.md)를 따른다. 예약 목록 AC10은 미구현·미검증이다.

단일 Spring Boot 애플리케이션과 PostgreSQL로 시작한다. 분산 시스템의 복잡도 없이 트랜잭션과 동시성 제어를 검증하기 위해서다. 저장소는 현재 `backend/`, `docs/`와 루트 `compose.yaml`로 구성한다. `frontend/`는 추후 React·TypeScript·Vite로 개발한다.

요청 흐름은 HTTP API → 애플리케이션 서비스 → 도메인 규칙 및 영속성 → PostgreSQL이다. 기능별 `space`, `reservation` 패키지와 작은 공통 오류 처리 영역을 제안한다. 별도 Gradle 멀티모듈이나 범용 프레임워크는 도입하지 않는다.

## 주요 책임

| 영역 | 책임 |
| --- | --- |
| API | 요청·응답 DTO, 형식·필수값 검증, HTTP 상태 및 공통 오류 변환 |
| Space | 공간 등록·조회, 수용 인원 등 공간 규칙 |
| Reservation | 생성·취소·목록, 시간 구간 및 상태 전이 규칙 |
| 애플리케이션 서비스 | 트랜잭션 경계, 중복 조회·저장 및 선택한 동시성 제어 방식의 순서 조율 |
| 영속성 | 공간·예약 저장, 중복 및 목록 조회, 선택한 동시성 제어 방식 구현 |
| 공통 | 오류 코드·응답, 주입 가능한 Clock 등 실제 공통 관심사만 포함 |

엔티티를 API 응답으로 직접 노출하지 않는다. 입력 형식 검증과 비즈니스 규칙 검증을 구분하고, 시간 판단은 주입 가능한 Clock을 사용한다.

## 데이터 모델

| 테이블 | 주요 필드 | 제약·의미 |
| --- | --- | --- |
| spaces | id, name, description, capacity, created_at | PK, 이름 필수, capacity > 0, description 선택 |
| reservations | id, space_id, booker_name, start_at, end_at, status, created_at, cancelled_at | PK, 공간 FK, 필수값 NOT NULL, start_at < end_at, 상태 제한 |

- 공간 1개에 예약 여러 개가 연결된다. 예약을 물리 삭제하지 않는다.
- 공간과 예약 ID는 DB 생성 BIGINT IDENTITY를 채택했다.
- 시각은 PostgreSQL `timestamptz`, Java에서는 `Instant` 중심으로 취급한다. 원래 입력의 시간대 이름을 보존하는 요구는 없다.
- `CONFIRMED`이면 cancelled_at은 NULL, `CANCELLED`이면 취소 시각을 기록한다. 기본 무결성은 DB 제약과 애플리케이션에서 함께 보장한다.
- V3의 `(space_id, status, start_at)` B-tree 인덱스는 보존한다. V4는 공간 동등성·tstzrange `[)` 겹침을 결합한 GiST 배타 제약을 CONFIRMED에만 적용한다. 중복 무결성의 최종 보장은 이 제약이며 실행 계획·부하 튜닝은 후속 과제다.
- 공간 이름은 VARCHAR(100), 설명은 선택 VARCHAR(2000), 목록은 ID 오름차순에 기본 20개·최대 100개 페이지를 적용했다. 예약자명은 VARCHAR(100)이다. 예약 목록의 페이지 크기와 최대 조회 구간은 미결정이다.

## 동시성 및 트랜잭션 결정

현재 구현은 **PostgreSQL 배타 제약으로 중복을 보장하고 취소는 예약 ID 행만 잠근다.** 공간 행 쓰기 잠금과 사전 중복 조회는 제거했다. 생성은 명명된 제약에 대한 INSERT ON CONFLICT DO NOTHING RETURNING을 사용한다. 반환 행이 없으면 중복 409이며 다른 제약 위반을 삼키지 않는다. 실행 전후 Clock으로 미래 시작을 검사해 대기 중 시각이 지나면 롤백한다.

단순 INSERT와 3회 교착 재시도만 적용한 중간 구현에서는 기존 8동시 테스트에서 503이 발생했다. PostgreSQL의 speculative insertion 복구 경로를 쓰는 DO NOTHING을 선택했으며 재시도 횟수 증가로 문제를 감추지 않았다. 생성 SQL은 JdbcTemplate이 동일 DataSource의 JpaTransactionManager 트랜잭션에 참여하여 실행한다. 취소는 JPA의 PESSIMISTIC_WRITE로 예약 행을 읽고 잠금 이후의 상태·Clock으로 변경한다.

외부 ReservationService는 NOT_SUPPORTED로 실패한 트랜잭션 밖에서 오류를 분류한다. 별도 ReservationTransactions 빈의 각 생성·취소 시도는 REQUIRES_NEW·READ COMMITTED다. 재시도 시 새 영속성 컨텍스트와 트랜잭션을 사용하고 실패한 시도의 쓰기를 롤백한다. self-invocation으로 트랜잭션 프록시를 우회하지 않는다.

PostgreSQL이 고정되어 있고 시간 구간 무결성이 핵심이므로 DB 전용 배타 제약을 채택했다. 공간 잠금은 구현이 간단하지만 무관한 구간도 직렬화하며 직접 SQL을 강제하지 못한다. SERIALIZABLE은 별도의 직렬화 실패 정책이 필요하다. 비교·실패 근거와 전환 순서는 [reservation-concurrency.md](reservation-concurrency.md)에 기록한다. 이번에는 고부하 벤치마크를 실행하지 않는다.

해당 이름의 예약 배타 제약에서 발생한 23P01만 중복 409로 변환한다. 40P01만 최초 포함 최대 3회, 10~30ms jitter 후 새 트랜잭션에서 재시도한다. 소진·인터럽트·다른 일시적 시간 초과는 503이다. 각 시도의 제한은 15초이며 전체 HTTP 요청의 전역 제한은 아니다. 전체 요청 마감·성능 조정은 별도 후속 과제다.

V4는 테이블의 쓰기를 막은 상태에서 기존 활성 중복을 점검하고 제약을 설치한다. 중복이 있으면 실패하며 데이터를 자동 삭제·취소하지 않는다. btree_gist를 public에 설치할 권한과 마이그레이션 잠금에 따른 운영 절차가 필요하다.

## 기술 선택 이유와 검증 방향

| 선택 | 이유·확인할 점 |
| --- | --- |
| Java 21, Spring Boot 3.5.16, Gradle 8.14.3 | 요청된 기반. 호환 범위 확인 후 Wrapper와 Boot BOM으로 버전 고정 |
| PostgreSQL | 트랜잭션·행 잠금 및 실제 운영 DB와 같은 의미의 검증 |
| Spring Data JPA | 초기 의존성으로 채택. 일반 CRUD와 트랜잭션 통합을 단순화. 향후 잠금·쿼리는 생성 SQL과 통합 테스트로 확인 |
| Flyway | 초기 환경에 채택. 스키마와 제약을 버전 관리. Hibernate는 validate로 설정하고 자동 스키마 갱신에 의존하지 않음 |
| JUnit 5, Testcontainers | 순수 규칙 테스트와 실제 PostgreSQL 통합·동시성 테스트 분리 |

단위 테스트는 시간 경계·겹침·상태 전이를 확인한다. 통합 테스트는 마이그레이션, DB 제약, API 오류 계약, 롤백을 검증한다. 동시성 테스트는 독립 트랜잭션과 충분한 DB 연결을 확보하고 제한 시간 내 완료 여부와 최종 데이터를 확인한다. 테스트 격리를 통해 서로의 예약 데이터에 영향을 주지 않도록 한다.

Gradle Wrapper와 빌드 파일, 로컬 PostgreSQL Docker Compose 및 Testcontainers 통합 테스트를 구성했다. 공간 API 결과는 [space-api.md](space-api.md), 예약 생성·취소와 동시성 설계·검증 결과는 [reservation-api.md](reservation-api.md)와 [reservation-concurrency.md](reservation-concurrency.md)에 기록한다. 초기 환경 검증 결과와 CI 실행 절차는 [backend-setup.md](backend-setup.md)에 기록한다. GitHub Actions는 PR 생성·업데이트 및 main push에서 Java 21·Wrapper로 전체 빌드와 PostgreSQL 테스트를 실행하도록 구성했다. Docker 연결 실패는 CI 실패로 처리한다. 현재 예약 API 브랜치에는 예약 생성·취소, 동시성 및 교착 복구 테스트가 포함되며 동일한 CI 빌드 명령의 실행 대상이다. 이 통합 상태의 원격 CI 실행 결과는 아직 확인하지 않았다.

## 구현 전에 확인할 미결정 사항

1. 시작 전 최초 취소, 시각과 무관한 재취소 성공, 미래 예약·마이크로초 정밀도는 구현 계약으로 채택했다. 인증·소유자 권한은 후속 과제다.
2. 공간과 예약 생성·취소 API 계약은 각 문서에 정리했다. 예약 목록·페이지·필터 계약은 미결정이다.
3. 초기 환경 버전과 Java 21·Docker 호환성은 확인했다. 향후 버전 갱신 시 동일 검증을 다시 수행한다.
4. 배타 제약·예약 행 잠금·제한 재시도를 채택했다. 직접 SQL 무결성·실제 교착·재시도 소진·대기 후 시간 정책을 테스트하고 성능 보장은 별도로 검증한다.
5. 동시 테스트는 8개 HTTP 요청과 기본 Hikari 최대 10개 연결, 같은 구간 생성 3회 반복으로 시작한다. 부하와 다중 인스턴스 규모의 검증은 후속 과제다.
6. 실행·검증 및 CI 절차는 backend-setup.md에 기록했다. 도메인 구현 후 테스트 범위를 확장하고 원격 CI 실행 결과와 필수 상태 검사 설정을 확인한다.

다음 작업은 예약 목록과 필터 계약을 정하고 구현하는 것이다. Custom Skill, Subagent, 자동 Loop는 필요성이 확인되면 단계적으로 추가하며, GSD 등의 Framework는 도입하지 않는다.
