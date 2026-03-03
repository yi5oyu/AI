---
name: spring-rest-api-controller-unit-test
description: Write BDD-style unit tests for the Spring Boot REST API Controller (Web Layer) using @WebMvcTest and Mockito.
argument-hint: "[테스트할 대상 API 컨트롤러 및 특정 시나리오 (예: DocumentController 게시글 생성 201 성공 및 400 실패 단위 테스트)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "junit5", "mockito", "rest-api", "webmvctest"]
triggers:
  - "컨트롤러 단위 테스트 작성"
  - "API 엔드포인트 단위 테스트"
---

# Spring Boot REST API Controller Unit Test

Guidelines for writing fast and isolated unit tests in a Spring Boot environment that strictly exclude the database and complex business logic (Service), focusing solely on the behavior of the Controller (Web Layer).

## Overview

- Test execution is extremely fast because it does not load the entire application, only the target controller and web-related beans.
- By injecting fake objects (Mocks) using Mockito instead of actual Service objects, it tests only the inherent role of the 'Web API shell', such as HTTP request/response routing, parameter binding, `@Valid` validation, and returning status codes.

## When to Use This Skill

- When the business logic or DB is not yet complete, but you want to first verify and confirm the API endpoint specifications (URL, status code, JSON format) agreed upon with frontend developers.
- When lightly testing various failure cases to ensure DTO validation (e.g., `@NotBlank`, `@NotNull`) works correctly.
- When checking if the global exception handler (`@RestControllerAdvice`) correctly catches controller exceptions and returns them in the precise `ProblemDetail` (RFC 7807) format.

## How It Works

### Step 1: Test Environment Setup

**NEVER use the heavy `@SpringBootTest`** for integration testing. **You MUST use `@WebMvcTest(TargetController.class)`** to lightly load only the target controller.
- `MockMvc` and `ObjectMapper` are injected via `@Autowired`.
- The Service layer that the controller depends on **MUST be injected as a fake object using `@MockitoBean`**. (Spring Boot 3.4+ latest specification. Never use `@Transactional` in this test.)

### Step 2: BDD Mocking in Given

Following the BDD pattern, pre-configure the return values (Stubbing) of the fake service logic in the `// Given` section using **BDDMockito**'s `given(...).willReturn(...)` method.

### Step 3: Execution with MockMvc

Execute the HTTP request using `MockMvc` in the `// When` section. Serialize the Request DTO into a JSON string using `ObjectMapper` and send it in the body (`content()`).

### Step 4: Assertion

Verify the HTTP status code and response JSON fields using `MockMvc`'s `andExpect()` in the `// Then` section. Business logic or DB state are not concerns of this test and should never be verified. Finally, verify the number of invocations of the Mock object using `then(service).should()`.

## Examples

```java
@WebMvcTest(DocumentController.class)
@MockBean(JpaMetamodelMappingContext.class) // To prevent JPA Auditing context errors
class DocumentControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Spring Boot 3.4+ latest specification (replaces @MockBean)
    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("Returns 201 Created with response DTO when valid input is provided")
    @WithMockUser // Prevents Spring Security 401 Unauthorized errors
    void createDocument_Success() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("Unit Test Title", "Unit test body content.");
        String requestJson = objectMapper.writeValueAsString(request);

        DocumentResponse mockResponse = new DocumentResponse(1L, "Unit Test Title", "Unit test body content.");
        
        // Setting up fake service return value using BDDMockito
        given(documentService.createDocument(any(DocumentCreateRequest.class)))
                .willReturn(mockResponse);

        // When
        ResultActions resultActions = mockMvc.perform(post("/api/v1/documents")
                .with(csrf()) // Inject CSRF token for POST requests
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));

        // Then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Unit Test Title"));
                
        // Verify interaction: ensure the service logic was called exactly once
        then(documentService).should(times(1)).createDocument(any(DocumentCreateRequest.class));
    }

    @Test
    @DisplayName("Returns 400 Bad Request and ProblemDetail without calling service logic when @Valid fails")
    @WithMockUser
    void createDocument_Fail_WhenValidationFails() throws Exception {
        // Given
        DocumentCreateRequest invalidRequest = new DocumentCreateRequest("", "Title is empty.");
        String requestJson = objectMapper.writeValueAsString(invalidRequest);

        // When
        ResultActions resultActions = mockMvc.perform(post("/api/v1/documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));

        // Then
        resultActions
                .andExpect(status().isBadRequest())
                // Verify ProblemDetail format from spring-rest-api-exception-handler
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.invalid_params.title").exists());
                
        // Verify no interaction: rejected at the controller level due to Validation error
        then(documentService).shouldHaveNoInteractions();
    }
}

```

## Best Practices

* **Enforce Static Imports:** To maximize code readability, methods like `MockMvcRequestBuilders.post`, `MockMvcResultMatchers.status`, and `BDDMockito.given` **MUST be statically imported** to keep the code short and concise.
* **Strict Separation of Concerns:** Do not try to check if data was saved in the DB during a controller unit test. Focus 100% on "Did the controller receive parameters correctly and return the correct HTTP status code?".

## Common Pitfalls

* **JPA Auditing Context Error (Critical):** If `@EnableJpaAuditing` is declared on the main class, an error occurs when loading `@WebMvcTest` because JPA beans are missing. You must add `@MockBean(JpaMetamodelMappingContext.class)` to the test class, or separate the Auditing configuration into a dedicated `@Configuration` class.
* **Using @SpringBootTest:** Attaching the heavy `@SpringBootTest` to a web layer unit test loads the entire Spring context, defeating the purpose of a unit test (fast speed). Always use `@WebMvcTest`.
* **Missing Spring Security Compatibility:** If Spring Security is applied to the project, attach `@WithMockUser` at the class or method level to bypass authentication/authorization, and unconditionally append `.with(csrf())` to the `MockMvc` chaining when testing data modification requests (POST/PUT/DELETE).

## Related Skills

* `spring-rest-api-service-unit-test`: Use this next to write isolated unit tests for the business logic (Service) after verifying the controller.
* `spring-rest-api-integration-test`: Use this to perform end-to-end integration testing connecting the actual DB and service logic after all unit tests have passed.
