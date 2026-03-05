---
name: spring-security-rest-api
description: Configure global security settings (SecurityFilterChain), CORS, and access control for a Stateless REST API in a Java 21 and Spring Boot 3.x+ (Spring Security 6.x+) environment.
argument-hint: "[허용(Permit)할 API 엔드포인트 목록, CORS 허용 도메인, 추가할 커스텀 필터 (예: API 보안 설정하고 Swagger 및 회원가입 경로는 모두 허용)]"
source: "custom"
tags: ["java", "spring-boot", "security", "backend", "architecture", "cors", "java21"]
triggers:
  - "API 엔드포인트 설정"
  - "CORS 설정"
---

# Spring Boot REST API Security Configuration

Guidelines for building a dedicated, session-less security environment for pure REST APIs, adhering to the latest Spring Security 6.x+ specification (Lambda DSL) and Java 21 standards.

## Overview

- Completely excludes the heavy `WebSecurityConfigurerAdapter` inheritance approach, configuring a `@Bean` registration-based `SecurityFilterChain` instead.
- Optimizes server resources by disabling (`disable`) unnecessary default settings (CSRF, Form Login, HTTP Basic, Session) to align with the Stateless nature of REST APIs.
- Clearly separates public endpoints (White-list) requiring no authentication from protected endpoints, and sets global CORS policies for frontend integration.

## When to Use This Skill

- When designing the Authorization architecture for the entire API at the beginning of a project.
- When opening URL paths (White-list) that must be accessed without authentication, such as signup, login, and Swagger UI.
- When setting up CORS (Cross-Origin Resource Sharing) policies for seamless communication with clients like React, Vue, or mobile apps.
- When safely inserting a custom JWT authentication filter (`JwtAuthenticationFilter`) into the default Spring Security filter chain.

## How It Works

### Step 1: Base Configuration

Create a security configuration class (`SecurityConfig`) annotated with `@Configuration` and `@EnableWebSecurity`. It is also recommended to declare `@EnableMethodSecurity` to allow fine-grained access control at the controller method level.

### Step 2: Define SecurityFilterChain (Lambda DSL)

Create a method that returns a `SecurityFilterChain` and register it as a `@Bean`. **You MUST use the latest Lambda DSL syntax (`AbstractHttpConfigurer::disable`) introduced in Spring Security 6.1+.**
- Disable `csrf()`, `formLogin()`, and `httpBasic()`.
- Strictly set the session creation policy in `sessionManagement()` to `SessionCreationPolicy.STATELESS` so that server memory (sessions) is not used.

### Step 3: Configure Authorization & CORS

Configure permissions per endpoint via `authorizeHttpRequests()`.
- **Allow Error Dispatch (Crucial):** In Spring Boot 3.x+, you MUST permit `DispatcherType.ERROR` so that internal error handling requests (`/error`) are not blocked by Spring Security.
- Open URLs that do not require authentication using `requestMatchers("...").permitAll()`. Lock all other requests with `anyRequest().authenticated()`.
- Enable the `cors()` configuration and register a separate `CorsConfigurationSource` bean to allow frontend domains.

### Step 4: Insert Custom Filter

Insert the separately implemented JWT token verification filter before the default form login authentication filter location using `addFilterBefore(customFilter, UsernamePasswordAuthenticationFilter.class)`.

## Examples

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enable @PreAuthorize at the controller method level
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT Filter (injected after being implemented via the spring-jwt-auth skill)
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Public API list requiring no authentication (White-list)
    private static final String[] WHITE_LIST_URL = {
            "/api/v1/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. REST API Optimization: Disable default security features (Spring Security 6.1+ Lambda syntax)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                
                // 2. CORS Configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Session Management: STATELESS setting (No session saved on the server)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Endpoint Authorization Settings
                .authorizeHttpRequests(auth -> auth
                        // Spring Boot 3.x+: Allow internal error requests so they aren't blocked by security
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(WHITE_LIST_URL).permitAll() // Allow public endpoints
                        .anyRequest().authenticated()                // All other requests require authentication
                )

                // 5. Insert Custom Filter: Place JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Uses Java 21 standard immutable collections (List.of)
        // Explicitly specify the frontend domains to allow in a real production environment.
        configuration.setAllowedOrigins(List.of("http://localhost:3000")); 
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // Preflight request caching time (1 hour)

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

* **Mix with Method-Level Security:** For better maintainability, only specify centrally controlled White-lists in the `SecurityConfig` file. Manage domain-specific granular permissions (e.g., APIs accessible only by ADMIN) at the controller layer using the `@PreAuthorize("hasRole('ADMIN')")` annotation.
* **Add Security Exception Handling:** Later, implement `AuthenticationEntryPoint` (Authentication Failure - 401) and `AccessDeniedHandler` (Authorization Failure - 403) and add them to `.exceptionHandling()`. This aligns the security error response format with the previously created `ProblemDetail` global exception handling specification.

## Common Pitfalls

* **Missing DispatcherType.ERROR (Critical):** Starting from Spring Security 6.x (Spring Boot 3.x), internal error forwarding (`/error`) also passes through the security filter. If this setting is omitted, 404 or 500 errors occurring in API logic will be masked as 403 Forbidden to the client, leading to a debugging nightmare. You MUST add `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.
* **Using Deprecated Methods (Critical):** `antMatchers()` and `mvcMatchers()` have been completely removed. **You MUST use `requestMatchers()**` so the AI does not generate legacy code. Also, method chaining like `.csrf().disable()` is deprecated; you must use Lambda expressions.
* **Filter Exception Handling Scope (Critical):** The Security Filter Chain (like `JwtAuthenticationFilter`) sits in front of Spring MVC's `DispatcherServlet`. Therefore, exceptions thrown inside the filter (e.g., Token Expired) can NEVER be caught by `@RestControllerAdvice`. Filter exceptions must be handled independently.

## Related Skills

* `spring-jwt-auth`: Use this to implement the `JwtAuthenticationFilter` and token provider logic that will be injected into this SecurityConfig to actually extract and verify tokens.
* `spring-rest-api-controller-unit-test`: Check how to bypass security filters using `@WithMockUser` and `.with(csrf())` when performing isolated tests on secured controllers with `@WebMvcTest`.
