# 프런트엔드 초기 환경과 공간 목록

## 범위와 기술 선택

기존 React 계획을 Vue 3·TypeScript·Vite로 변경했다. 공식 create-vue 3.24.0의 TypeScript·Router·Vitest·ESLint 옵션을 사용했고 템플릿의 예제·Oxlint·Vue DevTools 플러그인·병렬 스크립트 도구는 제외했다. Pinia·Axios·UI 프레임워크·Custom Skill은 추가하지 않았다.

Node는 로컬·CI 모두 `.nvmrc`의 24.15.0, npm은 로컬 11.12.1을 사용했다. 의존성은 package-lock.json으로 고정하고 npm ci로 설치한다. [Vue 공식 초기화](https://vuejs.org/guide/quick-start.html)와 [TypeScript 안내](https://vuejs.org/guide/typescript/overview)를 참고한다.

`/`는 `/spaces`로 이동한다. 공간 이름·설명·수용 인원·등록 시각, 전체 건수와 이전/다음 페이지를 표시한다. 목록은 ID 오름차순, page=0부터·size=20 고정이다. 로딩·빈 결과·네트워크/서버 오류·재시도를 구분한다. 공간 등록(`/spaces/new`)·상세(`/spaces/:id`)도 제공한다. 계약과 최신 검증은 [frontend-space-screens.md](frontend-space-screens.md)를 따른다. 예약 생성(`/reservations/new`)도 제공하며 공간 상세에서 사전선택할 수 있다. 최신 계약·검증은 [frontend-reservation-create.md](frontend-reservation-create.md)를 따른다. 예약 목록·취소 화면은 미구현이다.

## 실행

1. [백엔드 안내](backend-setup.md)에 따라 PostgreSQL과 백엔드를 8080에서 실행한다. 샘플 공간이 필요하면 local 프로필로 실행한다. 공간이 하나도 없을 때만 4개가 자동 등록되며, 기존 데이터가 있으면 그대로 유지한다. 추가 공간은 Swagger UI로 등록할 수 있다.
2. `frontend/`에서 아래 명령을 실행한다.

```powershell
npm ci
npm run dev
```

<http://127.0.0.1:5173/spaces>에 접속한다. 포트가 사용 중이면 Vite는 실패하며 임의 포트로 이동하지 않는다. `/api` 요청은 Vite가 <http://127.0.0.1:8080>으로 전달한다. 백엔드 CORS는 변경하지 않았다. API가 꺼져 있으면 오류와 다시 시도 버튼이 표시된다. 종료는 각 서버 터미널에서 Ctrl+C로 수행한다.

Vite 프록시는 개발용이다. build의 dist는 정적 산출물이며 운영에서는 `/api` 역방향 프록시와 SPA history fallback 또는 별도 배포 구성이 필요하다. 운영 배포·preview 서버의 API 연결은 이번 검증 대상이 아니다. 비밀값을 프런트엔드 번들로 넣지 않는다.

## 계약과 구현

- 계약 기준은 [space-api.md](space-api.md), 실제 백엔드 `/v3/api-docs`다. Space·SpacePage는 `src/api/spaces.ts`에 정의한다. 날짜 문자열을 보존하고 정수 ID·페이지 메타데이터와 필수 응답 형식을 확인한다.
- Java BIGINT 전체 범위를 JS number로 정확히 표현할 수 없다. 현재는 안전 정수 범위를 넘으면 응답 오류로 처리해 잘못 반올림된 ID를 표시하지 않는다. 큰 ID 지원은 백엔드와 문자열 ID 계약을 별도로 정해야 한다.
- `Intl.DateTimeFormat`의 timeZone을 Asia/Seoul로 고정하고 시간대를 화면에 명시한다. 저장값을 바꾸거나 브라우저 시간대에 따라 표시하지 않는다. 예약 생성은 서울 분 단위 입력을 +09:00으로 전송한다. 상세 계약은 frontend-reservation-create.md를 따른다.
- `src/api/client.ts`는 JSON 요청과 공통 오류를 처리한다. 비JSON 오류 본문을 화면에 노출하지 않는다. SpaceListView는 로컬 상태를 사용하고 새 요청·화면 이탈 시 이전 요청을 취소하며 오래된 결과를 반영하지 않는다.
- 테스트 설명은 한글로 작성한다. 단위·컴포넌트 테스트의 API 모킹과 실제 PostgreSQL·HTTP 연동을 구분한다.

## 로컬 검증과 CI

`frontend/`에서 실행한다. lint는 파일을 자동 수정하지 않으며 테스트는 watch 없이 종료한다.

```powershell
npm run lint
npm run type-check
npm run test:unit
npm run build
```

build는 vue-tsc 타입 검사와 Vite 프로덕션 번들 생성을 포함한다. 반복 개발용 test:watch 스크립트도 제공하지만 watch 실행 자체는 이번에 검증하지 않았다.

[Frontend CI](../.github/workflows/frontend-ci.yml)는 PR 생성·업데이트·재오픈·리뷰 준비 전환과 main push에서 위 검증 및 npm ci를 실행한다. 경로 필터·실패 무시 설정은 없다. Node·lockfile 경로를 명시하고 Actions는 공식 릴리스 SHA로 고정했다. 기존 Backend CI·Testcontainers·Trivy는 변경하지 않았다. 이 CI는 모킹 테스트를 실행하며 실제 백엔드 E2E를 수행한 것으로 취급하지 않는다.

## 검증 기록 (2026-11-06)

- 구현 전 npm run test:unit: 화면·API 모듈 부재로 테스트 파일 2개 로드 실패(테스트 본문 미실행). 구현 후 API 4개·화면 6개, 총 10개 통과했다.
- npm ci 재설치 성공. 최종 npm run lint·npm run type-check·npm run test:unit·npm run build 모두 종료 코드 0이며 테스트 10개가 통과했다. actionlint 1.7.7로 frontend-ci.yml 정적 검사도 통과했다.
- npm ls로 Vue 3.5.45·Vue Router 5.4.0·TypeScript 6.0.3·Vite 8.3.6·Vitest 4.1.12·ESLint 10.14.0·vue-tsc 3.3.14 선택을 확인했다. 템플릿의 허용 범위에서 해석된 버전이며 lockfile 재설치 결과와 일치한다.
- 실제 백엔드 bootJar 생성 성공(태스크 up-to-date). 기존 개발 DB와 분리한 PostgreSQL 17.11 컨테이너(frontend_check DB, 15433)와 백엔드(8080), Vite(5173)로 확인했다. 실제 OpenAPI에서 page·size 기본값 0·20 및 페이지 응답 5개 필드를 확인했다.
- Vite HTTP 프록시로 빈 목록을 확인하고 해당 임시 DB에 API로 공간 21개를 등록해 page0=20개·page1=1개·totalElements=21·totalPages=2를 확인했다. /spaces HTML 응답도 200이다. 이 검증은 브라우저 렌더링 성공을 의미하지 않는다.
- 브라우저 확인은 Computer Use가 현재 URL을 신뢰할 수 있게 확인하지 못해 정책상 중단됐다. 사용자 지시에 따라 재시도하지 않으며 렌더링·클릭·반응형·실제 키보드 동작은 미검증으로 남긴다.
- 검증용 Java·Vite 프로세스와 --rm 임시 PostgreSQL을 종료했고 8080·5173·15433 리스너 및 해당 컨테이너가 없는 것을 확인했다. 기존 Compose DB는 변경하지 않았다.
- npm audit는 개발 도구의 minimatch·glob 관련 전이 의존성을 포함해 moderate 16개를 보고했다(영향 패키지 수이며 고유 취약점 16개라는 뜻이 아님). HIGH·CRITICAL은 0개, npm audit --omit=dev는 0개다. 자동 downgrade·force fix·전이 의존성 강제 override는 하지 않았다. 개발 의존성의 호환 패치·상위 도구 업데이트는 후속 확인 항목이다.

문서 상대 링크·git diff --check·신규 파일 끝 공백 검사를 통과했다. 검증용 프로세스와 임시 DB 익명 볼륨의 제거도 추가 확인했다.

## 독립 리뷰와 남은 항목

Superpowers requesting-code-review의 독립 리뷰어 Subagent가 origin/main·HEAD `46a2868` 대비 tracked 변경과 신규 frontend·CI·문서를 읽기 전용으로 검토했다. 백엔드 DTO·페이지·오프셋 계약과 실제 변경을 대조했으며 Critical 0·Important 0·Minor 1건이다.

Minor는 취소 후 늦게 도착하는 Promise의 resolve/reject를 무시하는 회귀 테스트 보강이다. requestId 방어는 코드상 적절하나 현재 unmount 테스트는 AbortSignal 취소만 검증한다. 동작 결함은 확인되지 않았고 완료를 막는 지적이 아니므로 후속 테스트 보강으로 남긴다. 중요한 코드 수정이 없어 중복 리뷰는 하지 않았다. 리뷰어는 diff 검사만 직접 실행했으며 테스트·빌드·audit·브라우저·원격 Actions를 재실행하지 않았다.

원격 GitHub Actions, 브라우저 E2E, 프런트엔드 별도 Trivy는 미실행이다. 프런트엔드 초기 구성 단계에서는 전체 백엔드 테스트를 재실행하지 않았으나, 이후 같은 브랜치의 local 샘플 공간 추가 후 전체 백엔드 clean build를 실행해 164개·실패/오류/건너뜀 0을 확인했다. 프런트엔드 lint·테스트 10개·타입 검사 포함 build도 다시 통과했다. local 실행·검증·리뷰 기록은 [backend-setup.md](backend-setup.md)를 따른다. 이후 공간 등록·상세 화면은 frontend-space-screens.md에서 구현·검증했고 예약 생성은 frontend-reservation-create.md에서 구현·검증했고 다음 구현은 예약 목록·취소 화면이다. 브라우저 실제 UI 검증·개발 의존성 moderate 항목 확인·늦은 응답 회귀 테스트는 남은 검증 작업이다. 커밋·push는 하지 않았다.
