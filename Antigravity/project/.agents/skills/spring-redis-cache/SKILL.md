---
name: spring-redis-cache
description: Configure a global RedisCacheManager integrating Spring's cache abstraction (@Cacheable) with Redis for performance optimization, while actively preventing detrimental over-caching strategies.
argument-hint: "[캐싱할 대상 데이터/메서드, 적용할 만료 시간(TTL), 직렬화 객체 타입 (예: 상품 단건 조회에 10분 만료로 Redis 캐시 적용)]"
source: "custom"
tags: ["java", "spring-boot", "redis", "cache", "performance", "backend", "optimization", "architecture"]
triggers:
  - "레디스 캐시 설정"
  - "캐싱 구현"
---

# Spring Boot Redis Cache Optimization

Guidelines for eliminating database I/O bottlenecks and maximizing response speeds by combining Spring's AOP-based cache abstraction (`@Cacheable`, `@CacheEvict`) with Redis. This strictly advises against the "caching is always faster" fallacy by considering network trip costs and serialization overheads.

## Overview

- When a method is called, a Proxy intercepts the call. If the data exists in Redis, it is returned immediately (Cache Hit) without hitting the DB.
- Registers `RedisCacheManager` as a Bean to set a global default TTL (Time-To-Live) and finely control custom TTLs for specific data domains (cache names).
- Reuses the custom serializer (`GenericJackson2JsonRedisSerializer`) configured previously to enforce caching data in a human-readable JSON format.

## When to Use This Skill

- In domains where Read requests overwhelmingly outnumber Write/Update requests, and **data volatility is low** (e.g., announcements, product details, popular recipe lists).
- When the query execution cost (due to complex joins, sorting, or aggregations) is definitively higher than the Redis access cost (Network I/O + JSON Deserialization).
- When building global cache infrastructure to prevent data consistency issues in a scaled-out multi-server environment, which is a common problem with local caches (Caffeine).

## How It Works

### Step 1: Enable Caching

Declare the **`@EnableCaching`** annotation at the top of a `@Configuration` class to activate Spring's caching features.

### Step 2: Configure `RedisCacheManager`

Register a `RedisCacheManager` bean to control cache behavior.
- Use `RedisCacheConfiguration.defaultCacheConfig()` to generate the default settings.
- **Serialization:** Specify `StringRedisSerializer` for Keys and `GenericJackson2JsonRedisSerializer` for Values.
- **TTL Configuration:** Set the default expiration time using `entryTtl()`, and specify custom TTLs per `cacheNames` via `withInitialCacheConfigurations()`.

### Step 3: Apply Annotations

Apply caching annotations to methods in the Service Layer.
- `@Cacheable`: Returns data from the cache if it exists; otherwise, executes the method (DB query) and saves the result to the cache. Use SpEL for composite keys (e.g., `key = "#param1 + '_' + #param2"`).
- `@CacheEvict`: Forcefully invalidates (deletes) the existing cache when data is modified or deleted. Use the `allEntries = true` option to clear an entire list cache.

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

    // Cache Penetration Defense: Consider caching 'null' to protect the DB from malicious non-existent ID lookups.
    @Cacheable(cacheNames = "products", key = "#productId")
    @Transactional(readOnly = true)
    public ProductDto getProduct(Long productId) {
        return productRepository.findById(productId)
                .map(ProductDto::from)
                .orElse(null); // Cache the null result with a short TTL instead of throwing a 404 immediately
    }

    @CacheEvict(cacheNames = "products", key = "#productId")
    @Transactional
    public void updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        product.update(request);
    }
}

```

## Best Practices

* **Cache Penetration Defense:** For public APIs, malicious users can spam requests with non-existent IDs, bringing down the DB. Instead of always using `@Cacheable(unless = "#result == null")`, it is often safer to cache the `null` result itself with a very short TTL (e.g., 10 seconds) to protect the DB.
* **Constantize Cache Namespaces:** Extract strings used in `cacheNames` and TTL information into constants to prevent Cache Misses caused by typos.

## Common Pitfalls

* **Performance Degradation due to Over-Caching (Critical):** Redis is still an external server. Blindly caching a lightweight DB query that uses an index and finishes in 1-2ms will actually degrade response times due to Redis network round-trips and JSON serialization/deserialization overhead (3-5ms). Only introduce caching when the DB query cost significantly outweighs the Redis access cost.
* **Cache Thrashing from High Volatility (Critical):** Caching data that is updated more frequently than it is read (e.g., real-time inventory, stock tickers) will cause `@CacheEvict` to be called constantly before the data is even read. This drops the Cache Hit Ratio to 0% and adds unnecessary load to both Redis and the DB.
* **Self-Invocation Problem (Critical):** Spring's `@Cacheable` operates on Proxies. Calling a `@Cacheable` method directly from within the same class bypasses the Proxy, meaning the cache is ignored and the DB is hit every time. Always invoke the method from an external class (like a Controller).
* **Polymorphic Deserialization / Entity Caching:** Missing type info in `ObjectMapper` causes `ClassCastException`, and caching JPA Entities directly causes LazyLoading exceptions. Always serialize immutable DTOs and set `activateDefaultTyping`.

## Related Skills

* `spring-redis-config`: The connection factory configuration guideline serving as the foundation for this cache manager.
* `database-erd-design`: Refer to this to evaluate data Read/Write volatility before deciding to introduce a cache.
