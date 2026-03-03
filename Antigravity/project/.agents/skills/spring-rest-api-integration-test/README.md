---
name: spring-rest-api-integration-test
description: Spring Boot REST API의 전체 계층(Controller-Service-Repository)과 데이터베이스를 연결하여 검증하는 통합 테스트를 작성합니다.
argument-hint: "[테스트할 컨트롤러/메서드 및 시나리오 (예: DocumentController 문서 생성 및 DB 저장 통합 테스트)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "junit5", "rest-api", "integration-test", "h2"]
triggers:
  - "통합 테스트 작성"
  - "DB 테스트 작성"
---

# Spring Boot REST API Integration Test

불필요한 설정을 걷어내고, 빠르고 직관적으로 작성할 수 있는 실무형 통합 테스트 지침입니다.

## Overview

- **속도 우선:** H2 인메모리 DB를 기본으로 사용하여 로컬 환경이나 CI 파이프라인에서 즉시, 빠르게 실행됩니다.
- **유지보수 중심:** 복잡한 `.sql` 파일보다는 자바 코드(`Repository`) 기반의 데이터 준비를 권장합니다.
- **표준 지향:** 특정 DB 전용 기능보다는 JPA 표준 기능을 우선 사용하여 이식성을 높입니다.

## How It Works

### Step 1: Minimal Environment Setup

- **어노테이션:** `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional` 세 가지를 기본으로 사용합니다.
- **프로파일:** `@ActiveProfiles("test")`를 통해 `application-test.yml`의 설정(H2 등)을 로드합니다.
- `MockMvc`와 함께 `ObjectMapper`를 주입받아 JSON 직렬화에 사용합니다.

### Step 2: Lean Data Setup

- **Java First:** 테스트 데이터는 가급적 `repository.save()`를 통해 준비합니다. (엔티티 변경 시 컴파일 에러로 즉시 확인 가능하므로 리팩토링에 유리합니다.)
- **Select @Sql:** 연관 관계가 너무 복잡하여 자바 코드가 길어질 때만 `@Sql`을 선택적으로 사용하십시오.

### Step 3: BDD Execution & Verification

- `// Given`: 필요한 최소한의 데이터를 DB에 저장하거나 Request DTO를 생성합니다.
- `// When`: `mockMvc.perform()`으로 API를 호출합니다.
- `// Then`: HTTP 상태 코드와 응답 JSON을 검증합니다. 마지막에는 반드시 Repository를 통해 DB에 데이터가 올바르게 반영되었는지 `AssertJ`로 교차 검증하십시오.

## Examples

```java
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 테스트 완료 후 자동 롤백
class DocumentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DocumentRepository documentRepository;

    @Test
    @DisplayName("새로운 문서를 생성하면 201 응답을 반환하고 DB에 데이터가 저장된다")
    void createDocument_Success() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("실무 테스트", "통합 테스트 내용입니다.");
        String content = objectMapper.writeValueAsString(request); // JSON 하드코딩 지양

        // When & Then: API 응답 검증
        mockMvc.perform(post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("실무 테스트"));

        // Then: DB 상태 최종 검증
        List<Document> documents = documentRepository.findAll();
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getTitle()).isEqualTo("실무 테스트");
        assertThat(documents.get(0).getContent()).isEqualTo("통합 테스트 내용입니다.");
    }
}

```

## Best Practices

* **No Over-Engineering:** 모든 필드를 일일이 검증하기보다, 비즈니스 핵심 로직이 담긴 필드 위주로 검증하여 테스트 코드의 무게를 줄이십시오.
* **Implicit Precision:** 날짜 비교 시 초 단위까지만 검증하거나 AssertJ의 `isCloseTo`를 사용하여 DB 정밀도 차이로 인한 실패를 방지하십시오.
* **Header-based Versioning:** 만약 프로젝트가 헤더나 파라미터로 버전을 관리한다면, URI 대신 `.header()` 또는 `.param()`을 추가하십시오.

## Common Pitfalls

* **JSON 문자열 하드코딩 금지:** `"{ \"title\": \"test\" }"` 같은 문자열 하드코딩은 DTO 구조 변경 시 에러를 유발합니다. 반드시 `ObjectMapper`를 사용하십시오.
* **@Transactional의 착시 (Critical):** 통합 테스트에 `@Transactional`을 붙이면 뷰(View) 렌더링이나 DTO 변환 시점까지 영속성 컨텍스트가 유지됩니다. 이로 인해 프로덕션 환경에서는 발생하는 `LazyInitializationException`(지연 로딩 에러)이 테스트에서는 발생하지 않고 통과해 버릴 수 있습니다. 지연 로딩 검증이 핵심인 테스트라면 `@Transactional`을 제거하고 수동으로 데이터를 지우는 방식을 고려하십시오.
* **Hardcoded ID:** `1L` 같은 하드코딩된 ID 대신, 생성된 객체의 ID(예: `savedDoc.getId()`)를 받아 사용함으로써 DB 시퀀스 불일치 문제를 피하십시오.

## Related Skills

* `spring-rest-api-controller-unit-test`: DB 연결 없이 웹 계층만 빠르게 격리 테스트하고 싶을 때 사용합니다.
* `spring-rest-api-service-unit-test`: Use this next to write isolated unit tests for the business logic (Service) after verifying the controller.
