---
name: spring-redis-cache
description: Spring Boot 3.x 이상 환경에서 스프링 캐시 추상화(@Cacheable)와 Redis를 연동하여 성능 최적화를 위한 전역 캐시 매니저(RedisCacheManager)를 구축합니다. 단, 무분별한 오버 캐싱을 방지하는 전략을 포함합니다.
argument-hint: "[캐싱할 대상 데이터/메서드, 적용할 만료 시간(TTL), 직렬화 객체 타입 (예: 상품 단건 조회에 10분 만료로 Redis 캐시 적용)]"
source: "custom"
tags: ["java", "spring-boot", "redis", "cache", "performance", "backend", "optimization", "architecture"]
triggers:
  - "레디스 캐시 설정"
  - "캐싱 구현"
---

# Spring Boot Redis Cache Optimization

Spring의 AOP 기반 캐시 추상화(`@Cacheable`, `@CacheEvict`)와 Redis를 결합하여 데이터베이스 I/O 병목을 제거하고 응답 속도를 극대화하는 지침입니다. "무조건 캐시가 빠르다"는 맹신을 버리고, 네트워크 비용과 직렬화 오버헤드를 고려한 실무적인 접근을 지향합니다.

## Overview

- 메서드 호출 시 프록시(Proxy)가 개입하여 Redis에 데이터가 있으면 DB를 조회하지 않고 바로 반환(Cache Hit)합니다.
- `RedisCacheManager`를 빈(Bean)으로 등록하여 글로벌 기본 TTL(만료 시간)을 설정하고, 특정 데이터 도메인(캐시 이름)마다 개별적인 TTL을 세밀하게 제어합니다.
- 앞서 구성한 커스텀 직렬화기(`GenericJackson2JsonRedisSerializer`)를 재사용하여, 캐시 데이터가 육안으로 식별 가능한 JSON 포맷으로 저장되도록 강제합니다.

## When to Use This Skill

- 읽기(Read) 요청이 쓰기/수정(Write/Update)보다 압도적으로 많고, **데이터 변동성(Volatility)이 낮은 도메인** (예: 공지사항, 상품 정보, 인기 레시피 목록).
- 복잡한 조인, 정렬, 통계 연산 등으로 인해 DB 쿼리 실행 비용이 Redis 접근 비용(네트워크 I/O + JSON 역직렬화)보다 확실하게 비쌀 때.
- 로컬 캐시(Caffeine)를 사용할 경우 다중 서버 환경(Scale-out)에서 데이터 정합성이 깨지는 것을 방지하기 위해 글로벌 캐시를 구축할 때.

## How It Works

### Step 1: Enable Caching

`@Configuration` 클래스 상단에 **`@EnableCaching`** 어노테이션을 선언하여 Spring의 캐싱 기능을 활성화합니다.

### Step 2: Configure `RedisCacheManager`

캐시 동작을 제어할 `RedisCacheManager` 빈을 등록합니다.
- `RedisCacheConfiguration.defaultCacheConfig()`를 사용하여 기본 설정을 생성합니다.
- **직렬화:** Key는 `StringRedisSerializer`, Value는 `GenericJackson2JsonRedisSerializer`를 지정하여 바이트 깨짐 현상을 방지합니다.
- **TTL 설정:** `entryTtl()`을 사용하여 기본 만료 시간을 설정하고, `withInitialCacheConfigurations()`를 통해 캐시 이름(`cacheNames`)별 커스텀 TTL을 지정합니다.

### Step 3: Apply Annotations

서비스 계층(Service Layer)의 메서드에 캐싱 어노테이션을 적용합니다.
- `@Cacheable`: 캐시에 데이터가 있으면 반환, 없으면 메서드(DB 쿼리)를 실행 후 캐시에 저장합니다. 파라미터가 여러 개일 경우 `key = "#param1 + '_' + #param2"`처럼 SpEL로 고유 키를 구성하십시오.
- `@CacheEvict`: 데이터 수정/삭제 시 기존 캐시를 강제로 무효화(삭제)합니다. 전체 목록 캐시를 날릴 때는 `allEntries = true` 옵션을 사용합니다.

