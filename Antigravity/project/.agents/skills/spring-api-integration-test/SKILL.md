---
name: spring-rest-api-integration-test
description: Write Spring Boot REST API integration tests that connect and verify the entire layer (Controller-Service-Repository) and the database.
argument-hint: "[테스트할 컨트롤러/메서드 및 시나리오 (예: DocumentController 문서 생성 및 DB 저장 통합 테스트)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "junit5", "rest-api", "integration-test", "h2"]
triggers:
  - "통합 테스트 작성"
  - "DB 테스트 작성"
---

# Spring Boot REST API Integration Test

Practical guidelines for writing slim, fast, and intuitive integration tests by removing unnecessary configurations.

## Overview

- **Speed First:** Uses an H2 in-memory database by default, allowing it to execute instantly and quickly in local environments or CI pipelines.
- **Maintenance Focused:** Recommends Java code (`Repository`) based data preparation over complex `.sql` files for better maintainability.
- **Standard Oriented:** Prioritizes standard JPA features over specific DB-exclusive functions to increase portability.

## How It Works

### Step 1: Minimal Environment Setup

- **Annotations:** Use only three basic annotations: `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@Transactional`.
- **Profiles:** Load the test configuration (like H2 settings) from `application-test.yml` using `@ActiveProfiles("test")`.
- Inject `MockMvc` along with `ObjectMapper` to be used for JSON serialization.

### Step 2: Lean Data Setup

- **Java First:** Prepare test data using `repository.save()` whenever possible. (This is advantageous for refactoring as entity changes can be immediately caught as compile errors.)
- **Select @Sql:** Use `@Sql` selectively only when associations are too complex and the Java code becomes excessively long.

### Step 3: BDD Execution & Verification

- `// Given`: Save the minimum necessary data to the DB or create a Request DTO.
- `// When`: Call the API using `mockMvc.perform()`.
- `// Then`: Verify the HTTP status code and the response JSON. Finally, MUST cross-verify with `AssertJ` that the data was correctly reflected in the DB through the Repository.

## Examples

```java
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Automatically rolls back after the test completes
class DocumentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DocumentRepository documentRepository;

    @Test
    @DisplayName("Returns 201 response and saves data to DB when a new document is created")
    void createDocument_Success() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("Practical Test", "Integration test content.");
        String content = objectMapper.writeValueAsString(request); // Avoid JSON hardcoding

        // When & Then: Verify API response
        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Practical Test"));

        // Then: Final verification of DB state
        List<Document> documents = documentRepository.findAll();
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getTitle()).isEqualTo("Practical Test");
        assertThat(documents.get(0).getContent()).isEqualTo("Integration test content.");
    }
}

```

## Best Practices

* **No Over-Engineering:** Rather than verifying every single field, reduce the weight of the test code by focusing your assertions on fields that contain core business logic.
* **Implicit Precision:** When comparing dates, verify only up to seconds or use AssertJ's `isCloseTo` to prevent failures caused by differences in DB precision.
* **Header-based Versioning:** If the project manages versions via headers or parameters, add `.header()` or `.param()` instead of versioning the URI.

## Common Pitfalls

* **Prohibit JSON String Hardcoding:** Hardcoding strings like `"{ \"title\": \"test\" }"` causes errors when the DTO structure changes. You MUST use `ObjectMapper`.
* **The @Transactional Illusion (Critical):** If you attach `@Transactional` to an integration test, the persistence context is maintained until the view is rendered or the DTO is converted. Because of this, a `LazyInitializationException` that would occur in a production environment might not occur during the test, resulting in a false positive. If verifying lazy loading is crucial for the test, consider removing `@Transactional` and manually cleaning up the data.
* **Hardcoded IDs:** To avoid DB sequence mismatch issues, do not use hardcoded IDs like `1L`. Instead, retrieve and use the ID of the created object (e.g., `savedDoc.getId()`).

## Related Skills

* `spring-rest-api-controller-unit-test`: Use this when you want to quickly isolate and test only the web layer without connecting to the DB.
* `spring-rest-api-testcontainers`: Extend and use this when you want to run integration tests in an environment that is 100% identical to the actual production DB (MySQL, PostgreSQL, etc.) rather than H2.
