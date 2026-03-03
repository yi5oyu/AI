---
name: spring-rest-api-exception-handler
description: Enforce and standardize REST API exception handling using the Spring Boot 3.0+ standard ProblemDetail (RFC 7807).
argument-hint: "[추가하거나 처리할 예외 타입 (예: EntityNotFoundException 핸들러 추가, 검증 예외 처리 등)]"
source: "custom"
tags: ["java", "spring-boot", "exception-handling", "rest-api", "backend", "architecture"]
triggers:
  - "API 예외 처리 만들어"
  - "에러 핸들러 추가"
---

# Spring Boot REST API Exception Handler

Detailed guidelines for implementing a centralized REST API exception handler complying with the Spring Boot standard specification (RFC 7807).

## Overview

- Use to intercept exceptions (from Controllers, Services, etc.) occurring during HTTP API request processing and return a consistent JSON error response to the client.
- Radically prevents the AI from indiscriminately generating legacy custom `ErrorResponse` DTOs and strictly enforces the use of the officially built-in `ProblemDetail` object.
- **Prerequisite:** Ensure `spring.mvc.problemdetails.enabled=true` is set in the application configuration (`application.yml`) so that framework-level exceptions are also automatically formatted to the same standard.

## When to Use This Skill

- When standardizing the error response specification for REST APIs at the beginning of a project.
- When mapping HTTP status codes and messages for specific business exceptions (e.g., `EntityNotFoundException`) thrown by the Service layer.
- When you need to clearly process and return field-specific errors occurring from DTO validation (`@Valid`) failures.

## How It Works

### Step 1: Define RestControllerAdvice

Create an exception handling class (e.g., `GlobalExceptionHandler`) annotated with `@RestControllerAdvice`.
- It is strongly recommended that this class **extends `ResponseEntityExceptionHandler`** so that standard Spring MVC exceptions can be handled automatically.

### Step 2: Enforce ProblemDetail (Strict Rule)

**NEVER create custom classes** (e.g., `CustomErrorResponse`, `ErrorDto`) as the response return type. 
- You MUST unconditionally use only the `org.springframework.http.ProblemDetail` object as the return type.

### Step 3: Implement Exception Handlers

Specify the exceptions to catch using `@ExceptionHandler`.
- Use the `ProblemDetail.forStatusAndDetail(HttpStatus, String)` factory method to set the status code and detailed error message.
- It is recommended to set `problemDetail.setType(URI)` for unique identification of the error type.
- If additional information is needed (e.g., a list of validation failure fields), use the `problemDetail.setProperty("key", value)` method to add custom extension properties.



## Examples

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. Business Exception Handling (e.g., Data retrieval failure)
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
        log.warn("Entity Not Found: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setType(URI.create("[https://api.yourdomain.com/errors/not-found](https://api.yourdomain.com/errors/not-found)"));
        return problemDetail;
    }

    // 2. Business Logic Errors & Invalid Arguments
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal Argument: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 3. Input Validation (@Valid) Exception Handling (Optimized for Spring Boot 3.x)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        
        // Spring Boot 3.0+ MethodArgumentNotValidException already contains a default ProblemDetail inside
        ProblemDetail problemDetail = ex.getBody(); 
        problemDetail.setDetail("Invalid or missing input values.");
        
        Map<String, String> validationErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Unknown error",
                        (existing, replacement) -> existing // Prevent duplicate keys
                ));
        
        problemDetail.setProperty("invalid_params", validationErrors);
        
        return super.handleExceptionInternal(ex, problemDetail, headers, status, request);
    }

    // 4. Unhandled Internal Server Errors
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllUncaughtException(Exception ex) {
        // Server errors must be logged at the ERROR level with the stack trace
        log.error("Internal Server Error Occurred in API Request", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred. Please contact the administrator.");
    }
}

```

## Best Practices

* **Strict Log Level Separation:** Log client-side bad requests (400, 404, etc.) at the `WARN` level. Server internal logic defects (500) that developers need to be aware of MUST be logged at the `ERROR` level along with the stack trace.
* **Specify Exact Exceptions:** Instead of catching a generic `Exception`, specify the exact exception class as much as possible to construct predictable handling.

## Common Pitfalls

* **Scope Misunderstanding (Critical):** This class (`@RestControllerAdvice`) only intercepts exceptions that occur within the thread processing the HTTP API request. It NEVER catches exceptions thrown in background threads like `@Async`, `@Scheduled`, or Kafka/RabbitMQ Listeners. Those require separate exception handling logic.
* **Custom ErrorResponse Prohibition (Critical):** The AI is **strictly prohibited** from arbitrarily creating custom classes like `record ErrorResponse(int status, String message)` to respond. Unconditionally return a `ProblemDetail`.
* **Security Information Exposure:** When handling 500 errors, do not expose the actual exception message (`ex.getMessage()`) directly to the client. There is a risk of leaking database queries or internal server structures.

## Related Skills

* `spring-rest-api`: When backed by this exception handler, you can focus purely on business logic in the API Service or Controller layers, freely throwing exceptions like `IllegalArgumentException` without worrying about formatting the error response.
