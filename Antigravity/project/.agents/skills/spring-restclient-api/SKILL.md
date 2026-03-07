---
name: spring-restclient-api
description: Communicate with external APIs and configure timeouts and global error handling using the modern RestClient in a Spring Boot 3.2+ environment.
argument-hint: "[호출할 외부 API 주소, HTTP 메서드, 헤더/바디 포맷, 응답 매핑 객체 (예: 카카오 결제 준비 API POST 호출 로직과 타임아웃 설정 작성)]"
source: "custom"
tags: ["java", "spring-boot", "restclient", "api", "http", "backend", "java21"]
triggers:
  - "외부 API 호출"
  - "RestClient 설정"
---

# Spring Boot RestClient API Integration

Guidelines for communicating intuitively and securely with external systems (payments, social logins, public data, etc.) over HTTP using the `RestClient` officially supported starting from Spring Boot 3.2.

## Overview

- Completely replaces the heavy and less readable `RestTemplate`, providing a refined Fluent API (method chaining) on par with `WebClient`.
- Operates synchronously without needing WebFlux dependencies, and **demonstrates massive throughput approaching non-blocking levels when combined with Java 21 Virtual Threads**.
- Ensures server stability by configuring global Timeout settings and HTTP status code-based error handling (`defaultStatusHandler`) by default.

## When to Use This Skill

- When calling Kakao/Google servers to verify tokens or fetch user info during social login (OAuth2).
- When sending payment approval/cancellation requests to Payment Gateway (PG) APIs.
- When fetching JSON data from a public data portal or external microservice (MSA) server to convert it into internal DTOs.

## How It Works

### Step 1: Configure `RestClient` Bean

Register `RestClient` as a Bean in a `@Configuration` class.
- **Utilize Auto-Configuration:** Instead of calling `RestClient.builder()` directly, inject the `RestClient.Builder` provided by Spring Boot as a parameter, which comes pre-configured with Observability metrics.
- Use the built-in `JdkClientHttpRequestFactory` (from Java 11+) to set timeouts. (No external libraries required).
- Apply common settings like `baseUrl` and `defaultHeader`.
- Define global exception handling logic for 4xx and 5xx errors using `defaultStatusHandler`.

### Step 2: Implement API Call Logic

Use the injected `RestClient` in the Service layer for communication.
- Start with `.get()`, `.post()`, `.put()`, or `.delete()` methods.
- Set the endpoint and query parameters with `.uri()`.
- For POST/PUT, passing an object to `.body()` automatically performs JSON serialization.
- Directly deserialize the response JSON into a DTO using `.retrieve().body(Class)`.

## Examples

```java
// 1. RestClient Configuration
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient customRestClient(RestClient.Builder builder) {
        // Use Java 11+ built-in HTTP client factory (Highly compatible with Virtual Threads)
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));    // 5-second read timeout
        
        // Important: If you need to set a Connect Timeout using a custom HttpClient, consider the following approach:
        // requestFactory.setHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());

        // Use the builder provided by Spring Boot to retain default settings like Observability metrics
        return builder
                .requestFactory(requestFactory)
                // .baseUrl("[https://api.external-service.com](https://api.external-service.com)") // Set if the domain is fixed
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new IllegalArgumentException("External API Client Error: " + response.getStatusCode());
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new IllegalStateException("External API Server Error: " + response.getStatusCode());
                })
                .build();
    }
}

// 2. Service Layer Implementation
@Service
@RequiredArgsConstructor
public class ExternalApiService {

    private final RestClient restClient;

    // Example of GET request (with Query Parameters and 404 Error Swallowing)
    public ExternalDataDto fetchExternalData(String queryId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.example.com")
                        .path("/v1/data")
                        .queryParam("id", queryId)
                        .build())
                .retrieve()
                // Swallow the exception and return null if a 404 occurs, bypassing the global handler
                .onStatus(HttpStatusCode::is404, (request, response) -> { /* do nothing */ })
                .body(ExternalDataDto.class);
    }

    // Example of POST request (with Body) returning a Generic Type
    public List<ResultDto> sendData(RequestPayload payload) {
        return restClient.post()
                .uri("[https://api.example.com/v1/submit](https://api.example.com/v1/submit)")
                .header("Authorization", "Bearer your-token-here")
                .body(payload)
                .retrieve()
                // MUST use ParameterizedTypeReference when deserializing into Lists or generic types
                .body(new ParameterizedTypeReference<List<ResultDto>>() {});
    }
}

```

## Best Practices

* **Use `ParameterizedTypeReference`:** If the external API response is a generic type like `List<Dto>` or `ApiResponse<Dto>`, using `.body(List.class)` will cause Type Erasure, resulting in a `LinkedHashMap`. You MUST use `new ParameterizedTypeReference<List<Dto>>() {}` to ensure type safety.
* **Interface-based `@HttpExchange`:** If you have dozens of API endpoints to call, consider the advanced technique of declaring Spring Boot 3's `@HttpExchange` interfaces to use them declaratively rather than invoking `RestClient` directly.

## Common Pitfalls

* **Missing Timeout Configurations (Critical):** If timeouts are not specified when creating `RestClient`, the application can enter an Infinite Block state. If the external server fails to respond, all threads on your server will be exhausted, leading to a Cascading Failure. You MUST configure `ReadTimeout` and `ConnectTimeout`.
* **Unconditional Exceptions on 4xx/5xx:** `.retrieve()` throws a `RestClientException` by default for 4xx or 5xx HTTP status codes. If you want to handle a 404 response from an external API as a "normal business flow (no data)" rather than an exception, you must add an `onStatus(HttpStatusCode::is404, ...)` handler as shown in the example to swallow the exception gracefully.
* **Confusion with `WebClient`:** `RestClient` is synchronous. There is no longer any need to use the asynchronous `WebClient` for simple communication if it means adding an unnecessary WebFlux dependency.

## Related Skills

* `spring-rest-api-exception-handler`: Reference this when you need to respond to the client with a consistent `ProblemDetail` error specification when external API communication fails (exceptions thrown by `defaultStatusHandler`).
