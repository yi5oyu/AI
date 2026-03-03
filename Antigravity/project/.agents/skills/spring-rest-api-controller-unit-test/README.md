---
name: spring-rest-api-controller-unit-test
description: WebMvcTest와 Mockito를 활용하여 Spring Boot REST API 컨트롤러의 웹 계층 단위 테스트(Unit Test)를 BDD 패턴으로 작성합니다.
argument-hint: "[테스트할 대상 API 컨트롤러 및 특정 시나리오 (예: DocumentController 게시글 생성 201 성공 및 400 실패 단위 테스트)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "junit5", "mockito", "rest-api", "webmvctest"]
triggers:
  - "컨트롤러 단위 테스트 작성"
  - "API 엔드포인트 단위 테스트"
---

# Spring Boot REST API Controller Unit Test

Spring Boot 환경에서 데이터베이스나 복잡한 비즈니스 로직(Service)을 철저히 배제하고, 오직 Controller(웹 계층)의 동작만 빠르고 격리된 상태로 검증하는 단위 테스트 작성 지침입니다.

## Overview

- 애플리케이션 전체를 띄우지 않고 대상 컨트롤러와 웹 관련 빈(Bean)들만 로드하므로 테스트 실행 속도가 매우 빠릅니다.
- 실제 Service 객체 대신 Mockito를 이용한 가짜 객체(Mock)를 주입하여, 오직 HTTP 요청/응답 라우팅, 파라미터 바인딩, `@Valid` 검증, 상태 코드 반환 등 '웹 API 껍데기' 본연의 역할만 테스트합니다.

## When to Use This Skill

- 비즈니스 로직이나 DB가 아직 완성되지 않았지만, API 엔드포인트 스펙(URL, 상태 코드, JSON 포맷)을 먼저 검증하고 확정하고 싶을 때.
- `@NotBlank`, `@NotNull` 등 DTO의 유효성 검사(Validation)가 잘 작동하는지 다양한 실패 케이스를 가볍게 테스트할 때.
- 글로벌 예외 처리기(`@RestControllerAdvice`)가 컨트롤러 예외를 낚아채서 `ProblemDetail` (RFC 7807) 포맷으로 정확히 반환하는지 확인할 때.

## How It Works

### Step 1: Test Environment Setup

통합 테스트용 무거운 `@SpringBootTest`를 절대 사용하지 마십시오. **반드시 `@WebMvcTest(TargetController.class)`를 사용**하여 대상 컨트롤러만 가볍게 로드합니다.
- `MockMvc`와 `ObjectMapper`는 `@Autowired`로 주입받습니다.
- 컨트롤러가 의존하는 Service 계층은 **반드시 `@MockitoBean`을 사용하여 가짜 객체로 주입**받습니다. (Spring Boot 3.4+ 최신 규격. 이 테스트에서는 `@Transactional`을 절대 사용하지 않습니다.)

### Step 2: BDD Mocking in Given

BDD 패턴을 준수하여 `// Given` 절에서 **BDDMockito**의 `given(...).willReturn(...)` 메서드를 사용해 가짜 서비스 로직의 반환값(Stubbing)을 미리 세팅하십시오.

### Step 3: Execution with MockMvc

`// When` 절에서 `MockMvc`를 사용하여 HTTP 요청을 실행합니다. `ObjectMapper`를 활용하여 Request DTO를 JSON 문자열로 직렬화해 본문(`content()`)에 담아 전송하십시오.

### Step 4: Assertion

`// Then` 절에서 `MockMvc`의 `andExpect()`를 활용하여 HTTP 상태 코드와 응답 JSON 필드를 검증하십시오. 비즈니스 로직이나 DB 상태는 이 테스트의 관심사가 아니므로 절대 검증하지 않습니다. 마지막에는 `then(service).should()`를 통해 Mock 객체의 호출 횟수를 검증하십시오.

## Examples

