---
name: spring-restdocs-openapi
description: Spring REST Docs와 epages(restdocs-api-spec)를 활용하여 프로덕션 코드 오염 없이 단위 테스트 기반으로 OpenAPI(Swagger) 명세서를 자동 생성합니다.
argument-hint: "[문서화할 대상 API 컨트롤러/메서드 및 문서에 포함될 요청/응답 필드 정보 (예: DocumentController 문서 생성 API REST Docs 작성)]"
source: "custom"
tags: ["java", "spring-boot", "rest-docs", "openapi", "swagger", "testing", "documentation"]
triggers:
  - "REST Docs 작성"
  - "API 문서화"
---

# Spring REST Docs + OpenAPI Documentation

프로덕션 코드(Controller, DTO)에 어떠한 문서화 어노테이션도 추가하지 않고, 오직 컨트롤러 단위 테스트(`@WebMvcTest`)를 통과해야만 Swagger UI 명세서가 생성되는 '테스트 주도 문서화(Test-Driven Documentation)' 지침입니다.

## Overview

- **운영 코드 100% 클린 유지:** Swagger 어노테이션(`@Tag`, `@Operation`, `@Schema` 등)을 완전히 배제하여 비즈니스 라우팅 코드와 DTO 본연의 모습만 남깁니다.
- **문서 신뢰도 100% 보장:** API 스펙이 변경되었는데 문서(테스트)를 수정하지 않으면 빌드가 실패하므로, API 실제 동작과 문서가 불일치할 확률이 0%입니다.
- **파이프라인:** `MockMvc` 테스트 실행 -> `.json` 스니펫 생성 -> `./gradlew openapi3` 빌드 태스크 실행 -> 최종 `openapi3.json` 및 Swagger UI 생성의 흐름을 가집니다.

## When to Use This Skill

- Controller와 DTO 코드를 어노테이션으로 더럽히고 싶지 않을 때.
- 프론트엔드 개발자에게 신뢰할 수 있고 항상 최신 상태로 유지되는 대화형 Swagger UI 명세서를 제공해야 할 때.
- `spring-rest-api-controller-unit-test` 스킬로 작성된 컨트롤러 단위 테스트에 문서화 기능을 덧붙이고 싶을 때.

## How It Works

### Step 1: Add Dependencies & Plugins

이 스킬은 `com.epages:restdocs-api-spec-mockmvc` 의존성과 `com.epages.restdocs-api-spec` Gradle 플러그인이 프로젝트에 설정되어 있다고 가정합니다.

### Step 2: Test Environment Setup

기존 `@WebMvcTest` 환경에 **`@AutoConfigureRestDocs`** 어노테이션을 추가하여 문서화 환경을 자동 구성합니다.

### Step 3: Write Documentation in `andDo()`

`MockMvc`의 `perform()` 체이닝 마지막 `andDo()` 메서드 안에 **`MockMvcRestDocumentationWrapper.document()`** (epages 전용 클래스)를 사용하여 문서를 작성합니다.
- **Schema 지정 (중요):** `resourceDetails().requestSchema(Schema.schema("..."))`를 반드시 사용하여 Swagger UI의 Schemas(Models) 영역에 DTO 구조가 깔끔하게 그룹핑되도록 하십시오.
- **필드 문서화:** `requestFields()`, `responseFields()`, `pathParameters()`, `queryParameters()`를 사용하여 페이로드의 모든 필드를 빠짐없이 명시합니다.

### Step 4: Strict Field Matching

테스트에서 실제 주고받는 JSON의 모든 필드가 `requestFields` 및 `responseFields`에 1:1로 매핑되어야 합니다. 단 하나라도 누락되거나 틀리면 `SnippetException`이 발생하며 테스트가 실패합니다.

## Examples

```java
@WebMvcTest(DocumentController.class)
@AutoConfigureRestDocs // REST Docs 자동 설정 추가
@MockBean(JpaMetamodelMappingContext.class)
class DocumentControllerDocsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private DocumentService documentService;

    @Test
    @DisplayName("문서 생성 API 문서화 및 테스트")
    @WithMockUser
    void createDocumentDocs() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("REST Docs 제목", "내용입니다.");
        DocumentResponse mockResponse = new DocumentResponse(1L, "REST Docs 제목", "내용입니다.");
        
        given(documentService.createDocument(any())).willReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/v1/documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // epages Wrapper를 사용한 문서화 블록
                .andDo(MockMvcRestDocumentationWrapper.document("create-document",
                        preprocessRequest(prettyPrint()),   // 요청 JSON 포맷팅
                        preprocessResponse(prettyPrint()),  // 응답 JSON 포맷팅
                        resourceDetails()
                                .tag("Document API")
                                .summary("문서 생성 API")
                                .description("새로운 문서를 생성하고 저장합니다.")
                                // Swagger UI Model 생성을 위한 Schema 명시적 지정
                                .requestSchema(Schema.schema("DocumentCreateRequest"))
                                .responseSchema(Schema.schema("DocumentResponse")),
                        requestFields(
                                fieldWithPath("title").type(JsonFieldType.STRING).description("문서의 제목"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("문서의 본문 내용")
                        ),
                        responseFields(
                                fieldWithPath("id").type(JsonFieldType.NUMBER).description("생성된 문서의 고유 식별자(ID)"),
                                fieldWithPath("title").type(JsonFieldType.STRING).description("생성된 문서의 제목"),
                                fieldWithPath("content").type(JsonFieldType.STRING).description("생성된 문서의 본문 내용")
                        )
                ));
    }
}

```

## Best Practices

* **Static Import 활용:** 문서화 코드가 길어지므로 `MockMvcRestDocumentationWrapper.document`, `Preprocessors.*`, `ResourceDocumentation.resourceDetails`, `PayloadDocumentation.*`, `Schema.schema` 등은 반드시 Static Import 하여 가독성을 높이십시오.
* **공통 포맷(Format) 분리:** 응답 DTO에 공통적으로 들어가는 페이지네이션 필드나 글로벌 예외(`ProblemDetail`) 응답 포맷은 별도의 유틸리티 클래스나 스니펫으로 분리하여 중복 코드를 제거하십시오.

## Common Pitfalls

* **일반 REST Docs 클래스 사용 (Critical):** `MockMvcRestDocumentation.document()`를 사용하면 일반 `.adoc` 스니펫만 생성되고 OpenAPI(Swagger) 변환이 불가능해집니다. 반드시 `epages` 패키지의 **`MockMvcRestDocumentationWrapper.document()`**를 사용하십시오.
* **Path / Query Parameter 매핑 오류:** `MockMvcRequestBuilders.get("/api/v1/documents/{id}", 1L)` 처럼 URL에 변수를 사용했다면, 반드시 `pathParameters(parameterWithName("id").description("..."))`로 문서화해야 합니다. 명시하지 않으면 테스트가 실패합니다.
* **필드 누락 테스트 실패:** JSON 응답에 `createdAt` 같은 필드가 추가되었는데 `responseFields`에 명시하지 않으면 `SnippetException`이 발생합니다. 만약 의도적으로 무시하고 싶은 필드가 있다면 `fieldWithPath("createdAt").ignored()` 처리를 해야 합니다.

## Related Skills

* `spring-rest-api-controller-unit-test`: 문서화 로직을 붙이기 전, 순수한 웹 계층 단위 테스트를 작성하는 기본 지침입니다.
* `spring-rest-api-exception-handler`: 글로벌 예외 처리기에서 내려주는 에러 응답 구조도 테스트 시 `responseFields`에 명확히 정의하여 문서화에 포함시킬 수 있습니다.
