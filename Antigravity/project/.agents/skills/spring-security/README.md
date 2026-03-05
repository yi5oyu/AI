---
name: spring-security
description: Java 21 및 Spring Boot 3.x 이상(Spring Security 6.x+) 환경에서 Stateless REST API를 위한 전역 보안 설정과 CORS, 접근 권한을 구성합니다.
argument-hint: "[허용(Permit)할 API 엔드포인트 목록, CORS 허용 도메인, 추가할 커스텀 필터 (예: API 보안 설정하고 Swagger 및 회원가입 경로는 모두 허용)]"
source: "custom"
tags: ["java", "spring-boot", "security", "backend", "architecture", "cors", "java21"]
triggers:
  - "API 엔드포인트 설정"
  - "CORS 설정"
---

# Spring Boot Security Configuration

Java 21 및 Spring Security 6.x 이상의 최신 규격(Lambda DSL)을 준수하여, 세션을 사용하지 않는 순수 REST API 전용 보안 환경을 구축하기 위한 지침입니다.

## Overview

- 무거운 `WebSecurityConfigurerAdapter` 상속 방식을 완전히 배제하고, `@Bean` 등록 기반의 `SecurityFilterChain`을 구성합니다.
- REST API의 특성(Stateless)에 맞춰 불필요한 기본 설정(CSRF, Form Login, HTTP Basic, Session)을 모두 비활성화(`disable`)하여 서버 자원을 최적화합니다.
- 인증이 필요 없는 공개 엔드포인트(White-list)와 보호되는 엔드포인트를 명확히 분리하고, 프론트엔드 연동을 위한 전역 CORS 정책을 설정합니다.

## When to Use This Skill

- 프로젝트 초기에 전체 API의 접근 권한 제어(Authorization) 아키텍처를 설계할 때.
- 회원가입, 로그인, Swagger UI 등 인증 없이 접근해야 하는 URL 경로(White-list)를 열어주어야 할 때.
- React, Vue, 모바일 앱 등 클라이언트와의 원활한 통신을 위해 CORS(Cross-Origin Resource Sharing) 정책을 설정할 때.
- 직접 구현한 JWT 인증 필터(`JwtAuthenticationFilter`)를 Spring Security 기본 필터 체인에 안전하게 삽입할 때.

## How It Works

### Step 1: Base Configuration

`@Configuration`과 `@EnableWebSecurity` 어노테이션이 붙은 보안 설정 클래스(`SecurityConfig`)를 생성합니다. 컨트롤러 레벨의 세밀한 권한 제어를 위해 `@EnableMethodSecurity`를 함께 선언하는 것을 권장합니다.

### Step 2: Define SecurityFilterChain (Lambda DSL)

`SecurityFilterChain`을 반환하는 메서드를 만들고 `@Bean`으로 등록합니다. **반드시 Spring Security 6.1 이상의 최신 람다(Lambda) DSL 문법(`AbstractHttpConfigurer::disable`)을 사용해야 합니다.**
- `csrf()`, `formLogin()`, `httpBasic()`을 모두 비활성화합니다.
- `sessionManagement()`의 세션 생성 정책을 `SessionCreationPolicy.STATELESS`로 엄격하게 설정하여 서버 메모리(세션)를 사용하지 않도록 합니다.

### Step 3: Configure Authorization & CORS

`authorizeHttpRequests()`를 통해 엔드포인트별 권한을 설정합니다.
- **에러 디스패치 허용:** Spring Boot 3.x 이상에서는 내부 에러 핸들링 요청(`/error`)이 시큐리티에 막히지 않도록 반드시 `DispatcherType.ERROR`를 허용해야 합니다.
- 인증이 필요 없는 URL은 `requestMatchers("...").permitAll()`로 열어두며, 그 외 나머지는 `anyRequest().authenticated()`로 잠급니다.
- 프론트엔드 도메인 허용을 위해 `cors()` 설정을 활성화하고 별도의 `CorsConfigurationSource` 빈을 등록합니다.

