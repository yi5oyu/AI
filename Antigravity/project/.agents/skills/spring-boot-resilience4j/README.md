---
name: spring-boot-resilience4j
description: MSA 환경에서 마이크로서비스 간 통신 실패로 인한 장애 연쇄를 방지하기 위해 Resilience4j를 사용한 서킷 브레이커(Circuit Breaker), 속도 제한(Rate Limiter), 재시도(Retry), 시간제한(TimeLimiter) 로직을 구현합니다.
argument-hint: "[대상 서비스명, 실패 임계값, fallback 메서드 요구사항 (예: order-service 호출 실패 시 fallback 처리와 함께 Circuit Breaker 설정을 작성)]"
source: "custom"
tags: ["java", "spring-boot", "resilience4j", "msa", "circuit-breaker", "fault-tolerance"]
triggers:
  - "장애 격리"
  - "Resilience4j 연동"
  - "서킷 브레이커 설정"
---

# Spring Boot Resilience4j Implementation

마이크로서비스 아키텍처에서 외부 의존성(다른 서비스나 API) 호출 시 발생할 수 있는 장애가 전체 시스템으로 전파되는 것을 차단하고(Fault Tolerance), 빠르고 유연하게 복구하기 위한 Resilience4j 구현 지침입니다.

## Overview

- **Circuit Breaker:** 특정 서비스에 대한 호출 실패율이나 응답 시간이 임계치를 초과하면 회로를 차단(Open)하여 더 이상의 호출을 막고 즉각적인 Fallback(대체) 응답을 반환합니다.
- **Retry & TimeLimiter:** 일시적인 네트워크 오류에 대해 재시도(Retry)를 수행하거나, 지연되는 호출을 끊기 위해 시간제한(Timeout)을 설정합니다.
- **Fallback:** 서비스가 차단되었거나 예외가 발생했을 때 시스템이 안전하게 동작하도록 기본값이나 캐시된 데이터를 반환하는 안전장치입니다.

## When to Use This Skill

- 서비스 A가 서비스 B의 REST API를 동기적으로 호출(`RestTemplate`, `WebClient`, `RestClient`, `FeignClient`)할 때.
- 외부 API나 데이터베이스 통신 지연이 발생했을 때 내 서버의 스레드가 고갈되는 것을 방지해야 할 때.
- 장애 상황에서도 클라이언트에게 최소한의 기본 정보(Graceful Degradation)를 응답해야 할 때.

## How It Works

### Step 1: Dependencies
`build.gradle`에 Resilience4j Starter와 Spring Boot Actuator 의존성을 추가합니다.
- `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j`
- `org.springframework.boot:spring-boot-starter-actuator`

### Step 2: Configuration (`application.yml`)
자바 코드로 설정할 수 있지만, 운영 중 실시간 변경이 가능하도록 YAML을 통한 선언적 설정을 권장합니다. **특히 비즈니스 로직 에러(예: 404 Not Found)가 실패율에 포함되지 않도록 필터링하는 것이 중요합니다.**

### Step 3: Implement Circuit Breaker & Fallback
호출할 외부 서비스 메서드에 `@CircuitBreaker` 어노테이션을 선언하고, `fallbackMethod` 속성을 지정합니다.
- **주의:** Fallback 메서드는 원본 메서드와 반환 타입이 일치해야 하며, 마지막 매개변수로 발생한 `Throwable` 예외 객체를 받아야 합니다.

## Examples

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true # Actuator의 /health 엔드포인트에 상태 노출
        slidingWindowSize: 10 # 최근 10번의 호출을 기준으로 실패율 계산
        minimumNumberOfCalls: 5 # 실패율 계산을 시작하기 위한 최소 호출 수
        permittedNumberOfCallsInHalfOpenState: 3 # 반열림(Half-Open) 상태에서 테스트할 호출 수
        automaticTransitionFromOpenToHalfOpenEnabled: true # 지정된 대기 시간 후 자동으로 Half-Open 상태로 전환
        waitDurationInOpenState: 10s # 회로가 열려있는(Open) 시간. 이후 Half-Open으로 전환
        failureRateThreshold: 50 # 실패율 50% 이상 시 회로 차단(Open)
        slowCallDurationThreshold: 3s # 3초 이상 응답 지연 시 느린 호출로 간주
        slowCallRateThreshold: 50 # 느린 호출 비율이 50% 이상 시 회로 차단(Open)
        ignoreExceptions: # 클라이언트 에러 등은 실패율에서 제외
          - org.springframework.web.client.HttpClientErrorException.NotFound
          - org.springframework.web.client.HttpClientErrorException.BadRequest
    instances:
      inventoryServiceCircuitBreaker:
        baseConfig: default # default 설정을 상속

