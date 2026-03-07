---
name: spring-restclient-api
description: Spring Boot 3.2 이상 환경에서 최신 RestClient를 사용하여 외부 API와 통신하고, 타임아웃 및 전역 에러 처리를 구성합니다.
argument-hint: "[호출할 외부 API 주소, HTTP 메서드, 헤더/바디 포맷, 응답 매핑 객체 (예: 카카오 결제 준비 API POST 호출 로직과 타임아웃 설정 작성)]"
source: "custom"
tags: ["java", "spring-boot", "restclient", "api", "http", "backend", "java21"]
triggers:
  - "외부 API 호출"
  - "RestClient 설정"
---

# Spring Boot RestClient API Integration

Spring Boot 3.2부터 공식 지원되는 `RestClient`를 활용하여, 직관적이고 안전하게 외부 시스템(결제, 소셜 로그인, 공공 데이터 등)과 HTTP 통신을 수행하는 지침입니다.

## Overview

- 무겁고 가독성이 떨어지던 기존 `RestTemplate`을 완전히 대체하며, `WebClient` 수준의 세련된 Fluent API(메서드 체이닝)를 제공합니다.
- WebFlux 의존성 없이 동기(Synchronous) 방식으로 동작하며, **Java 21의 가상 스레드(Virtual Threads)와 결합할 때 넌블로킹(Non-blocking) 수준의 엄청난 처리량**을 보여줍니다.
- 전역 타임아웃(Timeout) 설정과 HTTP 상태 코드별 에러 핸들링(`defaultStatusHandler`)을 기본적으로 구성하여 서버의 안정성을 보장합니다.

## When to Use This Skill

- 소셜 로그인(OAuth2) 과정에서 토큰을 검증하거나 유저 정보를 가져오기 위해 카카오/구글 서버를 호출할 때.
- PG사(결제 대행사) API와 통신하여 결제 승인/취소 요청을 보낼 때.
- 공공 데이터 포털이나 외부 마이크로서비스(MSA) 서버에서 JSON 데이터를 가져와 우리 서버의 DTO로 변환해야 할 때.

## How It Works

### Step 1: Configure `RestClient` Bean

`@Configuration` 클래스에서 `RestClient`를 빈으로 등록합니다.
- **자동 주입 활용:** `RestClient.builder()`를 직접 호출하는 대신, Spring Boot가 관측성(Observability) 등을 미리 설정해 둔 `RestClient.Builder`를 파라미터로 주입받아 사용하십시오.
- 타임아웃을 설정하기 위해 Java 11부터 내장된 `JdkClientHttpRequestFactory`를 사용합니다. (외부 라이브러리 불필요)
- `baseUrl`, `defaultHeader` 등 공통 설정을 적용합니다.
- `defaultStatusHandler`를 통해 4xx, 5xx 에러 발생 시 공통 예외 처리 로직을 정의합니다.

### Step 2: Implement API Call Logic

서비스 계층에서 주입받은 `RestClient`를 사용하여 통신합니다.
- `.get()`, `.post()`, `.put()`, `.delete()` 메서드로 시작합니다.
- `.uri()`로 엔드포인트와 쿼리 파라미터를 설정합니다.
- POST/PUT의 경우 `.body()`에 객체를 넣으면 자동으로 JSON 직렬화가 수행됩니다.
- `.retrieve().body(Class)`를 통해 응답 JSON을 DTO로 바로 역직렬화합니다.

## Examples

