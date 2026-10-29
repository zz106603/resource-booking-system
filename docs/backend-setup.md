# 백엔드 초기 환경

## 범위와 작업 순서

기준 문서는 [requirements.md](requirements.md)와 [architecture.md](architecture.md)다. 아래 작업 순서와 2026-10-27 검증 결과는 초기 환경 구성 당시 기록이다. 이후 추가한 공간 API의 계약과 검증 결과는 [space-api.md](space-api.md)에 기록한다.

1. Java 21 기반 Gradle 빌드와 Wrapper를 구성한다.
2. 실제 PostgreSQL에서 애플리케이션 시작·Flyway 적용을 확인하는 JUnit 5 테스트를 먼저 작성한다.
3. 최소 애플리케이션, 환경변수 기반 연결 설정, 도메인 테이블 없는 초기 마이그레이션을 추가한다.
4. Docker Compose 로컬 DB와 Testcontainers 테스트 DB를 분리하고 빌드·테스트·실행을 검증한다.
5. 실제 실행 결과와 사용 명령을 아래에 기록한다. 커밋·push는 하지 않는다.

## 버전 선택

- Java 21: 프로젝트 기준. 로컬 JDK 21.0.7 확인.
- Spring Boot 3.5.16: JUnit 5 기반 기존 요구사항에 맞는 안정 3.5 계열. 최신 주 버전이라는 의미는 아니다.
- Gradle 8.14.3: Java 21 및 Boot 3.5 호환 범위의 Wrapper 사용.
- PostgreSQL 17.11 Alpine: 로컬 개발과 통합 테스트에서 같은 이미지 태그 사용.
- JPA·Flyway·PostgreSQL JDBC·JUnit·Testcontainers: Boot BOM으로 호환 버전을 함께 관리한다. 실제 해석 결과는 Flyway 11.7.2, PostgreSQL JDBC 42.7.11, JUnit Jupiter 5.12.2, Testcontainers 1.21.4다.

