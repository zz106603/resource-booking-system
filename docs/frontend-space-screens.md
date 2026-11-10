# 공간 등록·상세 화면

## 범위와 계약

기존 목록에서 공간 이름을 누르면 `/spaces/:id`, 공간 등록 링크는 `/spaces/new`로 이동한다. 등록·상세는 [공간 API 계약](space-api.md)을 사용하며 예약 화면과 백엔드 변경은 포함하지 않는다.

- 등록 이름은 공백만 불가·최대100자, 설명은 선택·최대2000자, 수용 인원은 1~2147483647의 정수다. 프런트엔드 입력 검증 뒤 숫자로 전송한다. 입력 이름·설명의 공백을 임의로 제거하지 않으며 비어 있는 설명만 null로 보낸다.
- 클라이언트 오류·서버 fieldErrors를 각 필드 아래 표시하고 입력을 유지한다. label·aria-invalid·aria-describedby·전체 오류 안내를 제공한다. 제출 중 입력과 버튼을 비활성화하고 중복 요청을 차단한다.
- 성공하면 반환된 실제 ID의 상세로 이동한다. 자동 POST 재시도는 없다. 응답 유실 시 등록 여부가 불명확하므로 목록 확인을 안내한다. 등록 중 화면을 떠나도 서버 처리가 취소되는 것은 아니며 늦은 응답으로 이동하지 않는다.
- 상세는 로딩·잘못된 ID·404·통신 오류를 구분한다. 통신 오류는 재시도할 수 있다. 경로 ID 변경과 화면 이탈 시 요청을 취소하고 이전 응답을 무시한다.
- ID는 기존 안전 정수 한계까지 지원한다. 등록 시각은 원본 문자열을 보존하며 Asia/Seoul로 표시한다.

실행 방법은 [frontend-setup.md](frontend-setup.md)를 따른다. 추가 라이브러리·스키마·CI 변경은 없다. 기존 getJson은 POST와 오류 필드를 함께 처리하는 작은 요청 함수로 확장했다. 비JSON 오류 문구는 목록·상세·등록에서 공통으로 사용할 수 있게 변경했다.

## 검증 기록 (2026-11-06)

- RED: 신규 API 테스트15개 실패와 새 화면 파일 부재로 화면 테스트 파일1개 로드 실패를 확인했다. 기존10개는 통과했다.
- 첫 구현 후 신규23개는 통과했으나 기존 목록6개가 RouterLink의 Router 설정 부재로 실패했다. 목록 테스트에 RouterLinkStub을 제공해 해결했다. 타입 검사에서는 get()의 DOMWrapper에 exists()를 호출한 테스트 단언1건이 실패해 href 단언으로 수정했다.
- 최종 npm run test:unit: 전체33개 통과. npm run lint, npm run build(vue-tsc 포함) 성공. 단위·컴포넌트 테스트는 fetch/API를 모킹한다.
- 실제 PostgreSQL17.11 임시 DB·백엔드8080·Vite5173에서 HTTP 프록시로 등록201→동일ID 상세200, 입력 공백 보존, 잘못된 입력400/VALIDATION_ERROR(name·capacity fieldErrors), 없는ID404를 확인했다. OpenAPI POST201 계약과 /spaces/new·/spaces/:id HTML200도 확인했다.
- 백엔드 bootJar는 성공(up-to-date)했으며 기존 백엔드 전체 테스트를 재실행한 것은 아니다. 검증용 서버·임시 DB는 종료했다. 기존 개발 DB는 사용하거나 변경하지 않았다.

문서 상대 링크·git diff --check·신규 파일 공백 검사를 통과했다. 백엔드·의존성·기존 CI는 변경하지 않았다.

## 독립 리뷰와 남은 항목

Superpowers requesting-code-review의 독립 리뷰어 Subagent가 기준 origin/main·HEAD `39dca3d` 대비 전체 diff와 신규 파일을 지침·요구사항과 대조했다. Critical 0·Important 0·Minor 1건이다. Minor는 등록 중 화면 이탈 후 늦은 POST 응답, 상세의 늦은 실패·화면 이탈 후 응답에 대한 회귀 테스트 보강 권고다. 현재 active/requestId 방어는 적절하며 기능 결함은 발견하지 못해 후속 검증으로 남긴다. 중요한 코드 수정이 없어 중복 리뷰하지 않았다. 리뷰어는 diff 검사를 직접 수행했고 테스트·빌드·실제 API를 재실행하지 않았다.

브라우저 렌더링·실제 클릭·포커스·키보드·반응형·브라우저 E2E와 원격 CI는 미검증이다. 이전 브라우저 정책 차단 및 사용자 지시에 따라 브라우저 도구를 재시도하지 않았다. 이후 [예약 생성 화면](frontend-reservation-create.md)을 추가했다. 이후 [예약 목록·취소 화면](frontend-reservation-list.md)도 구현했다. 커밋·push는 하지 않았다.