```java
@WebMvcTest(DocumentController.class)
@MockBean(JpaMetamodelMappingContext.class) // JPA Auditing 에러 방지용
class DocumentControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Spring Boot 3.4+ 최신 규격 (기존 @MockBean 대체)
    @MockitoBean
    private DocumentService documentService;

    @Test
    @DisplayName("유효한 입력값이 주어지면 201 Created와 함께 응답 DTO를 반환한다")
    @WithMockUser // Spring Security 401 에러 방지
    void createDocument_Success() throws Exception {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("단위 테스트 제목", "단위 테스트 본문입니다.");
        String requestJson = objectMapper.writeValueAsString(request);

        DocumentResponse mockResponse = new DocumentResponse(1L, "단위 테스트 제목", "단위 테스트 본문입니다.");
        
        // BDDMockito를 활용한 가짜 서비스 반환값 세팅
        given(documentService.createDocument(any(DocumentCreateRequest.class)))
                .willReturn(mockResponse);

        // When
        ResultActions resultActions = mockMvc.perform(post("/api/v1/documents")
                .with(csrf()) // POST 요청 시 CSRF 토큰 주입
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));

        // Then
        resultActions
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("단위 테스트 제목"));
                
        // 서비스 로직이 정확히 1번 호출되었는지 상호작용 검증
        then(documentService).should(times(1)).createDocument(any(DocumentCreateRequest.class));
    }

    @Test
    @DisplayName("입력값 검증(@Valid) 실패 시 서비스 로직을 호출하지 않고 400 Bad Request와 ProblemDetail을 반환한다")
    @WithMockUser
    void createDocument_Fail_WhenValidationFails() throws Exception {
        // Given
        DocumentCreateRequest invalidRequest = new DocumentCreateRequest("", "제목이 비어있습니다.");
        String requestJson = objectMapper.writeValueAsString(invalidRequest);

        // When
        ResultActions resultActions = mockMvc.perform(post("/api/v1/documents")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));

        // Then
        resultActions
                .andExpect(status().isBadRequest())
                // spring-rest-api-exception-handler의 ProblemDetail 규격 검증
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.invalid_params.title").exists());
                
        // Validation 에러로 컨트롤러 단에서 튕겼으므로, 서비스 로직은 단 한 번도 호출되지 않았음을 검증
        then(documentService).shouldHaveNoInteractions();
    }
}

```

## Best Practices

* **Static Import 강제:** 코드 가독성을 극대화하기 위해 `MockMvcRequestBuilders.post`, `MockMvcResultMatchers.status`, `BDDMockito.given` 등의 메서드는 **반드시 Static Import**로 처리하여 코드를 짧고 간결하게 유지하십시오.
* **관심사 철저 분리:** 컨트롤러 단위 테스트에서는 데이터가 DB에 저장되었는지 확인하려 하지 마십시오. "컨트롤러가 파라미터를 잘 받고, 올바른 HTTP 상태 코드를 내려주는가?"에만 100% 집중하십시오.

## Common Pitfalls

* **JPA Auditing 컨텍스트 에러 (Critical):** 메인 클래스에 `@EnableJpaAuditing`이 선언되어 있다면, `@WebMvcTest` 로드 시 JPA 빈이 없어 에러가 발생합니다. 테스트 클래스에 `@MockBean(JpaMetamodelMappingContext.class)`를 반드시 추가하거나, Auditing 설정을 별도의 `@Configuration`으로 분리해야 합니다.
* **@SpringBootTest 사용 금지:** 웹 계층 단위 테스트에 무거운 `@SpringBootTest`를 붙이면 스프링 컨텍스트 전체를 로드하므로 단위 테스트의 존재 이유(빠른 속도)가 사라집니다. 무조건 `@WebMvcTest`를 사용하십시오.
* **Spring Security 호환성 누락:** 프로젝트에 Spring Security가 적용되어 있다면, 인증/인가 우회를 위해 클래스나 메서드 레벨에 `@WithMockUser`를 달고, 데이터 변경 요청(POST/PUT/DELETE) 시 `MockMvc` 체이닝에 반드시 `.with(csrf())`를 추가하십시오.

## Related Skills

* `spring-rest-api-service-unit-test`: 컨트롤러 검증이 끝나면, 이어서 비즈니스 로직(Service)의 단독 단위 테스트를 작성할 때 사용합니다.
* `spring-rest-api-integration-test`: 단위 테스트들이 모두 통과한 후, 실제 DB와 서비스 로직을 모두 연결하여 전체 파이프라인 흐름을 최종 통합 테스트할 때 사용합니다.
