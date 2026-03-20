---
name: spring-boot-resilience4j
description: Implement Circuit Breaker, Rate Limiter, Retry, and TimeLimiter logic using Resilience4j to prevent cascading failures caused by inter-microservice communication errors in an MSA environment.
argument-hint: "[대상 서비스명, 실패 임계값, fallback 메서드 요구사항 (예: order-service 호출 실패 시 fallback 처리와 함께 Circuit Breaker 설정을 작성)]"
source: "custom"
tags: ["java", "spring-boot", "resilience4j", "msa", "circuit-breaker", "fault-tolerance"]
triggers:
  - "장애 격리"
  - "Resilience4j 연동"
  - "서킷 브레이커 설정"
---

# Spring Boot Resilience4j Implementation

Guidelines for implementing Resilience4j to block failures originating from external dependencies (other services or APIs) from propagating through the entire system (Fault Tolerance) and to recover quickly and gracefully in a Microservices Architecture.

## Overview

- **Circuit Breaker:** If the failure rate or response time for a specific service exceeds a threshold, it trips (Opens) the circuit to block further calls and immediately returns a Fallback (alternative) response.
- **Retry & TimeLimiter:** Performs retries for temporary network glitches, or sets a Timeout to cut off prolonged delayed calls.
- **Fallback:** A safety mechanism that returns default values or cached data so the system can continue to operate safely when a service is blocked or throws an exception.

## When to Use This Skill

- When Service A synchronously calls the REST API of Service B (using `RestTemplate`, `WebClient`, `RestClient`, or `FeignClient`).
- When you need to prevent your server's threads from being exhausted due to communication delays with external APIs or databases.
- When you must respond to the client with at least basic information even during an outage (Graceful Degradation).

## How It Works

### Step 1: Dependencies
Add the Resilience4j Starter and Spring Boot Actuator dependencies to `build.gradle`.
- `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j`
- `org.springframework.boot:spring-boot-starter-actuator`

### Step 2: Configuration (`application.yml`)
Although Java configuration is possible, YAML-based declarative configuration is recommended to allow real-time changes during operation. **Crucially, configure filtering so that business logic errors (e.g., 404 Not Found) are not included in the failure rate.**

### Step 3: Implement Circuit Breaker & Fallback
Declare the `@CircuitBreaker` annotation on the external service call method and specify the `fallbackMethod` attribute.
- **Caution:** The Fallback method MUST have the same return type as the original method, and MUST accept the thrown `Throwable` exception object as its final parameter.

## Examples

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true # Expose state to Actuator's /health endpoint
        slidingWindowSize: 10 # Calculate failure rate based on the last 10 calls
        minimumNumberOfCalls: 5 # Minimum calls required before calculating failure rate
        permittedNumberOfCallsInHalfOpenState: 3 # Number of test calls permitted in Half-Open state
        automaticTransitionFromOpenToHalfOpenEnabled: true # Automatically transition to Half-Open after wait duration
        waitDurationInOpenState: 10s # Time the circuit stays Open before switching to Half-Open
        failureRateThreshold: 50 # Trip circuit (Open) if failure rate is 50% or higher
        slowCallDurationThreshold: 3s # Consider a call slow if it takes longer than 3 seconds
        slowCallRateThreshold: 50 # Trip circuit (Open) if slow call rate is 50% or higher
        ignoreExceptions: # Exclude client errors from failure rate calculation
          - org.springframework.web.client.HttpClientErrorException.NotFound
          - org.springframework.web.client.HttpClientErrorException.BadRequest
    instances:
      inventoryServiceCircuitBreaker:
        baseConfig: default # Inherit default config

```

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    // Map to the instance name defined in application.yml
    @CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "getInventoryFallback")
    public InventoryDto getInventory(String productId) {
        log.info("Requesting inventory for product: {}", productId);
        String url = "http://inventory-service/api/inventory/" + productId;
        // Assume this external call might intentionally delay or throw errors
        return restTemplate.getForObject(url, InventoryDto.class);
    }

    // Fallback Method: Signature must match the original method, with an appended Throwable parameter
    public InventoryDto getInventoryFallback(String productId, Throwable t) {
        log.error("Fallback executed for product {}. Reason: {}", productId, t.getMessage());
        
        // 1. (Recommended) Attempt to fetch recent data from a cache like Redis first
        // return cachedInventoryData(productId);

        // 2. If cache also fails, return default (safe) data for system stability
        return new InventoryDto(productId, 0, "Out of Stock (Fallback)");
    }
}

```

## Best Practices

* **Monitor via Actuator Integration:** Real-time monitoring of the Circuit Breaker's state (CLOSED, OPEN, HALF_OPEN) via Kubernetes Liveness/Readiness Probes or Prometheus/Grafana is mandatory. Ensure `registerHealthIndicator: true` is set.
* **Appropriate Fallback Design (Caching):** A fallback does not mean simply ignoring the exception. As shown in the example, if the original call fails, structure a chain where you first try to read from a fast cache store (like Redis), and only if that fails, return a default value as a last resort.
* **Set Meaningful Exception Criteria:** Use the `ignoreExceptions` setting to exclude client-side exceptions (`400 Bad Request`, `404 Not Found`) or business exceptions from the server failure rate calculation. Only true network timeouts or `500 Internal Server Error` should be considered failures.

## Common Pitfalls

* **AOP Proxy Bypass on Internal Calls (Critical):** Because this operates on Spring AOP, calling a `@CircuitBreaker`-annotated method directly from within the *same* class bypasses the annotation. (e.g., if you have `public void a() { b(); }` and `b()` has the circuit breaker, calling `a()` will NOT trigger it). You must call it from an injected external Bean.
* **Threshold Configuration Errors:** In low-traffic environments, setting `minimumNumberOfCalls` too high means the circuit breaker might never trip during an outage. Setting it too low might cause 'Flapping' (the circuit closing after just 1 or 2 temporary errors). Tuning based on production traffic volume is essential.
* **Infinite Loop in Fallback:** If you make another synchronous call to an unstable external API inside the Fallback method, you risk causing a cascading failure again. Keep fallback logic extremely lightweight, such as fast in-memory calculations or safe cache lookups.

## Related Skills

* `spring-cloud-gateway`: Integrate this at the API Gateway level to globally apply circuit breakers and block failures for specific routing paths to internal services.
* `spring-restclient-api`: When using the modern `RestClient` instead of `RestTemplate`, apply the circuit breaker at the method level by wrapping the calls in the exact same manner.
