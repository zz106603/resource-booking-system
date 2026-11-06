# 로컬 샘플 공간 구현 계획

**Goal:** local 프로필로 백엔드를 시작할 때 공간이 하나도 없으면 샘플 4개를 준비한다.

**Architecture:** Flyway 후 ApplicationRunner가 실행된다. local 전용 빈이 한 트랜잭션에서 공간 유무 확인과 등록을 수행한다. 로컬 중복 기동에도 4개만 생성하도록 짧은 PostgreSQL 테이블 잠금으로 빈 테이블 확인을 보호한다. 기존 스키마·API·공간·예약은 변경하지 않는다.

**승인 범위:** 현재 feat/frontend-setup 브랜치와 미커밋 변경을 보존한다. 기본·통합 테스트 실행에서는 local을 활성화하지 않는다. 커밋·push 금지.

- [x] 실제 PostgreSQL 테스트를 먼저 추가해 기본 프로필 미적용, local 시작 시 4개 등록, 비어 있을 때 재등록, 재실행 시 동일 ID 유지, 기존 공간·예약 보존을 확인한다.
- [x] space/LocalSpaceInitializer.java를 추가하고 관련 테스트를 통과시킨다. 고정 ID·기존 행 수정·예약 생성은 하지 않는다.
- [x] 로컬 실행 프로필 명령과 적용 범위를 backend-setup·frontend-setup·architecture·swagger 문서에 갱신한다.
- [x] 전체 백엔드 build(164개), 프런트엔드 기존 검사(10개) 및 문서 일관성을 확인하고 독립 리뷰를 수행한다. Critical·Important 없음, Minor 문서 불일치 1건은 수정했다. 검증용 local 서버·임시 DB는 정리했다.

설계 판단: Docker init SQL은 새 볼륨에서만 실행되고 Flyway 이전에 실행되므로 사용하지 않는다. 공통 Flyway 마이그레이션에는 개발 데이터를 넣지 않는다. 실제 운영 DB에 local을 잘못 지정하면 샘플을 넣을 수 있으므로 프로필은 로컬 DB 연결에서만 명시한다.