### Step 4: Insert Custom Filter

별도로 구현된 JWT 토큰 검증 필터를 `addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class)`를 사용하여 기본 폼 로그인 인증 필터 위치 앞단에 삽입합니다.

## Examples

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 컨트롤러 메서드 단의 @PreAuthorize 사용을 위해 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT 필터 (spring-jwt-auth 스킬을 통해 구현된 필터를 주입받음)
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 인증이 필요 없는 공개 API 목록 (White-list)
    private static final String[] WHITE_LIST_URL = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API 최적화: 기본 보안 기능 비활성화 (Spring Security 6.1+ 람다 문법)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                
                // 2. CORS 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 세션 관리: STATELESS 설정 (서버에 세션 저장 안 함)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. 엔드포인트 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot 3.x+ : 내부 에러 요청을 시큐리티가 차단하지 않도록 허용
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(WHITE_LIST_URL).permitAll() // 공개 엔드포인트 허용
                        .anyRequest().authenticated()                // 그 외 모든 요청은 인증 필요
                )

                // 5. 커스텀 필터 삽입: JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Java 21 표준 불변 컬렉션(List.of) 사용
        // 실제 운영 환경에서는 허용할 프론트엔드 도메인을 명확히 지정하십시오.
        configuration.setAllowedOrigins(List.of("http://localhost:3000")); 
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Preflight 요청 캐싱 시간 (1시간)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

```

## Best Practices

* **메서드 레벨 보안 혼용:** `SecurityConfig` 파일에는 중앙 통제가 필요한 White-list만 명시하고, 각 도메인별 세부 권한(예: ADMIN만 접근 가능한 API)은 컨트롤러 계층에서 `@PreAuthorize("hasRole('ADMIN')")` 어노테이션을 사용하여 관리하는 것이 유지보수에 유리합니다.
* **보안 예외 핸들링 추가:** 추후 `AuthenticationEntryPoint` (인증 실패 - 401)와 `AccessDeniedHandler` (인가 실패 - 403)를 구현하여 `.exceptionHandling()`에 추가하면, 앞서 만든 `ProblemDetail` 전역 예외 처리 스펙과 보안 에러 응답 포맷을 일치시킬 수 있습니다.

## Common Pitfalls

* **DispatcherType.ERROR 누락 (Critical):** Spring Security 6.x(Spring Boot 3.x)부터 내부 에러 포워딩(`/error`)도 시큐리티 필터를 거칩니다. 이 설정을 누락하면 API 로직에서 발생한 404나 500 에러가 클라이언트에게는 403 Forbidden으로 둔갑하여 응답되는 치명적인 디버깅 지옥에 빠집니다. 반드시 `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`을 추가하십시오.
* **Deprecated 메서드 사용 (Critical):** `antMatchers()`, `mvcMatchers()`는 완전히 삭제되었습니다. AI가 이를 생성하지 않도록 **무조건 `requestMatchers()`를 사용**해야 합니다.
* **필터 예외 처리 스코프 (Critical):** Security Filter 체인(`JwtAuthenticationFilter` 등)은 Spring MVC의 `DispatcherServlet`보다 앞단에 위치합니다. 따라서 필터 내부에서 발생한 예외(예: 토큰 만료)는 `@RestControllerAdvice`가 절대 잡을 수 없습니다.

## Related Skills

* `spring-jwt-auth`: 이 SecurityConfig 설정에 주입되어 실제 토큰을 추출하고 검증할 `JwtAuthenticationFilter` 및 토큰 프로바이더 로직을 구현할 때 사용합니다.
* `spring-rest-api-controller-unit-test`: 보안이 적용된 컨트롤러를 `@WebMvcTest`로 격리 테스트할 때 `@WithMockUser`와 `.with(csrf())`를 사용하여 보안 필터를 우회하는 방법을 확인합니다.