## Examples

```java
// 1. Redis Cache Configuration
@EnableCaching
@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        GenericJackson2JsonRedisSerializer redisSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer))
                .entryTtl(Duration.ofHours(1));

        Map<String, RedisCacheConfiguration> customConfigs = Map.of(
                "products", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "notices", defaultConfig.entryTtl(Duration.ofDays(1))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(customConfigs)
                .build();
    }
}

// 2. Service Layer Implementation
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    // Cache Penetration 방어: 악의적인 존재하지 않는 ID 조회를 막기 위해 
    // 결과가 없어도(Null) 짧은 TTL로 캐싱할지, 아니면 unless = "#result == null"로 막을지 도메인에 따라 선택합니다.
    @Cacheable(cacheNames = "products", key = "#productId")
    @Transactional(readOnly = true)
    public ProductDto getProduct(Long productId) {
        return productRepository.findById(productId)
                .map(ProductDto::from)
                .orElse(null); // 캐시 관통을 막기 위해 404 예외 대신 null을 캐싱하는 전략 고려
    }

    @CacheEvict(cacheNames = "products", key = "#productId")
    @Transactional
    public void updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다."));
        product.update(request);
    }
}

```

## Best Practices

* **캐시 관통(Cache Penetration) 방어:** 퍼블릭 API의 경우 악의적인 유저가 존재하지 않는 ID로 무한 요청을 보내 DB를 다운시킬 수 있습니다. 이 경우 무조건 `@Cacheable(unless = "#result == null")`을 쓰는 것보다, 아예 `null` 값 자체를 아주 짧은 TTL(예: 10초)로 캐싱하여 DB를 보호하는 전략이 안전합니다.
* **캐시 네임스페이스 상수화:** `cacheNames`나 TTL 정보는 상수 클래스로 관리하여 오타를 방지하십시오.

## Common Pitfalls

* **오버 캐싱으로 인한 성능 저하 (Critical):** Redis도 결국 외부 서버입니다. DB 인덱스를 타서 1~2ms면 끝나는 매우 가벼운 쿼리를 무작정 캐싱하면, Redis 네트워크 통신 비용과 JSON 직렬화/역직렬화 비용(3~5ms)이 추가되어 오히려 응답 속도가 느려집니다. 쿼리 비용이 Redis 접근 비용보다 클 때만 캐시를 도입하십시오.
* **잦은 무효화로 인한 스래싱(Thrashing) (Critical):** 조회보다 수정이 더 자주 일어나는 데이터(예: 실시간 재고, 주식 호가)를 캐싱하면, 데이터를 읽기도 전에 `@CacheEvict`가 계속 호출됩니다. 이는 캐시 적중률(Hit Ratio)을 0%로 만들고 Redis와 DB 양쪽에 부하만 가중시킵니다.
* **내부 메서드 호출 (Self-Invocation) 문제 (Critical):** Spring `@Cacheable`은 프록시 기반이므로 동일한 클래스 내부에서 메서드를 직접 호출하면 캐시가 동작하지 않고 DB를 찌릅니다. 반드시 외부 클래스(Controller 등)에서 주입받아 호출하십시오.
* **다형성 역직렬화 에러 / 엔티티 직접 캐싱:** `ObjectMapper` 타입 정보 누락 시 `ClassCastException`이 발생하며, JPA Entity를 직접 캐싱하면 LazyLoading 예외가 터집니다. 반드시 불변 DTO를 캐싱하고 `activateDefaultTyping`을 설정하십시오.

## Related Skills

* `spring-redis-config`: 이 캐시 매니저 설정의 기반이 되는 커넥션 팩토리 연결 지침입니다.
* `database-erd-design`: 캐싱할 데이터의 조회/수정 빈도(Volatility)를 파악하여 캐시 도입 여부를 결정할 때 참조합니다.
