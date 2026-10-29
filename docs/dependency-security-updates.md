# 런타임 의존성 보안 패치

## 범위와 메이저 업그레이드 판단 (2026-10-29)

PR #7의 실제 실행 JAR 검사에서 확인한 HIGH 6·CRITICAL 6, 고유 CVE 12개를 기준으로 한다. Boot 플러그인과 기본 BOM은 3.5.16을 유지한다. Maven Central의 공개 메타데이터에서 3.5.16이 최신 3.5 패치이며 Spring Framework 6.2의 공개 최신은 6.2.19임을 확인했다.

| 계열 | 기존 | 적용할 패치 | 판단 |
| --- | --- | --- | --- |
| Jackson BOM | 2.21.4 | 2.21.8 | 전체 Jackson 모듈을 같은 BOM으로 정렬 |
| Tomcat embed core/el/websocket | 10.1.55 | 10.1.61 | 같은 10.1 계열의 3개 모듈을 함께 제약 |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 | 같은 42.7 계열 패치 |
| Spring Framework | 6.2.19 | 유지 | 공개 저장소에서 6.2.20을 사용할 수 없으며 7.x를 개별 주입하지 않음 |

Spring CVE-2026-47884(XsltView), CVE-2026-47890(SSE view fragments)의 공식 수정 버전은 OSS 7.0.9 또는 상용 6.2.20이다. 공개 의존성만으로 해소하려면 Boot 4·Framework 7의 메이저 전환을 별도로 계획하고 Jackson·JUnit 등 연관 전환도 검토해야 한다. 상용 지원 도입은 이번 범위가 아니다. 현재 JSON REST API에는 해당 뷰 기능이 없지만 영향 버전을 포함한 사실과 미해결 상태를 유지하며 Trivy 결과를 숨기지 않는다.

Gradle의 native platform을 사용하므로 Boot BOM 속성을 ext로 선언하는 방식으로는 관리 버전을 바꿀 수 없다. 별도 Jackson BOM과 Tomcat 모듈/pgJDBC constraints로 필요한 패치만 명시한다. Boot BOM의 기본값 그대로인 조합은 아니며, 공식적으로 검증된 전체 조합이라고 단정하지 않는다. 같은 계열이라는 근거와 실제 runtimeClasspath·실행 JAR·기존 전체 테스트 결과로 이 프로젝트의 호환성을 확인한다.

전체 `build --rerun-tasks --no-daemon`으로 기존 155개 테스트를 다시 실행하고 새 실행 JAR을 생성한다. 이 명령은 모든 태스크를 재실행하며 로컬 Trivy DB 캐시를 유지하기 위해 clean은 사용하지 않는다. Trivy rootfs 모드로 새 JAR을 재검사하고 이전 CVE ID의 해소·잔존 및 새 발견을 구분한다. 독립 리뷰어는 기준 HEAD와 실제 미커밋 변경을 검토한다. 커밋·push는 하지 않는다.

공식 근거: [Boot Gradle 의존성 관리](https://docs.spring.io/spring-boot/3.5/gradle-plugin/managing-dependencies.html), [Spring XSLT 권고](https://spring.io/security/cve-2026-47884/), [Spring SSE 권고](https://spring.io/security/cve-2026-47890/), [Tomcat 권고](https://tomcat.apache.org/security-10.html), [pgJDBC 권고](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-j92g-9f8w-j867).

## 실제 검증 결과

- `backend/`에서 `.\gradlew.bat dependencies --configuration runtimeClasspath --no-daemon`으로 새 패치를 선택했음을 확인했다. Jackson core/databind/dataformat/datatype/module은 2.21.8이며 annotations는 해당 Jackson BOM이 지정한 2.21이다. Tomcat embed 3개는 모두 10.1.61, JDBC는 42.7.14, Spring MVC는 6.2.19다. 실행 JAR의 BOOT-INF/lib에서도 동일 버전을 직접 확인했다.
- 첫 `.\gradlew.bat build --rerun-tasks --no-daemon`은 Tomcat JAR 다운로드 중 Maven Central DNS 오류로 compileJava에서 실패했다(19초). DNS 정상 응답과 공식 JAR URL의 HTTP 200을 확인한 뒤 설정·버전 변경 없이 새 Gradle 프로세스로 재실행했다.
- 재실행은 1분 38초, BUILD SUCCESSFUL. 전체 155개(환경·마이그레이션 5, 공간 45, 기존 예약 48, 목록 45, 단위 12), 실패·오류·건너뜀 0이다. 동시성·교착 복구·마이그레이션·기존 JSON 입력 정책을 포함한다.
- 새 실행 JAR을 기존 digest 고정 Trivy v0.76.1 rootfs로 재검사했다. 비root UID/GID 1001:1001, HOME=/tmp, capability 제거와 JAR 읽기 전용 마운트를 유지했다. 기존 로컬 DB 캐시를 사용하되 skip-db-update는 지정하지 않았고, HIGH/CRITICAL만 검사했다. Java 패키지 62개를 식별했고 스캔·SARIF 변환 모두 종료 코드 0이었다.
- 패키지 수의 61→62 증가는 pgJDBC 패치의 전이 의존성 checker-qual 추가로 확인했다. 새 JSON·SARIF도 기존 워크플로의 Python 검증 코드로 통과했다.

| 계열 | 이전 HIGH/CRITICAL | 재검사 | 결과 |
| --- | --- | --- | --- |
| Jackson | HIGH 5 | 0 | 기존 5개 CVE 해소 |
| Tomcat | CRITICAL 4 | 0 | 기존 4개 CVE 해소 |
| PostgreSQL JDBC | HIGH 1 | 0 | CVE-2026-54291 해소 |
| Spring MVC | CRITICAL 2 | CRITICAL 2 | CVE-2026-47884·CVE-2026-47890 잔존 |

새 HIGH/CRITICAL CVE는 발견되지 않았다. 이는 이 DB·필터·JAR의 검사 결과이며 모든 심각도·모든 취약점이 해소됐다는 뜻은 아니다. 잔여 Spring 두 항목은 버전 기준 실제 발견이며 억제·무시 설정을 추가하지 않았다. 현재 사용 경로의 XSLT/SSE 조건은 없지만 공격 재현은 수행하지 않았다.

실행 증거는 무시되는 `backend/build/runtime-classpath-security.txt`, `backend/build/test-results/test/`, `backend/build/trivy-security-update/trivy.json`, `trivy.sarif`에 남겼다. 원격 CI와 새 의존성 조합의 원격 Code Scanning 업로드는 미실행이다. 메이저 전환·상용 지원·잔여 취약점 수용 정책은 후속 결정 사항이다.

## 독립 리뷰와 변경 범위

`requesting-code-review` Skill의 독립 리뷰어 Subagent가 기준 HEAD `dc1e7f8` 대비 build.gradle·실행 문서의 미커밋 변경과 이 신규 문서를 검토했다. Critical·Important·Minor 지적 없이 승인 가능 판정이다. 리뷰어는 실제 의존성 보고서·JAR·JUnit XML·Trivy 전후 결과를 직접 확인했으나 빌드·스캐너 명령과 공식 메타데이터 조회를 재실행하지 않았다. 중요한 코드 수정이 없어 중복 리뷰는 수행하지 않았다.

문서 링크와 `git diff --check`를 통과했다. 애플리케이션·테스트 로직·CI·스키마·AGENTS.md는 변경하지 않았다. 커밋·push는 수행하지 않았다.
