---
name: database-h2-test-env
description: Spring Boot 통합 테스트 및 로컬 개발 환경을 위한 빠르고 독립적인 H2 인메모리 데이터베이스를 설정합니다.
argument-hint: "[프로젝트의 운영 DB 종류 (예: MySQL, PostgreSQL) 및 설정할 프로파일 (예: test, local)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "h2", "database", "configuration"]
triggers:
  - "H2 설정"
  - "테스트 DB 세팅"
  - "application-test.yml 작성"
---

# Spring Boot H2 Test Environment Setup

통합 테스트를 위한 H2 인메모리 데이터베이스 설정 및 프로덕션 DB 호환성 유지 지침입니다.

## Overview

- 디스크 I/O 없이 메모리에서만 동작하는 H2 데이터베이스를 설정하여 테스트 실행 속도를 극대화합니다.
- `application-test.yml`을 구성하여 운영 환경(Production)의 DB 설정과 테스트 환경을 완벽하게 분리합니다.
- H2의 호환성 모드(`MODE=...`)를 활용하여 실제 타겟 데이터베이스(MySQL, PostgreSQL 등)의 문법과 최대한 유사하게 동작하도록 구성합니다.

## When to Use This Skill

- 프로젝트 초기에 통합 테스트를 위한 전용 프로파일(`test`)과 DB 연결 설정을 구축해야 할 때.
- 로컬 환경(`local`)에서 개발 중 실제 DB를 띄우지 않고 임시로 애플리케이션을 구동하여 테스트하고 싶을 때.
- 테스트 실행 중 "Table not found" 에러나 DB 커넥션 종료, 예약어 충돌 에러가 발생하여 H2 설정을 튜닝해야 할 때.

## How It Works

### Step 1: Add Dependencies

`build.gradle` (또는 `pom.xml`)에 H2 데이터베이스 의존성을 추가합니다.
- 테스트 환경에서만 사용할 경우 반드시 `testImplementation` 또는 `testRuntimeOnly`로 스코프를 제한하여 프로덕션 빌드에 포함되지 않도록 하십시오.

### Step 2: Configure application-test.yml

`src/test/resources/application-test.yml` 파일을 생성하고 데이터소스(DataSource)와 JPA 속성을 정의합니다.
- **URL 설정:** `jdbc:h2:mem:testdb`를 기본으로 사용하되, 테스트 컨텍스트가 유지되는 동안 DB가 초기화되는 것을 막기 위해 `DB_CLOSE_DELAY=-1` 옵션을 반드시 추가합니다.
- **호환성 모드:** 운영 DB가 MySQL이라면 URL 끝에 `;MODE=MySQL`을 추가하여 문법 호환성을 맞춥니다.
- **DDL 자동 생성:** 테스트 시에는 항상 깨끗한 스키마가 필요하므로 `spring.jpa.hibernate.ddl-auto: create-drop` 또는 `create`를 지정합니다.

### Step 3: Enable H2 Console (Optional for Local)

만약 `local` 프로파일에서 H2를 사용한다면 웹 브라우저에서 DB 내용을 확인할 수 있도록 H2 콘솔을 활성화합니다. (단, 자동화된 `test` 프로파일에서는 불필요하므로 비활성화하는 것이 좋습니다.)

## Examples

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    # DB_CLOSE_DELAY=-1: Spring 컨텍스트가 살아있는 동안 DB 메모리 유지
    # MODE=MySQL: 운영 환경과 동일한 문법 사용
    # NON_KEYWORDS=USER: H2 2.x 예약어 충돌 방지
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER
    driver-class-name: org.h2.Driver
    username: sa
    password: ""

  jpa:
    # Spring Boot 3.x(Hibernate 6)에서는 생략 가능하나, 명시적 설정 시 H2Dialect 유지
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop # 테스트 시작 시 스키마 생성, 종료 시 드롭
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
  h2:
    console:
      enabled: false # CI/CD 및 자동화된 테스트 환경에서는 불필요

```

## Best Practices

* **방언(Dialect) 설정 주의:** URL에 `MODE`를 지정했더라도, JPA 방언 설정(`database-platform`)을 명시할 때는 실제 운영 DB 방언이 아닌 `org.hibernate.dialect.H2Dialect`를 유지해야 Hibernate가 H2에 맞는 쿼리를 안전하게 생성합니다. (Spring Boot 3.x 이상에서는 속성을 생략하면 자동 감지됩니다.)
* **프로덕션 코드 오염 방지:** H2 전용 설정이나 테스트용 더미 데이터 생성 로직이 `src/main/` 하위의 프로덕션 코드에 절대 침범하지 않도록 철저히 분리하십시오.

## Common Pitfalls

* **H2 2.x 예약어 충돌 (Critical):** H2 버전 2.0 이상부터 `USER`, `YEAR`, `VALUE` 등이 엄격한 예약어로 지정되었습니다. 엔티티 이름이 `User`일 경우 H2 구동 시 문법 에러가 발생합니다. URL에 `;NON_KEYWORDS=USER`를 추가하거나, 가급적 `@Table(name = "users")`처럼 복수형 테이블 명을 사용하십시오.
* **DB_CLOSE_DELAY 누락 (Critical):** 이 옵션을 빼먹으면, 통합 테스트가 여러 클래스에 걸쳐 실행될 때 커넥션이 끊어지면서 메모리 DB가 증발하여 이후 테스트가 전부 실패(`Table not found`)하는 현상이 발생합니다.
* **Native Query 호환성 문제:** H2의 `MODE=MySQL` 등의 호환성 모드는 완벽하지 않습니다. 특정 DB 전용 함수(예: MySQL의 `GROUP_CONCAT`)나 복잡한 Native Query(`@Query(nativeQuery = true)`)를 사용하면 H2에서 문법 에러가 발생합니다. 가급적 QueryDSL 등 JPA 추상화를 사용하십시오.

## Related Skills

* `spring-api-integration-test`: 이 H2 환경을 바탕으로 컨트롤러부터 DB까지 실제 요청 흐름을 검증하는 통합 테스트를 작성할 때 사용합니다.
* `database-erd-design`: 테스트할 엔티티 스키마 구조와 테이블 명(예약어 확인)을 미리 파악할 때 참고합니다.
