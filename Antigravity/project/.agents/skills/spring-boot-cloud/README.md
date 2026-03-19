---
name: spring-boot-cloud
description: MSA의 단일 진입점(Single Entry Point)인 Spring Cloud Gateway를 구축하여, 라우팅, JWT 인증 필터, Redis 기반 처리량 제한(Rate Limiting) 로직을 WebFlux(Non-blocking) 환경에 맞게 구현합니다.
argument-hint: "[라우팅할 내부 서비스 목록, 인증(JWT) 필요 여부, Rate Limit 기준 (예: user-service와 order-service로 라우팅하고, JWT 인증 필터가 적용된 게이트웨이를 작성)]"
source: "custom"
tags: ["java", "spring-boot", "spring-cloud", "gateway", "msa", "api-gateway", "webflux"]
triggers:
  - "API 게이트웨이 작성"
  - "스프링 클라우드"
---

# Spring Cloud Gateway Implementation

모든 클라이언트(Web/App)의 요청을 가장 먼저 받아 내부 K8s 마이크로서비스로 안전하게 전달하는 API 게이트웨이 구축 지침입니다.

## Overview

- **단일 진입점:** 클라이언트는 내부 마이크로서비스의 IP나 구조를 알 필요 없이 오직 게이트웨이 주소 하나만 호출합니다.
- **공통 관심사 분리:** 각 마이크로서비스(주문, 결제 등)마다 JWT 토큰 검증 로직이나 CORS 설정을 중복으로 구현할 필요 없이, 게이트웨이에서 한 번에 처리합니다.
- **WebFlux 기반 (Non-blocking):** 수많은 트래픽을 동시에 처리하기 위해 내부적으로 Netty 서버와 Project Reactor(WebFlux)를 사용합니다.

## When to Use This Skill

- K8s 클러스터 외부에서 들어오는 트래픽을 K8s 내부의 여러 서비스로 라우팅해야 할 때.
- 인가되지 않은 요청(유효하지 않은 JWT 토큰)을 내부 서비스에 도달하기 전에 차단(401 Unauthorized)하고 싶을 때.
- 특정 IP나 사용자의 무리한 호출을 막기 위해 Redis 기반의 Rate Limiter를 적용할 때.

## How It Works

### Step 1: Dependencies
Spring Cloud Gateway는 WebFlux를 기반으로 동작하므로 의존성 설정이 매우 중요합니다.
- `spring-cloud-starter-gateway`
- `spring-boot-starter-data-redis-reactive` (Rate Limiter 사용 시 필수)
- **주의:** `spring-boot-starter-web` (Spring MVC)이 클래스패스에 존재하면 시작 시 충돌이 발생합니다.

### Step 2: Routing & CORS Configuration (`application.yml`)
유지보수를 위해 YAML 기반 선언적 라우팅 및 CORS 설정을 권장합니다.
- `uri`: K8s 환경이므로 K8s의 Service Name을 그대로 사용합니다.
- `RewritePath`: 클라이언트가 호출한 경로(`/api/orders/...`)에서 내부 서비스 경로(`/...`)로 변환할 때 사용합니다.

### Step 3: Custom JWT Filter Implementation
요청 헤더에서 `Authorization` 토큰을 검증하고, 검증된 사용자 ID를 내부 서비스로 전달하기 위해 헤더를 변조(Mutate)하여 라우팅합니다.

## Examples

```yaml
# application.yml (Routing, CORS & Redis Rate Limiter)
spring:
  cloud:
    gateway:
      # 글로벌 CORS 설정
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "[https://my-frontend.com](https://my-frontend.com)"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
      routes:
        - id: order-service
          uri: http://order-service-svc:8080 # K8s 내부 서비스 도메인
          predicates:
            - Path=/api/orders/**
          filters:
            - RewritePath=/api/orders/?(?<segment>.*), /$\{segment} # 내부 전달 시 /api/orders 제거
            - JwtAuthFilter # 커스텀 JWT 필터 적용
            - name: RequestRateLimiter # Redis 기반 트래픽 제어
              args:
                redis-rate-limiter.replenishRate: 10 # 초당 10개씩 토큰 보충
                redis-rate-limiter.burstCapacity: 20 # 최대 20개까지 한 번에 요청 허용
                key-resolver: "#{@ipKeyResolver}" # IP 기반 제한

        - id: auth-service
          uri: http://auth-service-svc:8080
          predicates:
            - Path=/api/auth/** # 로그인/회원가입은 JWT 필터 제외

```

