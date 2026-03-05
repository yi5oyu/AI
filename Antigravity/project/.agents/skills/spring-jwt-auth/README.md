---
name: spring-jwt-auth
description: Java 21 및 Spring Boot 3.x 이상 환경에서 최신 JJWT(0.12.x) 라이브러리를 사용하여 JWT(JSON Web Token) 발급, 서명 검증 로직 및 커스텀 인증 필터를 구현합니다.
argument-hint: "[토큰 만료 시간, 권한(Role) 정보, 검증 로직 디테일 (예: Access Token 발급 프로바이더와 Bearer 검증 필터 작성)]"
source: "custom"
tags: ["java", "spring-boot", "security", "jwt", "authentication", "backend", "java21"]
triggers:
  - "JWT 구현"
  - "로그인 인증 처리"
---

# Spring Boot JWT Authentication & Filter

무상태(Stateless) REST API의 핵심인 JWT 발급 및 서명 검증 클래스(`JwtProvider`)와 HTTP 요청을 가로채어 토큰을 확인하는 커스텀 필터(`JwtAuthenticationFilter`)의 최신 표준 구현 지침입니다.

## Overview

- 클라이언트가 보낸 HTTP `Authorization` 헤더의 `Bearer` 토큰을 추출하고 검증하여, 유효한 경우 Spring Security의 `SecurityContext`에 인증 객체(Authentication)를 저장합니다.
- 서버 메모리나 세션을 전혀 사용하지 않으며, 토큰의 암호학적 서명(Signature)만을 검증하여 사용자의 식별자(PK, Role 등)를 인가합니다.
- 보안 취약점이 있는 레거시 JWT 라이브러리(0.11.x 이하)를 배제하고, 가장 최신 스펙인 JJWT 0.12.x 버전의 안전한 빌더 패턴을 강제합니다.

## When to Use This Skill

- 로그인(인증) API를 구현하며 유저 정보를 바탕으로 Access Token과 Refresh Token을 생성하여 응답해야 할 때.
- `spring-security-rest-api` 스킬에서 설정한 전역 필터 체인(`SecurityFilterChain`)에 등록할 `JwtAuthenticationFilter`를 실제 구현할 때.
- 클라이언트의 요청 헤더에서 토큰을 파싱하고 만료 여부나 서명 위조 여부를 안전하게 검증해야 할 때.

## How It Works

### Step 1: Add Dependencies

최신 버전의 JJWT(0.12.x 이상) 의존성을 `build.gradle`에 추가합니다.
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` 3가지 라이브러리가 모두 런타임/컴파일 환경에 알맞게 추가되어야 합니다.

### Step 2: Implement `JwtProvider` (Token Utility)

토큰의 생성과 파싱을 전담하는 `@Component`를 만듭니다.
- **Key Initialization:** `application.yml`에 정의된 Secret Key(최소 32바이트/256비트 이상)를 Base64 디코딩하여 `javax.crypto.SecretKey` 객체로 안전하게 초기화합니다.
- **최신 문법 강제:** 토큰 생성 시에는 `Jwts.builder().signWith(key).compact()`, 검증 시에는 `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` 문법을 반드시 사용해야 합니다.

### Step 3: Implement `JwtAuthenticationFilter`

`OncePerRequestFilter`를 상속받는 필터를 생성합니다.
- HTTP Request 헤더에서 `Authorization: Bearer <token>` 형태의 값을 추출합니다.
- `JwtProvider`를 통해 토큰의 유효성을 검증하고, 정상이라면 `UsernamePasswordAuthenticationToken` 객체를 생성해 `SecurityContextHolder`에 저장합니다.

## Examples

```java
// 1. JwtProvider (토큰 발급 및 서명 검증)
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

    // 토큰 발급 (JJWT 0.12.x 최신 불변 빌더 문법)
    public String createAccessToken(String userId, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(userId)
                // 권한 정보 저장 시 "ROLE_USER" 형태로 저장하는 것을 권장합니다.
                .claim("role", role) 
                .issuedAt(now)
                .expiration(validity)
                .signWith(key) // 최신 안전한 서명 방식
                .compact();
    }

    // 토큰 파싱 (JJWT 0.12.x 최신 파서 빌더 문법)
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key) // 최신 서명 검증 방식
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // 실제 실무에서는 ExpiredJwtException, MalformedJwtException 등을 세분화하여 로깅
            return false;
        }
    }
}

// 2. JwtAuthenticationFilter (HTTP 요청 가로채기 및 인가)
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

        // 토큰이 존재하고 유효한 경우에만 인증 처리
        // 유효하지 않으면 인증 객체를 설정하지 않고 넘어가며, 시큐리티 체인이 401로 차단합니다.
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.getClaims(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            // Java 21 표준 List.of 사용
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            
            // SecurityContext에 인증 객체 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터로 요청 전달
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

* **Secret Key 길이 확보 (중요):** HS256 알고리즘을 안전하게 사용하려면 `application.yml`의 Secret Key는 최소 256비트(32바이트 영문/숫자 혼합) 이상이어야 합니다. 짧은 키를 사용하면 JJWT 기동 시 예외(`WeakKeyException`)가 발생합니다.
* **Refresh Token 분리 적용:** Access Token의 수명은 30분 이내로 짧게 발급하고, 긴 수명을 가진 Refresh Token은 Redis나 DB에 저장하여 주기적으로 재발급(Rotation)하는 안전한 아키텍처를 도입하십시오.

## Common Pitfalls

* **권한(Role) Prefix 누락 (Critical):** Spring Security의 `@PreAuthorize("hasRole('USER')")`는 내부적으로 권한 이름이 `ROLE_USER`인지 검사합니다. JWT 발급 시 클레임에 `USER`라고만 넣고 필터를 통과시키면, 토큰은 정상인데 403 Forbidden 에러가 발생합니다. 반드시 토큰에 넣을 때 `ROLE_`을 붙이거나 필터에서 추가해 주어야 합니다.
* **Deprecated JJWT 문법 사용 (Critical):** `Jwts.parser().setSigningKey(key).parseClaimsJws(token)` 같은 0.11.x 이하의 구형 문법은 심각한 보안 경고를 냅니다. AI는 과거 학습 데이터로 인해 구형 코드를 생성할 수 있으므로, 반드시 **`verifyWith(key).build().parseSignedClaims(token)`** 같은 0.12.x 최신 문법을 강제해야 합니다.
* **Filter 예외 처리 스코프 (Critical):** `ExpiredJwtException` 등 토큰 검증 실패 예외가 발생했을 때 필터 내부에서 예외를 밖으로 던져버리면 `@RestControllerAdvice`가 절대 잡지 못합니다. 이 에러를 클라이언트에게 규격화된 JSON 포맷(`ProblemDetail`)으로 응답하려면 시큐리티의 `AuthenticationEntryPoint`로 위임해야 합니다.

## Related Skills

* `spring-security`: 이 필터를 SecurityFilterChain의 기본 폼 로그인 필터 앞단에 끼워 넣기 위한 전역 보안 설정 지침입니다.
* `spring-rest-api-exception-handler`: 필터에서 넘어온 인증/인가 예외를 일관된 응답 객체(ProblemDetail)로 처리할 때 참고합니다.
