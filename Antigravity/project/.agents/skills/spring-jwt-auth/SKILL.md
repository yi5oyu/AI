---
name: spring-jwt-auth
description: Implement JWT (JSON Web Token) issuance, signature verification logic, and a custom authentication filter using the latest JJWT (0.12.x) library in a Java 21 and Spring Boot 3.x+ environment.
argument-hint: "[토큰 만료 시간, 권한(Role) 정보, 검증 로직 디테일 (예: Access Token 발급 프로바이더와 Bearer 검증 필터 작성)]"
source: "custom"
tags: ["java", "spring-boot", "security", "jwt", "authentication", "backend", "java21"]
triggers:
  - "JWT 구현"
  - "로그인 인증 처리"
---

# Spring Boot JWT Authentication & Filter

Guidelines for implementing the core of a Stateless REST API: the JWT issuance and signature verification class (`JwtProvider`), and a custom filter (`JwtAuthenticationFilter`) that intercepts HTTP requests to validate tokens using the latest standards.

## Overview

- Extracts and verifies the `Bearer` token from the client's HTTP `Authorization` header, saving the Authentication object in the Spring Security `SecurityContext` if valid.
- Completely avoids server memory or sessions, granting authorization based solely on the cryptographic signature of the token to verify the user's identity (PK, Role, etc.).
- Excludes legacy JWT libraries (0.11.x and below) with security vulnerabilities, enforcing the safe builder pattern of the latest JJWT 0.12.x specification.

## When to Use This Skill

- When implementing a login (authentication) API and needing to generate and return an Access Token and Refresh Token based on user information.
- When actually implementing the `JwtAuthenticationFilter` to be registered in the global `SecurityFilterChain` set up by the `spring-security-rest-api` skill.
- When safely parsing a token from a client's request header to verify its expiration or check for signature forgery.

## How It Works

### Step 1: Add Dependencies

Add the latest version of JJWT (0.12.x or higher) dependencies to `build.gradle`.
- All three libraries (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) must be appropriately added for the runtime/compile environment.

### Step 2: Implement `JwtProvider` (Token Utility)

Create a `@Component` dedicated to token generation and parsing.
- **Key Initialization:** Safely initialize a `javax.crypto.SecretKey` object by Base64 decoding the Secret Key (at least 32 bytes/256 bits) defined in `application.yml`.
- **Enforce Latest Syntax:** You MUST use `Jwts.builder().signWith(key).compact()` for token creation, and `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` for verification.

### Step 3: Implement `JwtAuthenticationFilter`

Create a filter that extends `OncePerRequestFilter`.
- Extract the value in the form of `Authorization: Bearer <token>` from the HTTP Request header.
- Verify the token's validity using `JwtProvider`. If valid, create a `UsernamePasswordAuthenticationToken` object and store it in the `SecurityContextHolder`.

## Examples

```java
// 1. JwtProvider (Token Generation and Signature Verification)
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidityInMilliseconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-validity-in-seconds}") long accessTokenValidityInSeconds) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityInMilliseconds = accessTokenValidityInSeconds * 1000;
    }

    // Token Issuance (JJWT 0.12.x latest immutable builder syntax)
    public String createAccessToken(String userId, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(userId)
                // It is recommended to save the role information in the "ROLE_USER" format.
                .claim("role", role) 
                .issuedAt(now)
                .expiration(validity)
                .signWith(key) // Latest safe signing method
                .compact();
    }

    // Token Parsing (JJWT 0.12.x latest parser builder syntax)
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key) // Latest signature verification method
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Token Validity Verification
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // In practice, granularly log ExpiredJwtException, MalformedJwtException, etc.
            return false;
        }
    }
}

// 2. JwtAuthenticationFilter (HTTP Request Interception and Authorization)
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        String token = resolveToken(request);

        // Process authentication ONLY if the token exists and is valid.
        // If invalid, it skips setting the authentication object, and the Security Chain blocks it with a 401.
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.getClaims(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // Use Java 21 standard List.of
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            
            // Save the authentication object in the SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Pass the request to the next filter
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

```

## Best Practices

* **Ensure Secret Key Length (Crucial):** To safely use the HS256 algorithm, the Secret Key in `application.yml` must be at least 256 bits (32 bytes alphanumeric mix). Using a short key will throw an exception (`WeakKeyException`) when JJWT starts.
* **Apply Refresh Token Separation:** Issue short-lived Access Tokens (e.g., under 30 minutes) and introduce a secure architecture where long-lived Refresh Tokens are stored in Redis or a DB for periodic re-issuance (Rotation).

## Common Pitfalls

* **Missing Role Prefix (Critical):** Spring Security's `@PreAuthorize("hasRole('USER')")` internally checks if the role name is `ROLE_USER`. If you only put `USER` in the claim during JWT issuance and pass it through the filter, the token is valid, but a 403 Forbidden error occurs. You MUST attach `ROLE_` when putting it into the token or add it in the filter.
* **Using Deprecated JJWT Syntax (Critical):** Legacy syntax like `Jwts.parser().setSigningKey(key).parseClaimsJws(token)` (0.11.x or below) triggers severe security warnings. Because the AI might generate legacy code due to past training data, you MUST force the latest 0.12.x syntax like **`verifyWith(key).build().parseSignedClaims(token)`**.
* **Filter Exception Handling Scope (Critical):** If a token verification failure exception like `ExpiredJwtException` occurs and is thrown out of the filter, `@RestControllerAdvice` can NEVER catch it. To respond to the client with this error in a standardized JSON format (`ProblemDetail`), you must delegate it to Spring Security's `AuthenticationEntryPoint`.

## Related Skills

* `spring-security-rest-api`: Global security configuration guidelines for inserting this filter in front of the default form login filter in the SecurityFilterChain.
* `spring-rest-api-exception-handler`: Reference this when handling authentication/authorization exceptions passed from the filter into a consistent response object (`ProblemDetail`).
