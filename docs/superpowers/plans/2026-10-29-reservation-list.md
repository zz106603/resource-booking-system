# 예약 목록 조회 구현 계획

> 실행: superpowers:executing-plans로 현재 세션에서 구현하고, 완료 후 requesting-code-review의 독립 리뷰어에게 검토를 맡긴다.

**Goal:** 승인한 예약 필터·페이지 계약으로 AC10을 구현한다.
**Architecture:** 별도 읽기 서비스와 JPA Specification으로 조회하고 기존 쓰기 경로는 보존한다.
**Tech Stack:** Java 21, Spring Boot, JPA, PostgreSQL 17.11, JUnit 5, Testcontainers.
**Spec:** [승인 계약](../../reservation-list.md).

## 제약과 검토 초점

- feat/reservation-list에서 작업하며 커밋·push하지 않는다. 한글 테스트명을 사용한다.
- 테스트는 실제 HTTP·PostgreSQL로 검증하며 Docker 부재를 성공으로 처리하지 않는다.
- 필터 누락/빈 값, Long·offset 오버플로, 상태 오타, 시간대/정밀도와 양끝 경계, 동률 정렬을 보호한다.
- 새 성능 주장·스키마 변경·인증 기능을 추가하지 않는다.

## 작업

- [x] ReservationListIntegrationTest에 계약 테스트를 작성하고 `test --tests '*ReservationListIntegrationTest' --no-daemon`으로 GET 미구현 실패를 확인한다.
- [x] ReservationController에 GET을 추가한다. ReservationQueryService.list는 원시 선택 필터 문자열과 page/size를 검증하고 ReservationPageResponse를 반환한다. 빈 값과 생략을 구분한다.
- [x] ReservationRepository에 JpaSpecificationExecutor를 추가한다. 조회 서비스에서 startAt/id 정렬과 AND 조건, 공간 존재, 지원 시각·offset을 검증한다. InvalidReservationQueryException은 공통 INVALID_REQUEST 400으로 매핑한다.
- [x] 목록 통합 테스트 통과를 확인하고 `clean build --no-daemon`으로 기존 전체 테스트와 빌드를 검증한다.
- [x] requirements·architecture·기존 API 문서의 현재 AC10 상태와 참조를 갱신한다. 과거 검증 이력은 과거 기록으로 유지한다.
- [x] origin/main·HEAD와 미커밋 실제 diff를 독립 리뷰어에게 전달한다. 타당한 Critical/Important는 재현·수정·관련 테스트 후 변경 범위 재검토한다. 리뷰 지적 없음.
- [x] 실행 결과·실패 이력·리뷰 결과를 reservation-list.md에 기록하고 문서 링크·diff·변경 범위를 점검한다.