```

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    // application.yml에 정의된 인스턴스 이름과 매핑
    @CircuitBreaker(name = "inventoryServiceCircuitBreaker", fallbackMethod = "getInventoryFallback")
    public InventoryDto getInventory(String productId) {
        log.info("Requesting inventory for product: {}", productId);
        String url = "http://inventory-service/api/inventory/" + productId;
        // 의도적으로 지연이나 에러를 발생시키는 외부 호출 가정
        return restTemplate.getForObject(url, InventoryDto.class);
    }

    // Fallback 메서드: 원본 메서드와 서명(Signature)이 동일해야 하며, 끝에 Throwable 파라미터 추가
    public InventoryDto getInventoryFallback(String productId, Throwable t) {
        log.error("Fallback executed for product {}. Reason: {}", productId, t.getMessage());
        
        // 1. (권장) Redis 등 캐시에서 최근 데이터 조회 시도
        // return cachedInventoryData(productId);

        // 2. 캐시도 없다면 시스템 안정을 위한 기본(안전) 데이터 반환
        return new InventoryDto(productId, 0, "Out of Stock (Fallback)");
    }
}

```

## Best Practices

* **Actuator 연동 모니터링:** `registerHealthIndicator: true` 설정을 통해 쿠버네티스의 Liveness/Readiness Probe나 Prometheus/Grafana에서 서킷 브레이커의 상태(CLOSED, OPEN, HALF_OPEN)를 실시간으로 모니터링해야 합니다.
* **적절한 Fallback 설계 (캐시 활용):** Fallback은 예외를 무시하는 것이 아닙니다. 예제처럼 원본 호출이 실패하면 먼저 Redis 같은 빠른 캐시 저장소에서 데이터를 읽어오도록 구성하고, 그것마저 실패할 경우 최후의 수단으로 기본값을 반환하는 체인 형태가 좋습니다.
* **의미 있는 예외 기준 설정:** `ignoreExceptions` 설정을 활용하여 `400 Bad Request`나 `404 Not Found` 같은 클라이언트 측 예외나 비즈니스 예외는 서버 장애율 계산(Failure Rate)에서 제외하도록 설정하십시오. 오직 네트워크 타임아웃이나 `500 Internal Server Error`만 실패로 간주해야 합니다.

## Common Pitfalls

* **내부 메서드 호출 시 프록시 우회 (Critical):** Spring AOP 기반으로 동작하므로, 같은 클래스 내부에서 `@CircuitBreaker`가 적용된 메서드를 직접 호출하면 어노테이션이 작동하지 않습니다. (예: `public void a() { b(); }` 구조에서 `b()`에 서킷브레이커를 걸어도 `a()`에서 호출하면 적용 안 됨). 반드시 외부 빈(Bean)에서 의존성을 주입받아 호출해야 합니다.
* **임계치 설정 오류:** 트래픽이 적은 환경에서 `minimumNumberOfCalls`를 너무 높게 잡으면 장애가 나도 서킷 브레이커가 작동하지 않을 수 있으며, 반대로 너무 낮게 잡으면 일시적인 오류 1~2번에 회로가 닫혀버리는(Flapping) 현상이 발생합니다. 운영 환경의 트래픽 볼륨에 맞는 튜닝이 필수적입니다.
* **무한 루프 Fallback:** Fallback 메서드 내부에서 또 다른 불안정한 외부 API를 동기 호출하게 되면, 다시 장애가 연쇄될 위험이 있습니다. Fallback 로직은 최대한 가볍고 빠른 메모리 내 연산이나 캐시 조회 수준으로 유지하십시오.

## Related Skills

* `spring-cloud-gateway`: API 게이트웨이 레벨에서 내부 서비스로 향하는 트래픽에 서킷 브레이커를 전역적으로 적용할 때 연동합니다.
* `spring-restclient-api`: `RestTemplate` 외에 모던 `RestClient`를 사용할 때도 메서드 레벨에서 동일한 방식으로 서킷 브레이커를 감싸서 적용합니다.
