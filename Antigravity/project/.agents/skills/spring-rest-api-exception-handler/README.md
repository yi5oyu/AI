---
name: spring-rest-api-exception-handler
description: Spring Boot 3.0 이상의 표준 규격인 ProblemDetail(RFC 7807)을 활용하여 REST API 예외 처리를 강제하고 규격화합니다.
argument-hint: "[추가하거나 처리할 예외 타입 (예: EntityNotFoundException 핸들러 추가, 검증 예외 처리 등)]"
source: "custom"
tags: ["java", "spring-boot", "exception-handling", "rest-api", "backend", "architecture"]
triggers:
  - "API 예외 처리 만들어"
  - "에러 핸들러 추가"
---

# Spring Boot REST API Exception Handler

Spring Boot 표준 규격(RFC 7807)에 맞춘 REST API 전용 중앙 집중식 예외 처리 구현 지침입니다.

## Overview

- HTTP API 요청 처리 중 발생하는 예외(Controller, Service 등)를 가로채어 클라이언트에게 일관된 JSON 에러 응답을 반환할 때 사용합니다.
- AI가 임의의 구형 커스텀 `ErrorResponse` DTO를 무분별하게 생성하는 것을 원천 차단하고, 공식 내장된 `ProblemDetail` 객체 사용을 엄격히 강제합니다.
- **사전 조건:** 애플리케이션 설정(`application.yml`)에 `spring.mvc.problemdetails.enabled=true`가 설정되어 있어야 프레임워크 레벨의 예외도 동일한 규격으로 자동 응답됩니다.

## When to Use This Skill

- 프로젝트 초기에 REST API의 에러 응답 스펙을 표준화해야 할 때.
- 특정 비즈니스 예외(예: `EntityNotFoundException`)에 대한 HTTP 상태 코드 및 메시지 매핑이 필요할 때.
- DTO 입력값 검증(`@Valid`) 실패 시 발생하는 에러를 필드별로 명확하게 가공하여 반환해야 할 때.

## How It Works

### Step 1: Define RestControllerAdvice

`@RestControllerAdvice` 어노테이션이 붙은 예외 처리 클래스(`GlobalExceptionHandler` 등)를 생성합니다. 
- Spring MVC의 표준 예외들을 자동으로 처리할 수 있도록 이 클래스가 반드시 `ResponseEntityExceptionHandler`를 상속(extends)하도록 구성하십시오.

### Step 2: Enforce ProblemDetail (Strict Rule)

응답 반환 타입으로 커스텀 클래스(예: `CustomErrorResponse`, `ErrorDto`)를 **절대 생성하지 마십시오.** - 무조건 `org.springframework.http.ProblemDetail` 객체만을 반환 타입으로 사용해야 합니다.

### Step 3: Implement Exception Handlers

`@ExceptionHandler`를 사용하여 잡고자 하는 예외를 명시합니다.
- `ProblemDetail.forStatusAndDetail(HttpStatus, String)` 팩토리 메서드를 사용하여 상태 코드와 상세 에러 메시지를 세팅합니다.
- 에러의 고유 식별을 위해 `problemDetail.setType(URI)`을 설정하는 것을 권장합니다.
- 추가적인 정보(예: 유효성 검사 실패 필드 목록)가 필요하다면 `problemDetail.setProperty("key", value)` 메서드를 활용하여 확장 프로퍼티를 추가하십시오.

## Examples

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 비즈니스 예외 처리 (예: 데이터 조회 실패)
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
        log.warn("Entity Not Found: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("[https://api.yourdomain.com/errors/not-found](https://api.yourdomain.com/errors/not-found)"));
        return problemDetail;
    }

    // 2. 비즈니스 로직 오류 및 잘못된 인자
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal Argument: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 3. 입력값 검증(@Valid) 예외 처리 (Spring Boot 3.x 최적화)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        // Spring Boot 3.0부터 MethodArgumentNotValidException 내부에 기본 ProblemDetail이 생성되어 있음
        ProblemDetail problemDetail = ex.getBody(); 
        problemDetail.setDetail("입력값이 올바르지 않거나 누락되었습니다.");
        
        Map<String, String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Unknown error",
                        (existing, replacement) -> existing // 키 중복 방지
                ));
        
        problemDetail.setProperty("invalid_params", validationErrors);
        
        return super.handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // 4. 핸들링되지 않은 서버 내부 오류
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllUncaughtException(Exception ex) {
        // 서버 에러는 반드시 에러 레벨로 스택 트레이스를 남김
        log.error("Internal Server Error Occurred in API Request", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다. 관리자에게 문의하세요.");
    }
}

```

## Best Practices

* **로그 레벨 철저 분리:** 클라이언트의 잘못된 요청(400, 404 등)은 `WARN` 레벨로 로깅하고, 개발자가 인지해야 하는 서버 내부 로직 결함(500)은 **반드시** 스택 트레이스와 함께 `ERROR` 레벨로 로깅하십시오.
* **명확한 예외 지정:** 뭉뚱그려 `Exception`을 잡기보다는, 가능한 한 구체적인 예외 클래스를 지정하여 예측 가능한 핸들링을 구성하십시오.

## Common Pitfalls

* **Scope 오해 (Critical):** 이 클래스(`@RestControllerAdvice`)는 HTTP API 요청을 처리하는 스레드 내부에서 발생한 예외만 가로챕니다. `@Async`, `@Scheduled`, Kafka/RabbitMQ Listener 등 백그라운드 스레드에서 발생한 예외는 절대 잡지 못하므로, 해당 로직들은 별도의 예외 처리를 구성해야 합니다.
* **Custom ErrorResponse 생성 금지 (Critical):** AI가 임의로 `record ErrorResponse(int status, String message)` 같은 커스텀 클래스를 만들어 응답하는 것을 **엄격히 금지합니다.** 무조건 `ProblemDetail`을 반환하십시오.
* **보안 정보 노출:** 500 에러 처리 시 예외의 실제 메시지(`ex.getMessage()`)를 클라이언트에게 그대로 노출하지 마십시오. 데이터베이스 쿼리나 서버 내부 구조가 유출될 위험이 있습니다.

## Related Skills

* `spring-rest-api`: 이 예외 처리기가 뒷받침될 때, API Service나 Controller 계층에서 에러 응답 포맷을 신경 쓰지 않고 부담 없이 비즈니스 로직 처리에만 집중할 수 있습니다.