```java
// JwtAuthFilter.java (WebFlux 환경에 맞춘 Reactive 인증 필터)
@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final ReactiveJwtValidator jwtValidator; // 비동기 검증기

    public JwtAuthFilter(ReactiveJwtValidator jwtValidator) {
        super(Config.class);
        this.jwtValidator = jwtValidator;
    }

    public static class Config {}

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "No authorization header", HttpStatus.UNAUTHORIZED);
            }

            String token = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0).replace("Bearer ", "");

            // WebFlux의 Mono 체인을 사용하여 논블로킹으로 토큰 검증
            return jwtValidator.validate(token)
                    .flatMap(userId -> {
                        // 검증 성공 시 내부 서비스가 알 수 있도록 User ID 헤더 추가(Mutate)
                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-Id", userId)
                                .build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .onErrorResume(e -> onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED));
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        log.error("Gateway Auth Error: {}", err);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        return response.setComplete();
    }
}

// IpKeyResolver.java
@Configuration
public class RateLimiterConfig {
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }
}

```

## Best Practices

* **K8s Service Name을 URI로 사용:** 직접 IP를 쓰지 마십시오. K8s의 CoreDNS를 믿고 `http://{k8s-service-name}:{port}` 형태로 설정하면 동적으로 파드(Pod) IP가 바뀌어도 완벽하게 라우팅됩니다.
* **내부 서비스의 검증 책임 축소:** 게이트웨이에서 검증된 `X-User-Id`를 헤더에 넣어주었다면, 뒷단의 마이크로서비스들은 파싱 로직 없이 해당 헤더 값만 믿고 비즈니스 로직을 수행하도록 설계하십시오.
* **글로벌 예외 처리 (Global Exception Handling):** 필터 내의 단순 `onError`를 넘어, 게이트웨이 전역에서 발생하는 예외(예: 429 Too Many Requests, 404 Not Found)에 대해 클라이언트에게 일관된 JSON 포맷을 제공하려면 `ErrorWebExceptionHandler`를 구현하여 빈으로 등록하십시오.

## Common Pitfalls

* **필터 내 동기(Blocking) 호출 (Critical):** 게이트웨이 필터 안에서 외부 API를 `RestTemplate`으로 호출하거나 RDBMS 조회를 수행하면 게이트웨이가 멈춥니다. 토큰 블랙리스트 확인 등을 위해 Redis를 조회해야 한다면 반드시 Reactive Redis(`ReactiveStringRedisTemplate`)를 사용하거나 WebClient를 사용하십시오.
* **Spring WebMVC 혼용 (Critical):** Spring Cloud Gateway는 Netty 기반입니다. `HttpServletRequest` 같은 Tomcat 기반 클래스를 필터에서 사용하려 하면 컴파일 에러가 발생합니다. 반드시 `ServerWebExchange`를 사용하십시오.
* **CORS 이중 설정:** 게이트웨이에서 CORS를 허용해 두었는데, 내부 마이크로서비스에서 또 `@CrossOrigin`을 설정하면 'Multiple Access-Control-Allow-Origin' 에러가 발생합니다. CORS는 게이트웨이에서만 처리하십시오.

## Related Skills

* `spring-boot-k8s`: 게이트웨이가 라우팅할 타겟이 되는 K8s Service 매니페스트를 작성할 때 참조합니다.
* `spring-boot-resilience4j`: 특정 마이크로서비스가 죽었을 때 게이트웨이 단에서 서킷 브레이커를 열어 장애 연쇄를 막을 때 라우팅(Filters)에 결합합니다.
