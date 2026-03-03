---
name: spring-restdocs-openapi
description: Automatically generate OpenAPI (Swagger) specifications based on unit tests without polluting production code, using Spring REST Docs and epages (restdocs-api-spec).
argument-hint: "[문서화할 대상 API 컨트롤러/메서드 및 문서에 포함될 요청/응답 필드 정보 (예: DocumentController 문서 생성 API REST Docs 작성)]"
source: "custom"
tags: ["java", "spring-boot", "rest-docs", "openapi", "swagger", "testing", "documentation"]
triggers:
  - "REST Docs 작성"
  - "API 문서화"
---

# Spring REST Docs + OpenAPI Documentation

Guidelines for 'Test-Driven Documentation' where Swagger UI specifications are generated only by passing controller unit tests (`@WebMvcTest`), without adding any documentation annotations to the production code (Controller, DTO).

## Overview

- **100% Clean Production Code:** Completely excludes Swagger annotations (e.g., `@Tag`, `@Operation`, `@Schema`), leaving only the pure business routing code and DTO structures.
- **100% Document Reliability Guaranteed:** If the API specification changes but the document (test) is not updated, the build will fail. The probability of discrepancy between actual API behavior and the documentation is 0%.
- **Pipeline:** `MockMvc` test execution -> generates `.json` snippets -> executes `./gradlew openapi3` build task -> generates the final `openapi3.json` and Swagger UI.

## When to Use This Skill

- When you do not want to pollute Controller and DTO code with annotations.
- When you need to provide frontend developers with an interactive Swagger UI specification that is reliable and always up-to-date.
- When you want to add documentation capabilities to controller unit tests written with the `spring-rest-api-controller-unit-test` skill.

## How It Works

### Step 1: Add Dependencies & Plugins

This skill assumes that the `com.epages:restdocs-api-spec-mockmvc` dependency and the `com.epages.restdocs-api-spec` Gradle plugin are configured in the project.

### Step 2: Test Environment Setup

Add the **`@AutoConfigureRestDocs`** annotation to the existing `@WebMvcTest` environment to automatically configure the documentation environment.

### Step 3: Write Documentation in `andDo()`

Write the documentation inside the last `andDo()` method of the `MockMvc` `perform()` chain using **`MockMvcRestDocumentationWrapper.document()`** (epages-specific class).
- **Specify Schema (Crucial):** You MUST use `resourceDetails().requestSchema(Schema.schema("..."))` so that the DTO structure is neatly grouped in the Schemas (Models) section of the Swagger UI.
- **Document Fields:** Use `requestFields()`, `responseFields()`, `pathParameters()`, and `queryParameters()` to meticulously specify every field in the payload without omission.

### Step 4: Strict Field Matching

Every field in the JSON actually exchanged in the test must map 1:1 to `requestFields` and `responseFields`. If even one is missing or incorrect, a `SnippetException` is thrown, and the test fails.

## Examples

```java
@WebMvcTest(DocumentController.class)
@AutoConfigureRestDocs // Automatically configures REST Docs
@MockBean(JpaMetamodelMappingContext.class)
class DocumentControllerDocsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private DocumentService documentService;

    @Test
    @DisplayName("Document Create API Documentation and Test")
    @WithMockUser
    void createDocumentDocs() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("REST Docs Title", "This is the content.");
        DocumentResponse mockResponse = new DocumentResponse(1L, "REST Docs Title", "This is the content.");
        
        given(documentService.createDocument(any())).willReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // Documentation block using the epages Wrapper
                .andDo(MockMvcRestDocumentationWrapper.document("create-document",
                        preprocessRequest(prettyPrint()),   // Format Request JSON
                        preprocessResponse(prettyPrint()),  // Format Response JSON
                        resourceDetails()
                                .tag("Document API")
                                .summary("Create Document API")
                                .description("Creates and saves a new document.")
                                // Explicitly specify Schema for Swagger UI Model generation
                                .requestSchema(Schema.schema("DocumentCreateRequest"))
                                .responseSchema(Schema.schema("DocumentResponse")),
                        requestFields(
                                fieldWithPath("title").type(JsonFieldType.STRING).description("The title of the document"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("The body content of the document")
                        ),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("The unique identifier (ID) of the created document"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("The title of the created document"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("The body content of the created document")
                        )
                ));
    }
}

```

## Best Practices

* **Utilize Static Imports:** Since documentation code gets long, you MUST statically import `MockMvcRestDocumentationWrapper.document`, `Preprocessors.*`, `ResourceDocumentation.resourceDetails`, `PayloadDocumentation.*`, `Schema.schema`, etc., to improve readability.
* **Extract Common Formats:** Extract pagination fields or global exception (`ProblemDetail`) response formats commonly included in response DTOs into separate utility classes or snippets to eliminate duplicate code.

## Common Pitfalls

* **Using the Standard REST Docs Class (Critical):** If you use `MockMvcRestDocumentation.document()`, only standard `.adoc` snippets are generated, and OpenAPI (Swagger) conversion becomes impossible. You MUST use **`MockMvcRestDocumentationWrapper.document()`** from the `epages` package.
* **Path / Query Parameter Mapping Errors:** If you used variables in the URL like `MockMvcRequestBuilders.get("/api/v1/documents/{id}", 1L)`, you MUST document them with `pathParameters(parameterWithName("id").description("..."))`. If omitted, the test fails.
* **Test Failure Due to Unmapped Fields:** If a field like `createdAt` is added to the JSON response but not specified in `responseFields`, a `SnippetException` occurs. If you intentionally want to ignore a field, you must explicitly declare it like `fieldWithPath("createdAt").ignored()`.
* **Prohibit `@Schema` inside DTOs:** The purpose of this approach is to keep production code clean. Do not mix Swagger's `@Schema` annotations in DTOs. All specifications must be controlled solely within the unit test files.

## Related Skills

* `spring-rest-api-controller-unit-test`: The foundational guide for writing pure web layer unit tests before attaching documentation logic.
* `spring-rest-api-exception-handler`: The error response structure returned by the global exception handler can also be clearly defined in `responseFields` during testing to be included in the documentation.
