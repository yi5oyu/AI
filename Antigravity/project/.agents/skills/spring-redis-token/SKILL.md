---
name: spring-redis-token
description: Safely store JWT Refresh Tokens and implement an Access Token Blacklist for logouts using StringRedisTemplate in a Spring Boot environment.
argument-hint: "[토큰 수명(TTL) 정보, Refresh Token Rotation 적용 여부 (예: Access Token 블랙리스트 처리와 Refresh Token 갱신 로직 작성)]"
source: "custom"
tags: ["java", "spring-boot", "redis", "security", "jwt", "backend", "authentication"]
triggers:
  - "리프레시 토큰 관리"
  - "Redis 토큰 저장"
  - "토큰 재발급 로직"
---

# Spring Boot Redis Token Management

Guidelines for controlling the token lifecycle on the server side by utilizing an in-memory DB (Redis) for fast and easy TTL management. This overcomes the limitations of stateless JWTs and enhances security.

## Overview

- **Refresh Token Management:** Minimizes the risk of Access Token hijacking by storing long-lived Refresh Tokens both on the client (e.g., HTTP-only cookie) and the server (Redis), ensuring their exact values match during reissue requests.
- **Access Token Blacklist:** When a user logs out, the remaining unexpired Access Token is registered in Redis as a `Blacklist` to block any further access for its remaining lifespan, preventing use by hijackers.
- Implemented using `StringRedisTemplate` to achieve the fastest and lightest performance without object serialization overhead.

## When to Use This Skill

- When implementing API endpoints for login, logout, and token reissue based on tokens issued by the `spring-jwt-auth` skill.
- When a user clicks the logout button, and you need to not only delete the token on the client side but fundamentally block access from that token on the server side.
- When high security requirements demand the implementation of Refresh Token Rotation (RTR).

## How It Works

### Step 1: Inject `StringRedisTemplate`

Instead of the heavier `RedisTemplate` used for objects, inject and use `StringRedisTemplate`, which is optimized for token management where both Keys and Values are strings.

### Step 2: Manage Refresh Token

Save the Refresh Token issued during login to Redis.
- **Key:** `RT:{UserId}` (Using the user PK as the key allows extension to concurrent login control features).
- **Value:** The issued Refresh Token string.
- **TTL:** Set identical to the Refresh Token's expiration time so it is automatically deleted from Redis when it expires.

### Step 3: Implement Blacklist (Logout)

Invalidate the Access Token passed in the HTTP header upon a logout request.
- **Key:** `AT:{AccessToken}`
- **Value:** `logout` (An arbitrary string indicating status).
- **TTL:** (Crucial) Calculate and set ONLY the **Remaining Expiration** time of the Access Token.
- Subsequently, the `JwtAuthenticationFilter` must check if the incoming Access Token exists in Redis under the `AT:` key on every request. If it exists, throw a `401 Unauthorized` exception.

## Examples

```java
@Service
@RequiredArgsConstructor
public class AuthTokenRedisService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String RT_PREFIX = "RT:";
    private static final String AT_PREFIX = "AT:";

    // 1. Save Refresh Token (On Login & Reissue)
    public void saveRefreshToken(String userId, String refreshToken, long refreshTokenValidityInSeconds) {
        redisTemplate.opsForValue().set(
                RT_PREFIX + userId,
                refreshToken,
                Duration.ofSeconds(refreshTokenValidityInSeconds)
        );
    }

    // 2. Validate Refresh Token Match (On Token Reissue)
    public boolean validateRefreshTokenMatch(String userId, String clientRefreshToken) {
        String storedToken = redisTemplate.opsForValue().get(RT_PREFIX + userId);
        return storedToken != null && storedToken.equals(clientRefreshToken);
    }

    // 3. Delete Refresh Token (On Logout)
    public void deleteRefreshToken(String userId) {
        redisTemplate.delete(RT_PREFIX + userId);
    }

    // 4. Register Access Token to Blacklist (On Logout)
    public void setBlackList(String accessToken, long remainingExpirationInSeconds) {
        redisTemplate.opsForValue().set(
                AT_PREFIX + accessToken,
                "logout",
                Duration.ofSeconds(remainingExpirationInSeconds)
        );
    }

    // 5. Check if Blacklisted (Called from JwtAuthenticationFilter)
    public boolean isBlackListed(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(AT_PREFIX + accessToken));
    }
}

```

## Best Practices

* **Refresh Token Rotation (RTR):** When reissuing an Access Token, it is highly recommended to treat the existing Refresh Token as one-time use, issue a completely new token set (AT+RT), and overwrite it in Redis.
* **Constantize Prefixes:** Redis overwrites data if keys collide. You MUST prevent key collisions by defining prefixes like `RT:` and `AT:` as constants.

## Common Pitfalls

* **RTR Concurrency Race Condition (Critical):** When applying RTR, if the client fires multiple parallel API requests (e.g., fetching images, user info, notifications) right after the AT expires, multiple reissue requests hit the server simultaneously. If the first request instantly overwrites/deletes the RT, the subsequent requests will fail with "Invalid RT," forcibly logging out a legitimate user. You must handle this either by serializing reissue requests on the frontend (e.g., Axios Interceptors with a lock) or by allowing a short Grace Period (e.g., 10 seconds) for the old RT on the server.
* **Permanent Blacklist Storage Bug (Critical):** If you fail to set an expiration time (TTL) when saving an Access Token to Redis upon logout, the data will accumulate permanently in memory, ultimately triggering a **Redis OOM (Out of Memory)**. You MUST parse the *remaining expiration time* of the Access Token and assign it as the TTL.
* **Unnecessary Serialization Overhead:** Do not inject the object-caching `RedisTemplate<String, Object>` to handle simple string tokens. It wastes performance and causes casting issues. Always use `StringRedisTemplate`.

## Related Skills

* `spring-jwt-auth`: Used to parse the remaining expiration time of the issued token (`JwtProvider`) and integrate this blacklist verification logic inside the custom authentication filter.
* `spring-redis-config`: The foundational configuration for the `StringRedisTemplate` injected into this service.
