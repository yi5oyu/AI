---
name: spring-redis-config
description: Configure Redis connections using the Lettuce client and set up RedisTemplate with optimized serialization/deserialization in a Spring Boot 3.x+ environment.
argument-hint: "[Redis 호스트/포트 정보, 필요한 RedisTemplate 타입 (예: JWT 저장을 위한 StringRedisTemplate과 객체 저장을 위한 RedisTemplate 설정)]"
source: "custom"
tags: ["java", "spring-boot", "redis", "configuration", "database", "backend", "nosql"]
triggers:
  - "Redis 설정"
  - "레디스 연결"
---

# Spring Boot Redis Configuration

Guidelines for infrastructure connection and serialization optimization to utilize Spring Data Redis effectively.

## Overview

- Connects to the Redis server using **Lettuce**, Spring Boot's default asynchronous and non-blocking client, entirely replacing the heavier Jedis.
- Applies custom serialization configurations to prevent characters from being garbled into unknown byte arrays (e.g., `\xac\xed\x00\x05t\x00...`) when querying data directly from the Redis CLI.
- Clearly separates the configuration of `StringRedisTemplate` for simple string handling (like Tokens) and `RedisTemplate<String, Object>` for storing complex objects (like DTOs).

## When to Use This Skill

- When integrating an in-memory data store (Redis) infrastructure at the beginning of a project.
- When laying the groundwork before implementing Refresh Token management, distributed locks, or global caching.
- When a `SerializationException` or `LocalDateTime` parsing error occurs while saving objects to Redis, and the configuration needs to be corrected.

## How It Works

### Step 1: Add Dependency

Add the Redis dependency to `build.gradle`.
- `org.springframework.boot:spring-boot-starter-data-redis` (Lettuce client is automatically included).

### Step 2: Configure Properties

Specify Redis connection information in `application.yml` (host, port, password, etc.). Ensure you use the `spring.data.redis.*` prefix for Spring Boot 3.x+.

### Step 3: Configure `RedisConnectionFactory`

Create a `@Configuration` class and register `LettuceConnectionFactory` as a Bean.
- Inject the `@Value` properties configured in `application.yml` to set up the connection.

### Step 4: Configure `RedisTemplate`

Register template objects for data manipulation as Beans.



- **`StringRedisTemplate`:** Used when both Key and Value are strings (e.g., JWT Refresh Tokens). (Spring Boot's auto-configuration provides this out of the box, but it can be explicitly defined if custom connection factories are used).
- **`RedisTemplate<String, Object>`:** Configured to save Java objects in JSON format.
  - Key Serialization: Use `StringRedisSerializer`.
  - Value Serialization: Use `GenericJackson2JsonRedisSerializer` or `Jackson2JsonRedisSerializer`.
  - **ObjectMapper Configuration:** You MUST register `JavaTimeModule` so date types like Java 8+ `LocalDateTime` are correctly converted to JSON.

## Examples

```java
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password}")
    private String password;

    // 1. Redis Connection Factory (Lettuce)
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        if (StringUtils.hasText(password)) {
            configuration.setPassword(password);
        }
        return new LettuceConnectionFactory(configuration);
    }

    // 2. Template for simple string storage (e.g., JWT Tokens)
    // Note: Spring Boot automatically configures StringRedisTemplate. 
    // Explicit declaration is only needed if you are overriding the default connection factory.
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    // 3. Template for storing complex Java objects (DTOs)
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // Serialize Keys as Strings (for readability in Redis CLI)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Serialize Values as JSON (Register JavaTimeModule to prevent LocalDateTime serialization errors)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Note: If polymorphic deserialization is required, consider activateDefaultTyping() with BasicPolymorphicTypeValidator.

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }
}

```

## Best Practices

* **Apply Lettuce Connection Pool:** In high-traffic production environments, it is highly recommended to properly configure a Redis Connection Pool using `LettucePoolingClientConfiguration`. (This requires adding the `commons-pool2` dependency).
* **Use the Right Template for the Job:** Not everything needs to be saved as a JSON object. For token expiration management or simple counting (e.g., incrementing view counts), prioritize `StringRedisTemplate` as it has lower overhead.

## Common Pitfalls

* **Default JDK Serializer Issue (Critical):** The default `RedisTemplate` provided by Spring Boot uses `JdkSerializationRedisSerializer`. If used as-is, data is stored in binary format, making it unreadable in the Redis CLI or GUI tools (like Redis Desktop Manager), and impossible to share with heterogeneous platforms (Node.js, Python, etc.). You MUST register custom serializers.
* **LocalDateTime Serialization Error (Critical):** When attempting to serialize an object containing Java's `LocalDateTime` fields using `GenericJackson2JsonRedisSerializer`, a runtime serialization failure (`InvalidDefinitionException`) occurs if the `JavaTimeModule` is not registered in the `ObjectMapper`.
* **Avoid Using Jedis:** The Jedis client is not thread-safe in multi-threaded environments, making connection pooling expensive. Unless there is a specific legacy requirement, always use Lettuce, which supports asynchronous and non-blocking operations.

## Related Skills

* `spring-redis-token`: Use the configured `StringRedisTemplate` to store JWT Refresh Tokens and manage their Time-To-Live (TTL).
* `spring-redis-cache`: Utilize this connection factory when setting up `RedisCacheManager` to configure global caching with the `@Cacheable` annotation.
