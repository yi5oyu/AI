---
name: spring-redis-config
description: Spring Boot 3.x 이상 환경에서 Lettuce 클라이언트를 사용하여 Redis 연결을 설정하고, 직렬화/역직렬화가 최적화된 RedisTemplate을 구성합니다.
argument-hint: "[Redis 호스트/포트 정보, 필요한 RedisTemplate 타입 (예: JWT 저장을 위한 StringRedisTemplate과 객체 저장을 위한 RedisTemplate 설정)]"
source: "custom"
tags: ["java", "spring-boot", "redis", "configuration", "database", "backend", "nosql"]
triggers:
  - "Redis 설정"
  - "레디스 연결"
---

# Spring Boot Redis Configuration

Spring Data Redis를 활용하기 위한 인프라 연결 및 직렬화(Serialization) 최적화 지침입니다.

## Overview

- 무거운 Jedis 대신 Spring Boot의 기본이자 비동기 논블로킹(Non-blocking) 클라이언트인 **Lettuce**를 사용하여 Redis 서버와 연결합니다.
- Redis CLI 환경에서 데이터를 직접 조회할 때 알 수 없는 바이트 배열(`\xac\xed\x00\x05t\x00...`)로 문자가 깨지는 현상을 방지하기 위해 커스텀 직렬화 설정을 적용합니다.
- 단순 문자열(Token 등) 처리를 위한 `StringRedisTemplate`과 복잡한 객체(DTO 등) 저장을 위한 `RedisTemplate<String, Object>`를 명확히 분리하여 구성합니다.

## When to Use This Skill

- 프로젝트 초기에 인메모리 데이터 저장소(Redis) 인프라를 연동할 때.
- Refresh Token 관리, 분산 락, 글로벌 캐싱 등을 구현하기 전 기반 설정을 다질 때.
- 객체를 Redis에 저장할 때 직렬화 예외(`SerializationException`)나 `LocalDateTime` 파싱 에러가 발생하여 설정을 바로잡아야 할 때.

## How It Works

### Step 1: Add Dependency

`build.gradle`에 Redis 의존성을 추가합니다.
- `org.springframework.boot:spring-boot-starter-data-redis` (Lettuce 클라이언트 자동 포함)

### Step 2: Configure Properties

`application.yml`에 Redis 접속 정보를 명시합니다. (호스트, 포트, 비밀번호 등)

### Step 3: Configure `RedisConnectionFactory`

`@Configuration` 클래스를 만들고 `LettuceConnectionFactory`를 빈(Bean)으로 등록합니다.
- `application.yml`에 설정한 `@Value` 값들을 주입받아 커넥션을 구성합니다.

### Step 4: Configure `RedisTemplate`

데이터를 조작할 템플릿 객체를 빈으로 등록합니다.
- **`StringRedisTemplate`:** Key와 Value가 모두 String인 경우(예: JWT Refresh Token) 사용합니다. (Spring Boot 자동 구성을 활용할 수 있으나, 커스텀 커넥션 팩토리를 사용할 경우 명시적 등록이 필요할 수 있습니다.)
- **`RedisTemplate<String, Object>`:** Java 객체를 JSON 형태로 저장하기 위해 설정합니다.
  - Key 직렬화: `StringRedisSerializer` 사용.
  - Value 직렬화: `GenericJackson2JsonRedisSerializer` 또는 `Jackson2JsonRedisSerializer` 사용.
  - **ObjectMapper 설정:** Java 8+의 `LocalDateTime` 등 날짜 타입이 JSON으로 정상 변환되도록 `JavaTimeModule`을 반드시 등록해야 합니다.

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

    // 2. JWT 토큰 등 단순 문자열 저장을 위한 Template
    // 참고: Spring Boot는 기본적으로 StringRedisTemplate을 자동 구성(Auto-Configuration)합니다.
    // 커넥션 팩토리를 커스텀하게 오버라이딩하는 경우에만 명시적으로 빈을 등록하십시오.
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    // 3. 복잡한 Java 객체(DTO) 저장을 위한 Template
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // Key는 String으로 직렬화 (Redis CLI에서 육안으로 읽기 위함)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화 (JavaTimeModule을 등록하여 LocalDateTime 직렬화 에러 방지)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 다형성(Polymorphism) 처리가 필요한 경우 activateDefaultTyping 추가 고려

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }
}

```

## Best Practices

* **Lettuce 커넥션 풀 적용:** 트래픽이 많은 실무(Production) 환경에서는 `LettucePoolingClientConfiguration`을 사용하여 Redis 커넥션 풀(Connection Pool)을 적절히 설정하는 것이 권장됩니다. (이를 위해 `commons-pool2` 의존성 추가 필요)
* **목적에 맞는 Template 사용:** 모든 것을 JSON 객체로 저장할 필요는 없습니다. 토큰 만료 관리나 단순 카운팅(조회수 증감) 로직에는 오버헤드가 적은 `StringRedisTemplate`을 우선적으로 사용하십시오.

## Common Pitfalls

* **Default JDK Serializer 이슈 (Critical):** Spring Boot가 기본 제공하는 `RedisTemplate`은 `JdkSerializationRedisSerializer`를 사용합니다. 이를 그대로 쓰면 데이터가 바이너리 형태로 저장되어 Redis CLI나 GUI 툴(예: Redis Desktop Manager)에서 데이터를 육안으로 확인할 수 없고, 이종 플랫폼(Node.js, Python 등)과 데이터를 공유할 수 없습니다. 반드시 커스텀 직렬화기(Serializer)를 등록하십시오.
* **LocalDateTime 직렬화 에러 (Critical):** Java의 `LocalDateTime` 필드를 가진 객체를 `GenericJackson2JsonRedisSerializer`로 직렬화하려고 할 때, `ObjectMapper`에 `JavaTimeModule`이 등록되어 있지 않으면 런타임에 직렬화 실패 에러(`InvalidDefinitionException`)가 발생합니다.
* **Jedis 사용 지양:** Jedis 클라이언트는 멀티스레드 환경에서 스레드 세이프(Thread-safe)하지 않아 커넥션 풀 비용이 비쌉니다. 특별한 이유가 없다면 항상 비동기를 지원하는 Lettuce를 사용하십시오.

## Related Skills

* `spring-redis-token`: 설정된 `StringRedisTemplate`을 주입받아 JWT Refresh Token을 저장하고 수명(TTL)을 관리할 때 사용합니다.
* `spring-redis-cache`: `@Cacheable` 애노테이션과 함께 전역 캐시를 구성하기 위해 `RedisCacheManager`를 설정할 때 이 연결 팩토리를 활용합니다.