공식 근거: [Boot 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Boot 의존성 관리](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [PostgreSQL 지원 버전](https://www.postgresql.org/support/versioning/).

## 로컬 실행

전제: Java 21, Docker Desktop의 Linux 컨테이너 엔진과 Docker Compose, 최초 의존성 다운로드를 위한 네트워크. Gradle 별도 설치는 필요 없으며 `backend/`의 Wrapper를 사용한다. 아래 명령은 Windows PowerShell에서 실행·확인했다.

저장소 루트에서 DB를 실행한다.

```powershell
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

DB는 `127.0.0.1:15432`, 데이터베이스·사용자는 `resource_booking`, 로컬 전용 암호는 `local_password`다. 이 기본값은 개발용이며 외부 환경에서는 연결 설정을 제공한다. 데이터는 명명된 Docker 볼륨에 보존된다.

`backend/`에서 애플리케이션을 실행한다.

```powershell
.\gradlew.bat bootRun --no-daemon
```

기본 HTTP 포트는 8080이다. `/`에는 API가 없어 404이며, 공간 API는 `/api/spaces`에서 제공한다. 시작 로그의 `Started ResourceBookingApplication`과 Flyway 적용 결과로 기동 여부를 확인한다. 종료는 Ctrl+C를 사용한다.

연결 설정은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 변경한다. DB 공개 포트를 변경하려면 Compose의 `POSTGRES_PORT`와 JDBC URL의 포트를 함께 맞춘다. `.env`는 Compose 보간에만 사용되며 Spring Boot에 자동 로드되지 않는다.

루트에서 로컬 DB를 중지·제거하되 볼륨은 보존한다.

```powershell
docker compose down
```

## 빌드와 통합 테스트

`backend/`에서 실행한다.

```powershell
.\gradlew.bat clean build --no-daemon
```

`build`는 테스트와 실행 가능한 bootJar 생성을 포함한다. 테스트만 실행하는 명령도 다음과 같다.

```powershell
.\gradlew.bat test --no-daemon
```

`PostgresIntegrationTest`는 `@ServiceConnection`으로 별도 PostgreSQL 컨테이너의 동적 포트·자격 증명을 주입한다. Compose DB를 먼저 실행할 필요가 없으며 로컬 DB 데이터도 사용하지 않는다. Docker가 없으면 테스트는 실패하며 자동으로 건너뛰지 않는다.

`PostgresIntegrationTest`는 기본 기동, 실제 PostgreSQL 연결, 최신 Flyway 버전 4 및 기존 데이터 업그레이드를 확인한다. V1은 초기 이력, V2는 공간, V3는 예약, V4는 활성 예약 배타 제약이다. V4는 btree_gist를 public에 설치할 권한이 필요하며 활성 중복 데이터가 있으면 실패한다. 기존 데이터를 자동 수정하지 않는다. 공간 API는 `SpaceApiIntegrationTest`, 예약 API와 동시성·교착 복구는 `ReservationApiIntegrationTest`가 별도 PostgreSQL에서 검증한다. 상세 결과는 [reservation-concurrency.md](reservation-concurrency.md)를 참고한다.

테스트 리포트는 `backend/build/reports/tests/test/index.html`, 실행 JAR는 `backend/build/libs/resource-booking-system-backend-0.0.1-SNAPSHOT.jar`에 생성된다.

예약 목록은 `ReservationListIntegrationTest`가 독립 PostgreSQL·HTTP로 검증한다. 계약과 실제 결과는 [reservation-list.md](reservation-list.md)를 따른다. 목록 테스트만 실행하려면 `backend/`에서 `.\gradlew.bat test --tests '*ReservationListIntegrationTest' --no-daemon`을 사용한다. 전체 `clean build`와 CI 기본 명령에도 해당 테스트가 포함된다.

## GitHub Actions CI

[Backend CI](../.github/workflows/backend-ci.yml)는 PR 생성·커밋 업데이트·재오픈·리뷰 준비 전환과 `main` push에서 실행한다. 초안 PR도 실행하며 경로 필터 없이 해당 리비전의 전체 백엔드 테스트를 검증한다.

Ubuntu 24.04 러너에서 Temurin Java 21과 저장소의 Gradle Wrapper를 사용한다. `setup-gradle`로 Wrapper 검증과 Gradle 캐시를 구성하며, 테스트 결과를 캐시하거나 테스트를 제외하지 않는다. 체크아웃 토큰 권한은 `contents: read`로 제한한다.

`backend/`에서 `docker info`가 성공한 다음 `./gradlew clean build --no-daemon`을 실행한다. Docker 연결 실패 시 작업이 실패하며 `continue-on-error`로 무시하지 않는다. 기존 `@Testcontainers`에도 Docker 부재 시 건너뛰는 설정이 없다. PostgreSQL 17.11 Alpine은 테스트가 직접 생성하므로 Compose나 Actions PostgreSQL 서비스는 필요 없다.

작업 제한 시간은 20분이다. 성공·실패 시 생성된 HTML/JUnit XML 리포트를 `backend-test-reports` 아티팩트로 7일 보존한다. 빌드 시작 전 실패하여 리포트가 없으면 업로드는 경고만 남기며, 앞 단계의 실패 결과는 유지한다.

구성 근거: [Java 설정](https://github.com/actions/setup-java), [Gradle 설정과 Wrapper 검증](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md), [Testcontainers 실행 환경](https://java.testcontainers.org/supported_docker_environment/).

### CI 구성 시 로컬 검증 (2026-10-29)

- 기준: `origin/main`의 `1c86ef7`에서 생성한 `chore/backend-ci`. 예약 API PR #4는 확인 당시 미병합이므로 이 브랜치에 포함되지 않는다. 향후 예약 변경이 병합되면 같은 `build` 명령이 추가 테스트도 실행한다.
- Java 21.0.7, Docker 엔진 29.9.2 연결 확인.
- `backend/`에서 `.\gradlew.bat clean build --no-daemon`: 49초, BUILD SUCCESSFUL. PostgreSQL 통합 테스트 47개(환경·마이그레이션 2개, 공간 API 45개), 실패·오류·건너뜀 0.
- 루트에서 `docker run --rm -v "${PWD}:/repo:ro" -w /repo rhysd/actionlint:1.7.7 -color .github/workflows/backend-ci.yml`: 종료 코드 0, 지적 없음.
- `git diff --check`: 통과.
- 최초 제한 환경에서는 Git fetch와 Docker 소켓 접근이 권한 오류로 차단되었다. 허용된 실행으로 fetch와 Docker 연결 및 전체 빌드가 성공했다. 애플리케이션 코드나 설정을 변경해 우회하지 않았다.
- GitHub Actions 실제 이벤트 실행과 Ubuntu에서의 `./gradlew` 실행은 미검증이다. Linux Wrapper의 Git 모드 `100755`는 확인했다. Docker 미설치 환경의 실패 재현은 별도로 실행하지 않았으며, 사전 확인과 테스트 설정에서 실패를 무시하지 않는 것을 점검했다.
- 커밋·push·PR 생성은 수행하지 않았다. 실제 원격 실행 결과와 필수 상태 검사 지정은 후속 확인 항목이다.

## Trivy 실행 JAR 취약점 검사

기존 `build-and-test`의 Docker 확인·Java 21·Wrapper·전체 테스트·테스트 리포트는 유지한다. 빌드 성공 후 실행 JAR `backend/build/libs/resource-booking-system-backend-0.0.1-SNAPSHOT.jar`만 `backend-executable-jar`로 1일 보관하고, `needs: build-and-test`인 별도 `backend-security` Job이 같은 실행의 아티팩트를 내려받아 검사한다. `*-plain.jar`는 의존성이 없어 대상에서 제외한다. 실행 JAR은 `BOOT-INF/classes/`와 `BOOT-INF/lib/`의 런타임 라이브러리를 포함한다.

Trivy v0.76.1 공식 이미지의 OCI index digest를 `ghcr.io/aquasecurity/trivy@sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c`로 고정했다. 도입 시 Cosign의 GitHub OIDC 발행 주체·서명·투명성 로그 검증을 성공했다. 이후 매 실행에서 태그를 다시 해석하거나 설치 스크립트를 다운로드하지 않는다. 이미지 갱신 시 공식 릴리스·보안 권고·서명을 확인한 뒤 digest와 검증 기록을 함께 갱신한다. digest·서명은 무해함을 단독 보장하지 않는다.

2026년 3월의 Trivy 바이너리·Action 태그·설치 Action 공급망 사고를 고려해 `trivy-action`과 동적 설치 경로는 사용하지 않는다. 새로 추가하는 아티팩트 업로드·다운로드, 체크아웃, SARIF 업로드 Action은 검토한 릴리스의 전체 커밋 SHA로 고정한다. 기존 빌드 Job의 Action 버전은 이번 범위에서 변경하지 않는다.

**JAR은 `fs`가 아닌 `rootfs` 모드로 분석한다.** v0.76.1의 fs 모드는 JAR 분석기를 비활성화하므로 파일을 입력해도 Java 패키지를 찾지 못한다. `rootfs --scanners vuln --pkg-types library`로 실행 JAR과 내부 JAR의 알려진 라이브러리 취약점만 분석한다. HIGH·CRITICAL만 보고하며 수정 버전이 없는 취약점도 숨기지 않는다. 테스트 전용 의존성·실행 JDK·OS·PostgreSQL 이미지·애플리케이션 로직 검사는 포함하지 않는다.

검사는 컨테이너에 JAR 디렉터리만 읽기 전용으로 연결하고, 결과 디렉터리만 쓰기 허용한다. 스캔·변환 컨테이너 모두 `--user "$(id -u):$(id -g)" --env HOME=/tmp`로 러너와 같은 UID/GID를 사용한다. 러너 소유 출력 폴더에 쓰고 임시 HOME에 캐시를 생성할 수 있게 하면서 root 권한에 의존하지 않는다. 호스트 Docker 소켓·GitHub 토큰·자격 증명·저장소 전체를 컨테이너에 전달하지 않는다. capability는 모두 제거하고 no-new-privileges를 적용한다. DB는 매 실행 최신 정보로 내려받으며 이번에는 실행 간 DB 캐시를 구성하지 않는다. 스캔 제한은 10분, 보안 Job 제한은 15분이다.

스캐너는 JSON과 패키지 정보를 먼저 생성한다. Python 표준 라이브러리로 Java 패키지 식별 여부와 심각도를 확인한 뒤, 같은 Trivy 이미지의 `convert`로 SARIF 2.1.0을 생성·점검한다. 취약점이 0건이어도 유효한 결과지만 패키지 미식별·잘못된 JSON/SARIF는 실패다. JSON·SARIF 결과는 성공·실패 시 `backend-security-reports`로 7일 보관하며 보관 파일 자체가 검사 성공을 의미하지 않는다.

`--exit-code 0`은 취약점 발견만으로 실패시키지 않는 정책이다. JAR 누락·아티팩트 전달 실패·스캐너/DB/변환 실행 오류·결과 검증 오류·Code Scanning 업로드 오류는 무시하지 않는다. `continue-on-error`나 `|| true`는 사용하지 않는다. 선행 빌드 실패 시 보안 Job이 생략되는 것은 정상 검사 완료가 아니다.

SARIF는 `github/codeql-action/upload-sarif`로 category `trivy-backend-jar`, `wait-for-processing: true`로 업로드한다. 기존 빌드는 `contents: read`를 유지하고 보안 Job에만 `contents: read`, `security-events: write`를 준다. 현재 공개 저장소 기준으로 PAT·issues/pull-requests 쓰기 권한은 필요 없다. Code Scanning에서 실행 JAR 경로로 보고되며 Java 소스 줄 단위 분석 결과는 아니다. 취약점 발견을 차단 조건으로 하는 별도 저장소 규칙이 있다면 초기 보고 전용 정책과 함께 검토해야 한다. GitHub Issue 자동 생성·AI 자동 수정은 구성하지 않는다.

공식 근거: [Java 지원](https://trivy.dev/docs/latest/coverage/language/java/), [지원 모드](https://trivy.dev/docs/latest/coverage/language/), [v0.76.1 분석기 선택 코드](https://github.com/aquasecurity/trivy/blob/v0.76.1/pkg/commands/artifact/run.go), [공급망 사고 권고](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23), [서명 검증](https://trivy.dev/docs/latest/getting-started/signature-verification/), [SARIF 업로드·권한](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/integrate-with-existing-tools/upload-sarif-file).

### Trivy 도입 검증 기록 (2026-10-29)

- `backend/`에서 `.\gradlew.bat clean build --no-daemon`: BUILD SUCCESSFUL, 1분 32초. 전체 155개(실제 PostgreSQL 143·단위 12), 실패·오류·건너뜀 0. 실행 JAR 생성, 기존 동시성·교착 복구 테스트 유지.
- 공식 Trivy 0.76.1 이미지 digest와 소스 revision e713f67 확인. 공식 Cosign v3.0.2 Windows 바이너리는 GitHub 릴리스 SHA256 `7a137280d8686665ceb4d8565df2a0ac63f28031e014cdcae5d56891a6c8a400`와 대조 후 사용했다. 위 Trivy digest의 서명·발행 주체·투명성 로그 검증 종료 코드 0. 별도 digest 고정 Cosign 컨테이너 경로에서도 검증 성공.
- 최초 `fs` 실험은 종료 코드 0이었지만 `num=0`, Results·패키지 정보 부재였다. 공식 분석기 선택 코드에서 fs가 JAR을 제외함을 확인해 rootfs로 수정했다. 이를 취약점 없음이나 검사 성공으로 기록하지 않는다.
- DB가 없는 임시 컨테이너에서 `--skip-db-update --exit-code 0` 실행은 종료 코드 1로 실패했다. 취약점 발견용 종료 정책이 실행 오류를 삼키지 않는 것을 확인했다.
- 워크플로의 Python 검증 코드를 직접 추출해 Java 패키지 미식별·잘못된 SARIF는 실패하고, HIGH 취약점이 있는 패키지 결과·취약점 0건의 유효한 SARIF는 통과하는 것을 확인했다.
- 수정한 고정 이미지의 `rootfs --scanners vuln --pkg-types library --severity HIGH,CRITICAL --exit-code 0 --timeout 10m --no-progress --format json --list-all-pkgs`로 새 실행 JAR을 검사했다. 로컬에서만 반복 검증용 DB 캐시를 `backend/build/trivy-cache/`에 연결했다. 최초 DB 다운로드 포함 약 6분 22초, 종료 코드 0. 런타임 라이브러리 61개를 식별하고 HIGH 6·CRITICAL 6, 고유 CVE 12개를 보고했다. 취약점 발견과 검사 실행 성공은 구분하며 의존성 수정·오탐/영향도 판정은 이번 변경에 포함하지 않았다.
- 같은 이미지의 `convert --format sarif --severity HIGH,CRITICAL`로 실제 SARIF 12건을 생성했고 워크플로에서 추출한 JSON·SARIF 검증 코드가 모두 통과했다. 결과 경로는 실행 JAR/BOOT-INF/lib 아래의 상대 가상 경로이며 ROOTPATH는 `file:///reports/trivy.json/`다. 실제 GitHub 표시·경로 처리는 원격 확인 항목이다.
- Trivy가 취약점 없는 입력을 변환한 실제 SARIF의 `results: []`도 검증을 통과했다. 잘못된 JSON의 convert는 종료 코드 1. 네트워크 없는 rootfs 컨테이너의 DB 다운로드와 누락 JAR의 `test -s` 사전 조건도 각각 종료 코드 1이었다.
- `docker run --rm -v "${PWD}:/repo:ro" -w /repo rhysd/actionlint:1.7.7 -color .github/workflows/backend-ci.yml`: 최종 워크플로 검사 통과. 로컬 Python에 PyYAML이 없어 보조 파싱은 실행하지 못했으며 대신 actionlint를 사용했다. 실제 Trivy 스캔·결과 검증은 별도로 실행했다.
- `requesting-code-review` Skill의 독립 리뷰어 Subagent가 기준 origin/main·HEAD `2841904`와 두 파일의 미커밋 변경을 검토했다. Critical·Important·Minor 지적 없음. 실제 패키지·SARIF·빈 결과 형식도 추가 확인 후 수용 가능 판정을 유지했다. 리뷰어가 빌드·스캐너를 재실행한 것은 아니다.
- 원격 PR/main 이벤트·아티팩트 전달·SARIF 실제 업로드/처리·권한 거부·Fork/Dependabot PR은 로컬 검증으로 대체할 수 없으며, push 전이므로 아직 실행하지 않았다.
- 로컬 보고서는 무시되는 `backend/build/trivy-reports/trivy.json`, `trivy.sarif`에 있다. 재현할 때는 워크플로의 스캔·검증·변환 명령을 기준으로 입력/출력 마운트 경로를 로컬 환경에 맞춘다. DB·보고서는 build 아래의 검증 산출물이며 저장소에 포함하지 않는다. JDK/OS/PostgreSQL 이미지·테스트 의존성은 후속 검사 범위이고, 발견된 12개 CVE의 영향도·수정 버전 검토 및 원격 CI 확인이 다음 작업이다.

### Linux 출력 폴더 권한 수정 (2026-10-29)

- PR #7의 [Backend CI run 40829677088](https://github.com/zz106603/resource-booking-system/actions/runs/40829677088)에서 빌드·전체 테스트·실행 JAR 업로드/다운로드는 성공했다. 보안 Job 123301204463은 DB 다운로드와 JAR 분석 후 `open /reports/trivy.json: permission denied`로 종료 코드 1이었다. SARIF 업로드는 생략됐다.
- 기본 root 실행이라도 `--cap-drop ALL`이면 다른 UID가 소유한 0755 폴더의 쓰기 권한을 우회할 수 없다. Windows bind mount 검증에서는 이 POSIX 소유권 차이를 재현하지 못했다. 임시 Linux 볼륨에 UID/GID 1001:1001·0755 폴더를 구성하고 기존 root/cap-drop Trivy convert에서 같은 permission denied·종료 코드 1을 재현했다.
- 스캔·변환을 모두 러너 UID/GID로 실행하도록 수정하고 HOME은 /tmp로 지정했다. chmod 777·capability 복구·오류 무시는 사용하지 않는다. 같은 Linux 볼륨에서 실제 JAR 스캔과 SARIF 변환이 종료 코드 0, 출력 JSON/SARIF 소유권 1001:1001·0644로 성공했다. 비root 임시 HOME의 캐시 폴더 생성·쓰기도 확인했다. 로컬 재검증에서 DB는 기존 캐시를 재사용했으며 CI의 DB 갱신 정책은 유지한다.
- actionlint와 `git diff --check`를 통과했다. 애플리케이션·의존성·테스트 코드는 변경하지 않아 전체 빌드·테스트는 재실행하지 않았다. 수정 전 원격 빌드·테스트 성공과 수정 후 로컬 보안 실행 검증을 구분한다.
- 수정 후 생성된 JSON의 패키지 61개와 SARIF 12건을 워크플로의 실제 Python 검증 코드로 확인했다. 독립 리뷰어 Subagent가 HEAD `201f01b` 대비 권한 수정 두 파일만 재검토했으며 Critical·Important·Minor 지적 없이 수용 가능 판정을 내렸다. 기존 PR 전체를 중복 리뷰하지 않았고 리뷰어가 재현 실험을 재실행한 것은 아니다.
- 수정 후 원격 CI 재실행 및 SARIF 실제 업로드·처리·권한 확인은 미검증이다. 이 수정은 커밋·push하지 않은 상태로 검증했다.

## 실제 검증 결과 (2026-10-27)

| 검증 | 결과 |
| --- | --- |
| `java -version` | Java 21.0.7 확인 |
| `docker info --format '{{.ServerVersion}}'` | Docker Desktop 엔진 29.9.2 연결 확인 |
| `docker compose config --quiet` | 통과 |
| `docker compose up -d --wait`, `docker compose ps` | 15432 포트로 healthy 확인 |
| `docker compose exec -T postgres psql -U resource_booking -d resource_booking -c 'select version();'` | PostgreSQL 17.11 응답 |
| `backend/`에서 `.\gradlew.bat clean build --no-daemon` | 성공, 약 65초. 통합 테스트 1개, 실패·오류·건너뜀 모두 0 |
| `backend/`에서 `.\gradlew.bat --version` | 갱신된 Wrapper로 Gradle 8.14.3·Java 21 확인 |
| `backend/`에서 `.\gradlew.bat test --rerun-tasks --no-daemon` | Compose 중지 후 재실행 성공, 약 28초. 독립 Testcontainers 테스트 1개, 실패·오류·건너뜀 모두 0 |
| `backend/`에서 `.\gradlew.bat bootRun --no-daemon` | Compose DB 연결, Flyway V1 적용, JPA 초기화 및 HTTP 8080 기동 확인 후 검증용 프로세스 종료 |
| `Invoke-WebRequest 'http://localhost:8080/' -SkipHttpErrorCheck` | 예상한 404 응답 |
| `docker compose down` | 검증용 컨테이너·네트워크 정리, 볼륨 보존 |

### 실패와 복구

- 최초 Docker 연결은 엔진 미기동으로 실패했다. 첫 시작 시도도 종료 오류가 있었으나 `docker desktop start` 재실행 후 엔진 연결이 성공했다.
- 최초 Compose 실행은 127.0.0.1:5432 포트 공개 시 Windows 소켓 오류로 실패했다. 리스너와 예약 포트 조회로 점유 근거는 확인되지 않았다. `POSTGRES_PORT=15432`로 실행해 정상 기동을 확인했고 Compose·JDBC 기본값을 함께 변경했다. 5432의 구체적인 환경 원인은 확정하지 않았다.
- 테스트를 먼저 작성한 상태의 `.\gradlew.bat test --no-daemon`은 `@SpringBootConfiguration` 부재로 1개 실패했다. 최소 기동 클래스와 설정 추가 후 위 `clean build`에서 같은 테스트가 통과했다.
- 제한된 실행 환경의 최초 Maven Central 조회는 DNS 접근 오류로 실패했다. 네트워크 접근이 허용된 실행으로 버전과 의존성을 확인했다.
- 테스트 JVM에서 클래스 공유 관련 경고가 출력되었으나 테스트 실패는 없었다.
- Windows에서 Wrapper를 자기 갱신하는 실행 직후 일시적인 CMD 구문 메시지가 출력되었다. Wrapper 태스크와 후속 의존성 조회는 성공했고, 갱신된 Wrapper를 별도로 실행한 `--version` 및 테스트 재실행에서도 정상 동작을 확인했다. 정확한 메시지 원인은 확정하지 않았다.
