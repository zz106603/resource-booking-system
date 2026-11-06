# 프런트엔드 초기 구성 구현 계획

**Goal:** Vue 3·TypeScript·Vite 초기 구성, 독립 CI와 실제 공간 목록 조회를 구현한다.

**Architecture:** 공식 create-vue 기반 SPA. Router로 /spaces를 제공하고 작은 fetch API 함수와 화면 로컬 상태를 사용한다. 개발 시 /api를 localhost:8080으로 프록시한다. 시각 표시는 Asia/Seoul로 고정한다.

**승인 범위:** 기존 React 계획을 Vue로 변경하고 루트 지침 최소 수정·frontend/AGENTS.md와 실행 문서를 추가한다. Pinia·Axios·UI 프레임워크·Custom Skill 없이 시작한다. 공간 등록/상세·예약 화면은 후속 작업이다. 커밋·push 금지.

## 작업

- [x] 공식 도구의 TypeScript·Router·Vitest·ESLint 구성으로 초기화하고 불필요한 예제·중복 도구는 제외한다.
- [x] API 페이지 파라미터·오류와 목록 로딩·빈 결과·재시도·이전/다음 페이지·Asia/Seoul 표시 테스트를 먼저 작성하고 실패를 확인한다.
- [x] src/api/spaces.ts, views/SpaceListView.vue 및 Router·기본 CSS를 구현하고 테스트를 통과시킨다. 요청이 겹치면 오래된 결과를 반영하지 않는다.
- [x] Node 24 고정·npm lockfile·독립 frontend-ci.yml을 추가한다. PR·main push에 npm ci, lint, type-check, 단위/컴포넌트 테스트, build를 수행한다. 백엔드 CI는 보존한다.
- [x] AGENTS.md·frontend/AGENTS.md·아키텍처·요구사항·README·프런트엔드 실행 안내를 갱신한다.
- [x] 설치 재현·lint·type-check·전체 프런트엔드 테스트·빌드·워크플로 정적 검사와 가능한 실제 PostgreSQL/백엔드/프록시 연결을 검증한다. 브라우저 검증은 정책 차단 및 사용자 지시에 따라 미검증으로 남기고 자체 검증 서버를 정리했다.
- [x] 기준 main과 실제 변경사항의 독립 리뷰, 타당한 지적 처리, 최종 검증과 미검증 보고를 수행한다. Critical·Important 없음. Minor 1건(늦은 응답 무시 회귀 테스트 보강)은 후속으로 기록했다. 결과는 [frontend-setup.md](../../frontend-setup.md)를 따른다.

## 검토 초점

페이지는 0부터·기본20·최대100이며 전체 데이터를 한 페이지만으로 취급하지 않는다. 실패 응답·비JSON 응답·네트워크 실패와 빈 결과를 구분한다. ID의 안전 정수 한계는 조용히 반올림하지 않는다. 로딩 중 중복 요청과 unmount 후 갱신을 방지한다. 화면 조작은 키보드로 가능하고 오류·로딩을 읽을 수 있어야 한다. 테스트의 모킹 결과를 실제 백엔드 연결 성공으로 기록하지 않는다.

실행은 현재 세션에서 진행하고 완료 시 requesting-code-review의 독립 리뷰어를 사용한다. 검증 기록은 docs/frontend-setup.md에 남긴다.
