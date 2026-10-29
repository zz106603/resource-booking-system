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
