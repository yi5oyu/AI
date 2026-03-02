---
name: database-h2-test-env
description: Configure a fast, independent H2 in-memory database for Spring Boot integration tests and local development environments.
argument-hint: "[프로젝트의 운영 DB 종류 (예: MySQL, PostgreSQL) 및 설정할 프로파일 (예: test, local)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "h2", "database", "configuration"]
triggers:
  - "H2 설정"
  - "테스트 DB 세팅"
  - "application-test.yml 작성"
---

# Spring Boot H2 Test Environment Setup

Guidelines for configuring an H2 in-memory database for integration tests while maintaining compatibility with the production database.

## Overview

- Maximizes test execution speed by setting up an H2 database that operates entirely in memory without disk I/O.
- Configures `application-test.yml` to perfectly isolate the test environment from the production DB configuration.
- Utilizes H2's compatibility mode (`MODE=...`) to operate as similarly as possible to the target production database (MySQL, PostgreSQL, etc.) syntax.

## When to Use This Skill

- When setting up a dedicated profile (`test`) and DB connection configuration for integration tests at the beginning of a project.
- When you want to temporarily run and test the application in a local environment (`local`) without spinning up the actual production DB.
- When encountering "Table not found" errors, DB connection closed errors, or reserved keyword conflicts during test execution and need to tune H2 settings.

## How It Works

### Step 1: Add Dependencies

Add the H2 database dependency to `build.gradle` (or `pom.xml`).
- If used only in the test environment, you MUST restrict the scope to `testImplementation` or `testRuntimeOnly` so it is not included in the production build.

### Step 2: Configure application-test.yml

Create the `src/test/resources/application-test.yml` file and define the DataSource and JPA properties.
- **URL Configuration:** Use `jdbc:h2:mem:testdb` as the base, but you MUST add the `DB_CLOSE_DELAY=-1` option to prevent the DB from resetting while the Spring test context remains alive.
- **Compatibility Mode:** If the production DB is MySQL, append `;MODE=MySQL` to the URL to match syntax compatibility.
- **Auto DDL Generation:** Since tests always require a clean schema, specify `spring.jpa.hibernate.ddl-auto: create-drop` or `create`.

### Step 3: Enable H2 Console (Optional for Local)

If using H2 in the `local` profile, enable the H2 console to view DB contents in a web browser. (It is recommended to disable this for automated `test` profiles as it is unnecessary).



## Examples

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    # DB_CLOSE_DELAY=-1: Keeps DB in memory as long as the Spring context is alive
    # MODE=MySQL: Uses syntax compatible with the production environment
    # NON_KEYWORDS=USER: Prevents reserved keyword conflicts in H2 2.x
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER
    driver-class-name: org.h2.Driver
    username: sa
    password: ""

  jpa:
    # Optional in Spring Boot 3.x (Hibernate 6) due to auto-detection, 
    # but if explicitly set, MUST remain H2Dialect
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop # Creates schema on test start, drops on test end
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
  h2:
    console:
      enabled: false # Unnecessary in CI/CD and automated test environments

```

## Best Practices

* **Dialect Configuration Caution:** Even if `MODE` is specified in the URL, if explicitly configuring the JPA dialect (`database-platform`), you must maintain `org.hibernate.dialect.H2Dialect` (not the production DB dialect) so Hibernate safely generates H2-compatible queries. (In Spring Boot 3.x+, it is auto-detected if the property is omitted).
* **Prevent Production Code Pollution:** Strictly separate H2-specific configurations or test dummy data generation logic so they NEVER intrude into the production code under `src/main/`.

## Common Pitfalls

* **H2 2.x Reserved Keywords (Critical):** From H2 version 2.0+, words like `USER`, `YEAR`, and `VALUE` are strictly reserved. If an entity is named `User`, a syntax error will occur when H2 starts. Add `;NON_KEYWORDS=USER` to the URL, or preferably use plural table names like `@Table(name = "users")`.
* **Missing DB_CLOSE_DELAY (Critical):** If omitted, the connection drops when integration tests span multiple classes, evaporating the in-memory DB and causing subsequent tests to fail with a `Table not found` exception.
* **Native Query Compatibility Issues:** H2 compatibility modes like `MODE=MySQL` are not perfect. Using specific DB-exclusive functions (e.g., MySQL's `GROUP_CONCAT`) or complex native queries (`@Query(nativeQuery = true)`) will cause syntax errors in H2. Use JPA abstractions like QueryDSL whenever possible.

## Related Skills

* `spring-api-integration-test`: Use to write integration tests validating the actual request flow from controller to DB based on this H2 environment.
* `database-erd-design`: Reference to preemptively understand the entity schema structure and table names (checking for reserved words) to be tested.