```java
// 1. RestClient Configuration
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient customRestClient(RestClient.Builder builder) {
        // Java 11+ 내장 HTTP 클라이언트 팩토리 사용 (가상 스레드와 호환성 우수)
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));    // 읽기 타임아웃 5초
        
        // 중요: Connection Timeout 설정을 위해 커스텀 HttpClient 필요 시 아래 방식 활용 고려
        // requestFactory.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());

        // Spring Boot가 제공하는 builder를 사용하여 관측성 메트릭 등 기본 설정 유지
        return builder
                .requestFactory(requestFactory)
                // .baseUrl("[https://api.external-service.com](https://api.external-service.com)") // 도메인이 고정된 경우 설정
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new IllegalArgumentException("외부 API 클라이언트 에러: " + response.getStatusCode());
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new IllegalStateException("외부 API 서버 에러: " + response.getStatusCode());
                })
                .build();
    }
}

// 2. Service Layer Implementation
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestClient restClient;

    // GET 요청 (Query Parameter 포함 및 404 에러 핸들링 예시)
    public ExternalDataDto fetchExternalData(String queryId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.example.com")
                        .path("/v1/data")
                        .queryParam("id", queryId)
                        .build())
                .retrieve()
                // 404 발생 시 전역 핸들러를 무시하고 예외를 삼킨 뒤 null 반환
                .onStatus(HttpStatusCode::is404, (request, response) -> { /* do nothing */ })
                .body(ExternalDataDto.class);
    }

    // POST 요청 (Body 포함) 및 제네릭 타입 반환
    public List<ResultDto> sendData(RequestPayload payload) {
        return restClient.post()
                .uri("[https://api.example.com/v1/submit](https://api.example.com/v1/submit)")
                .header("Authorization", "Bearer your-token-here")
                .body(payload)
                .retrieve()
                // List나 제네릭 타입으로 역직렬화할 때는 ParameterizedTypeReference 사용
                .body(new ParameterizedTypeReference<List<ResultDto>>() {});
    }
}

```

## Best Practices

* **`ParameterizedTypeReference` 사용:** 외부 API 응답이 `List<Dto>`나 `ApiResponse<Dto>` 같은 제네릭(Generic) 타입일 경우, 단순 `.body(List.class)`로는 내부 타입 소거(Type Erasure)가 발생하여 `LinkedHashMap`으로 매핑됩니다. 반드시 `new ParameterizedTypeReference<List<Dto>>() {}`를 사용하여 타입 안정성을 확보하십시오.
* **인터페이스 기반 `HttpExchange`:** 호출해야 할 API 엔드포인트가 수십 개라면, `RestClient`를 직접 호출하기보다 Spring Boot 3의 `@HttpExchange` 인터페이스를 선언하여 선언적으로(Declarative) 사용하는 고급 기법을 고려하십시오.

## Common Pitfalls

* **타임아웃 미설정 (Critical):** `RestClient` 생성 시 타임아웃을 명시하지 않으면, 무한 대기(Infinite Block) 상태에 빠질 수 있습니다. 외부 서버가 장애로 응답을 주지 않으면, 우리 서버의 스레드도 모두 고갈되어 연쇄 장애(Cascading Failure)가 발생합니다. 반드시 `ReadTimeout`과 `ConnectTimeout`을 설정해야 합니다.
* **4xx, 5xx 응답 시 무조건 예외 발생:** `.retrieve()`는 HTTP 상태 코드가 4xx나 5xx일 때 기본적으로 `RestClientException`을 던집니다. 외부 API의 404 응답을 '예외'가 아니라 '정상적인 비즈니스 흐름(데이터 없음)'으로 처리하고 싶다면, 예제처럼 `onStatus(HttpStatusCode::is404, ...)` 핸들러를 추가하여 예외를 먹어(swallow) 치우고 유연하게 대처해야 합니다.
* **`WebClient`와의 혼동:** `RestClient`는 동기(Sync) 방식입니다. WebFlux 의존성을 추가하면서까지 단순 통신을 위해 비동기 `WebClient`를 사용할 필요는 더 이상 없습니다.

## Related Skills

* `spring-rest-api-exception-handler`: 외부 API 통신 실패 시(`defaultStatusHandler`에서 던진 예외) 클라이언트에게 일관된 `ProblemDetail` 스펙으로 에러를 응답할 때 참조합니다.
