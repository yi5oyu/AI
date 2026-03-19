---
name: spring-boot-cloud
description: Build Spring Cloud Gateway as the single entry point for MSA, implementing routing, JWT authentication filters, and Redis-based Rate Limiting in a WebFlux (Non-blocking) environment.
argument-hint: "[라우팅할 내부 서비스 목록, 인증(JWT) 필요 여부, Rate Limit 기준 (예: user-service와 order-service로 라우팅하고, JWT 인증 필터가 적용된 게이트웨이를 작성)]"
source: "custom"
tags: ["java", "spring-boot", "spring-cloud", "gateway", "msa", "api-gateway", "webflux"]
triggers:
  - "API 게이트웨이 작성"
  - "스프링 클라우드"
---

# Spring Cloud Gateway Implementation

Guidelines for building an API Gateway that acts as the first point of contact for all client requests, securely routing them to internal K8s microservices.

## Overview

- **Single Entry Point:** Clients only need to call a single gateway address without knowing the internal structure or IPs of the microservices.
- **Separation of Cross-cutting Concerns:** Handle JWT token validation and CORS configurations centrally in the Gateway instead of duplicating them across every microservice.
- **WebFlux-based (Non-blocking):** Internally uses a Netty server and Project Reactor to handle massive concurrent traffic efficiently.

## When to Use This Skill

- When routing incoming traffic from outside the K8s cluster to various internal K8s services.
- When blocking unauthorized requests (invalid JWT tokens) at the gateway level before they reach internal services.
- When applying a Redis-based Rate Limiter to prevent specific IPs or users from excessively calling APIs in a short time.

## How It Works

### Step 1: Dependencies
Because Spring Cloud Gateway is built on WebFlux, dependency configuration is critical.
- `spring-cloud-starter-gateway`
- `spring-boot-starter-data-redis-reactive` (Required for Rate Limiter)
- **Caution:** If `spring-boot-starter-web` (Spring MVC) is on the classpath, the server will crash upon startup.

### Step 2: Routing & CORS Configuration (`application.yml`)
YAML-based declarative routing and CORS configuration are recommended for maintainability.
- `uri`: Use the K8s Service Name directly.
- `RewritePath`: Use this to strip unnecessary prefixes (e.g., `/api/orders/...`) before forwarding the request to the internal service (`/...`).

### Step 3: Custom JWT Filter Implementation
Validate the `Authorization` token from the request header and mutate the header to pass the verified User ID to internal services before routing.

## Examples

```yaml
# application.yml (Routing, CORS & Redis Rate Limiter)
spring:
  cloud:
    gateway:
      # Global CORS Configuration
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
          uri: http://order-service-svc:8080 # K8s internal service domain
          predicates:
            - Path=/api/orders/**
          filters:
            - RewritePath=/api/orders/?(?<segment>.*), /$\{segment} # Strip /api/orders prefix
            - JwtAuthFilter # Apply custom JWT filter
            - name: RequestRateLimiter # Redis-based traffic control
              args:
                redis-rate-limiter.replenishRate: 10 # 10 tokens replenished per second
                redis-rate-limiter.burstCapacity: 20 # Allow max burst of 20 requests
                key-resolver: "#{@ipKeyResolver}" # IP-based limitation

        - id: auth-service
          uri: http://auth-service-svc:8080
          predicates:
            - Path=/api/auth/** # Exclude JWT filter for auth routes

```

```java
// JwtAuthFilter.java (Reactive Auth Filter for WebFlux)
@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final ReactiveJwtValidator jwtValidator;

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

            // Use WebFlux Mono chain for non-blocking token validation
            return jwtValidator.validate(token)
                    .flatMap(userId -> {
                        // Mutate request to add User ID header for internal services
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

* **Use K8s Service Name as URI:** Trust Kubernetes CoreDNS and configure the URI as `http://{k8s-service-name}:{port}` to ensure flawless routing even when Pod IPs change dynamically.
* **Reduce Validation Burden on Internal Services:** Once the gateway validates the JWT and injects `X-User-Id`, backend microservices should blindly trust that header and focus solely on business logic without parsing JWTs again.
* **Global Exception Handling:** To provide a consistent JSON error response to clients for gateway-wide exceptions (e.g., 429 Too Many Requests, 404 Not Found) beyond simple `onError` handling in filters, implement and register an `ErrorWebExceptionHandler` bean.

## Common Pitfalls

* **Blocking Calls Inside Filters (Critical):** If you make synchronous external API calls (e.g., `RestTemplate`) inside a gateway filter, the limited Netty worker threads will be blocked, freezing the gateway. If you must query Redis for token blacklists, ALWAYS use `ReactiveStringRedisTemplate` or WebClient.
* **Mixing Spring WebMVC (Critical):** Attempting to use Tomcat-based classes like `HttpServletRequest` in filters will result in errors. You MUST use `ServerWebExchange`.
* **Double CORS Configuration:** If you allow CORS at the gateway and set `@CrossOrigin` again in the backend microservice, a 'Multiple Access-Control-Allow-Origin' error occurs. Handle CORS only once at the gateway.

## Related Skills

* `spring-boot-k8s`: Refer to this when writing K8s Service manifests that serve as the routing targets for the gateway.
* `spring-boot-resilience4j`: Integrate this to open a circuit breaker and prevent cascading failures when an internal microservice goes down.
