---
name: spring-boot-docker-build
description: Java 21 및 Spring Boot 환경에 최적화된 멀티스테이지(Multi-stage) Dockerfile을 작성하여, 빌드 속도를 높이고 최종 이미지 크기와 보안 취약점을 최소화합니다.
argument-hint: "[Java 버전, 빌드 도구(Gradle/Maven), 노출할 포트 (예: Java 21과 Gradle을 사용하는 멀티스테이지 Dockerfile을 작성하고 8080 포트를 열어)]"
source: "custom"
tags: ["docker", "spring-boot", "java21", "devops", "deployment", "infrastructure"]
triggers:
  - "Dockerfile 작성"
  - "도커 빌드"
---

# Spring Boot Optimized Dockerfile (Multi-stage)

단일 `Dockerfile` 내에서 소스 코드 컴파일(Build)과 실행(Run) 환경을 완벽하게 분리하여, 운영 환경에 불필요한 소스 코드나 빌드 도구를 남기지 않고 초경량 컨테이너를 생성하는 지침입니다.

## Overview

- **Stage 1 (Builder):** 무거운 JDK(Java Development Kit)가 설치된 이미지를 사용하여 의존성을 다운로드하고 애플리케이션을 빌드(`.jar` 생성)합니다.
- **Stage 2 (Runtime):** 가벼운 JRE(Java Runtime Environment) 이미지를 베이스로 사용하며, Builder 단계에서 완성된 `.jar` 파일만 복사해 와서 실행합니다.
- 변경이 잦은 소스 코드(`src/`)보다 변경이 적은 설정 파일(`build.gradle`)을 먼저 복사하여 **도커 레이어 캐시(Layer Cache)**를 극대화합니다.

## When to Use This Skill

- 완성된 Spring Boot 애플리케이션을 AWS ECS, EKS(Kubernetes), 또는 로컬 `docker-compose` 환경에 배포하기 위해 컨테이너 이미지로 패키징할 때.
- 기존 도커 이미지의 크기가 너무 커서 배포 속도가 느리거나 디스크 용량을 많이 차지할 때 (최적화 목적).
- CI/CD 파이프라인(GitHub Actions, Jenkins)에서 컨테이너 빌드 자동화를 구성할 때.

## How It Works

### Step 1: Create `.dockerignore`

빌드 컨텍스트(Build Context)에 불필요한 파일이 포함되어 빌드 속도가 느려지는 것을 방지하기 위해 프로젝트 루트에 `.dockerignore` 파일을 생성합니다.
- 포함할 내용: `.git`, `.gradle`, `build/`, `out/`, `*.md`

### Step 2: Write Stage 1 (Build)

`eclipse-temurin:21-jdk-alpine` 과 같은 경량 JDK 베이스 이미지를 사용합니다.
- 소스 코드 전체를 복사하기 전에, `build.gradle`, `settings.gradle`, `gradlew` 파일만 먼저 복사하여 의존성을 다운로드(`.gradlew dependencies`)합니다. 이 레이어는 `build.gradle`이 변경되지 않는 한 캐시되어 빌드 시간을 대폭 단축시킵니다.
- 이후 소스 코드를 복사하고 `bootJar` 태스크로 빌드합니다.
- **주의:** Spring Boot 2.5+ 부터 생성되는 `-plain.jar` 파일을 삭제하여 2단계에서 복사 시 충돌이 발생하지 않도록 합니다.

### Step 3: Write Stage 2 (Run)

`eclipse-temurin:21-jre-alpine` 과 같은 JRE 전용 베이스 이미지를 사용합니다.
- **보안 설정:** 컨테이너 내부가 털리더라도 호스트 시스템에 대한 루트 권한을 얻지 못하도록 `addgroup`과 `adduser`로 권한이 제한된 Non-root 유저를 생성하여 실행합니다.
- 1단계(`builder`)에서 생성된 단일 `.jar` 파일을 복사하고, 메모리 최적화 플래그가 포함된 `ENTRYPOINT`를 통해 애플리케이션을 실행합니다.

## Examples

```dockerfile
# ==========================================
# Stage 1: Build Stage
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# 1. Gradle 래퍼와 설정 파일만 먼저 복사 (레이어 캐싱 극대화)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# 2. 실행 권한 부여 및 의존성 다운로드 (캐시 활용)
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 3. 실제 소스 코드 복사 및 빌드 진행
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

# 4. Spring Boot 2.5+에서 생성되는 불필요한 plain.jar 삭제 (COPY 충돌 방지용)
RUN rm -f build/libs/*-plain.jar

# ==========================================
# Stage 2: Runtime Stage
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 1. 보안 설정: Root 권한을 가지지 않은 제한된 유저(spring) 생성 및 전환
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 2. Builder 스테이지에서 빌드된 실행 가능한 jar 파일만 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# 3. 포트 노출 (명세 역할)
EXPOSE 8080

# 4. 타임존 설정 및 애플리케이션 실행
ENV TZ=Asia/Seoul

# MaxRAMPercentage=75.0: 컨테이너에 할당된 메모리의 75%만 Heap으로 사용하여 OOMKilled 방지
# /dev/urandom: 톰캣 시작 시 난수 생성 지연 방지
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]

```

## Best Practices

* **컨테이너 메모리 플래그 (`-XX:MaxRAMPercentage`):** 쿠버네티스나 도커 환경에서는 반드시 `-Xmx` 같은 절대값 대신 비율 기반의 설정을 사용하여, 컨테이너 메모리 제한(Limit) 변경에 JVM이 유연하게 대응하도록 해야 합니다.
* **Gradle Wrapper(`gradlew`) 사용:** 호스트 머신이나 CI/CD 서버에 Gradle이 설치되어 있지 않거나 버전이 달라도, 프로젝트에 종속된 동일한 환경으로 빌드를 보장할 수 있도록 반드시 `gradlew`를 사용하십시오.

## Common Pitfalls

* **소스 코드 통째로 복사 (`COPY . .`) (Critical):** 상단에서 `COPY . .`를 수행해 버리면, 소스 코드가 단 한 줄만 바뀌어도 도커 캐시가 깨져버려 매번 수백 MB의 라이브러리를 다시 다운로드하게 됩니다. 반드시 의존성 파일과 소스 코드 복사를 분리해야 합니다.
* **Plain Jar 충돌 (Critical):** `COPY --from=builder .../*.jar` 실행 시, `*-plain.jar`가 남아있으면 "복수의 파일이 매칭되었다"며 빌드가 실패합니다. 반드시 빌드 단계에서 plain jar를 제거하거나 명시적인 파일명을 지정하십시오.
* **Root 계정 실행 (Critical):** 컨테이너 런타임에 취약점이 발생할 경우 호스트 OS까지 위험해질 수 있으므로, 반드시 명시적으로 Non-root 유저를 생성하여 `USER` 명령어로 전환하십시오.
* **Alpine 베이스 이미지의 한계 (musl libc):** Alpine은 용량이 작아 좋지만, C언어 기반 네이티브 라이브러리(이미지 리사이징, 특정 DB 드라이버 등)를 사용할 경우 `glibc` 부재로 인해 런타임 에러가 발생할 수 있습니다. 이 경우 `eclipse-temurin:21-jre-jammy` (Ubuntu 기반)로 변경해야 합니다.

## Related Skills

* `spring-rest-api`: 이 도커 컨테이너 내부에서 실행될 웹 애플리케이션의 핵심 비즈니스 로직 설계 지침입니다.
* `database-erd-design`: 컨테이너가 띄워진 후 통신하게 될 외부 데이터베이스 인프라 구조를 파악할 때 참조합니다.
