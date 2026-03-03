---
name: spring-rest-api-service-unit-test
description: Spring 컨텍스트 없이 순수 JUnit 5와 Mockito를 활용하여 Service 계층의 핵심 비즈니스 로직을 격리 검증하는 단위 테스트(Unit Test)를 BDD 패턴으로 작성합니다.
argument-hint: "[테스트할 대상 Service 클래스 및 특정 비즈니스 시나리오 (예: DocumentService 게시글 생성 성공 및 조회 실패 단위 테스트)]"
source: "custom"
tags: ["java", "spring-boot", "testing", "junit5", "mockito", "backend", "architecture"]
triggers:
  - "서비스 단위 테스트 작성"
  - "비즈니스 로직 테스트"
---

# Spring Boot REST API Service Unit Test

Spring 프레임워크(ApplicationContext)를 전혀 띄우지 않고, 외부 의존성(DB, 외부 API 등)을 가짜 객체로 대체하여 오직 Service 계층 내부의 순수 비즈니스 로직만을 초고속으로 검증하는 단위 테스트 작성 지침입니다.

## Overview

- 데이터베이스 연결이나 웹 계층(Controller)의 개입 없이, 오직 Service 클래스 내부의 `if-else` 분기, 데이터 가공, 특정 조건에서의 예외 발생 등 도메인 핵심 규칙이 맞게 동작하는지 검증합니다.
- Spring Boot 프레임워크를 로드하지 않으므로 수천 개의 테스트도 단 몇 초 만에 실행되며, 부작용(Side-effect)이 전혀 없는 가장 가볍고 중요한 테스트 스킬입니다.

## When to Use This Skill

- 새로운 비즈니스 로직을 Service에 구현한 직후, 로직의 수학적/논리적 정확성을 검증할 때.
- 데이터베이스 조회 실패 시 `EntityNotFoundException` 등 의도한 예외가 정확히 터지는지(Throw) 테스트할 때.
- 특정 조건에 따라 다르게 분기되는 복잡한 도메인 로직의 모든 예외 케이스(Edge Cases)를 빠르고 촘촘하게 테스트해야 할 때.

## How It Works

### Step 1: Pure Mockito Environment

테스트 클래스 상단에 Spring 관련 어노테이션(`@SpringBootTest`, `@WebMvcTest` 등)을 절대 사용하지 마십시오. 
- **반드시 `@ExtendWith(MockitoExtension.class)`를 사용**하여 순수 Mockito 환경만 활성화합니다.

### Step 2: Inject Mocks

테스트 대상이 되는 실제 Service 클래스에는 **`@InjectMocks`**를 붙여 객체를 생성합니다.
- Service가 의존하는 Repository나 외부 컴포넌트에는 **`@Mock`**을 붙여 가짜 객체(Mock)를 주입합니다.

### Step 3: BDD Stubbing & Execution

BDD 패턴(`// Given`, `// When`, `// Then`)을 철저히 지키십시오.
- `// Given`: **BDDMockito**의 `given(...).willReturn(...)`을 사용하여 가짜 Repository가 반환할 데이터를 미리 세팅(Stubbing)합니다.
- `// When`: `@InjectMocks`로 생성된 실제 Service의 메서드를 호출하여 로직을 실행합니다.

### Step 4: Assertion & Verification

결과를 엄격하게 검증합니다.
- **정상 로직 (`// Then`):** AssertJ의 `assertThat()`을 사용하여 반환된 DTO의 값이 예상과 일치하는지 검증합니다.
- **예외 검증:** AssertJ의 `assertThatThrownBy()`를 사용하여 특정 상황에서 의도한 비즈니스 예외가 정확히 발생하는지 검증합니다.
- **상호작용 검증:** `BDDMockito.then(...).should()`를 사용하여 Repository의 특정 메서드가 호출되었는지 검증하십시오. 의존 객체로 넘어간 파라미터 내부 상태를 검증해야 할 때는 `ArgumentCaptor`를 활용하십시오.

## Examples

```java
@ExtendWith(MockitoExtension.class) // Spring 컨텍스트 없이 순수 Mockito만 사용 (초고속)
class DocumentServiceUnitTest {

    @InjectMocks
    private DocumentService documentService; // 테스트할 실제 서비스 객체

    @Mock
    private DocumentRepository documentRepository; // 서비스가 의존하는 가짜 DB 객체

    @Captor
    private ArgumentCaptor<Document> documentCaptor; // 내부 매핑 로직을 검증하기 위한 Captor

    @Test
    @DisplayName("유효한 요청 시 게시글 엔티티를 생성하여 저장하고 응답 DTO를 반환한다")
    void createDocument_Success() {
        // Given
        DocumentCreateRequest request = new DocumentCreateRequest("비즈니스 제목", "비즈니스 본문");
        Document savedDocument = Document.builder()
                .title("비즈니스 제목")
                .content("비즈니스 본문")
                .build();
        
        // 엔티티의 ID처럼 DB가 자동 생성해 주는 값은 단위 테스트에서 리플렉션으로 주입
        ReflectionTestUtils.setField(savedDocument, "id", 1L); 

        // Repository의 save()가 호출되면 savedDocument를 반환하도록 Stubbing
        given(documentRepository.save(any(Document.class))).willReturn(savedDocument);

        // When
        DocumentResponse response = documentService.createDocument(request);

        // Then: 반환된 DTO 상태 검증
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("비즈니스 제목");
        
        // Then: 상호작용 검증 및 save()로 넘어간 엔티티의 내부 상태 캡처 검증
        then(documentRepository).should(times(1)).save(documentCaptor.capture());
        Document capturedDocument = documentCaptor.getValue();
        assertThat(capturedDocument.getTitle()).isEqualTo("비즈니스 제목");
        assertThat(capturedDocument.getContent()).isEqualTo("비즈니스 본문");
    }

    @Test
    @DisplayName("존재하지 않는 게시글을 조회하면 EntityNotFoundException 예외가 발생한다")
    void getDocument_Fail_WhenNotFound() {
        // Given
        Long invalidId = 999L;
        
        // Repository가 빈 Optional을 반환하도록 세팅
        given(documentRepository.findById(invalidId)).willReturn(Optional.empty());

        // When & Then: 의도한 예외가 정확히 던져지는지 검증
        assertThatThrownBy(() -> documentService.getDocument(invalidId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("해당 문서를 찾을 수 없습니다");
                
        // DB 조회를 1회 시도했는지 상호작용 검증
        then(documentRepository).should(times(1)).findById(invalidId);
    }
}

```

## Best Practices

* **Static Import 강제:** 코드 가독성을 극대화하기 위해 AssertJ의 `assertThat`, `assertThatThrownBy`, Mockito의 `any`, `times`, `given`, `then` 등의 메서드는 **반드시 Static Import**로 처리하십시오.
* **리플렉션 적극 활용:** `ReflectionTestUtils.setField()`를 사용하면, 엔티티 클래스에 테스트만을 위한 불필요한 Setter를 열어두는 설계 결함을 방지하면서도 가짜 식별자(ID) 값을 쉽게 주입할 수 있습니다.
* **ArgumentCaptor 활용:** Service 메서드 내부에서 DTO를 Entity로 변환한 뒤 `save()` 등을 호출하는 경우, `@Captor`와 `ArgumentCaptor`를 사용하여 가짜 레포지토리로 넘어간 파라미터를 낚아채서 내부 매핑 로직을 엄격하게 검증하십시오.

## Common Pitfalls

* **@SpringBootTest 사용 금지 (Critical):** 비즈니스 로직 하나 검증하자고 수십 초씩 걸리는 스프링 컨텍스트를 띄우지 마십시오. 무조건 순수 Mockito 환경(`@ExtendWith(MockitoExtension.class)`)을 유지해야 합니다.
* **Stubbing 누락으로 인한 NPE:** Mock 객체의 메서드는 별도로 `given()` 세팅을 해주지 않으면 기본적으로 `null` (또는 빈 컬렉션)을 반환합니다. Service 내부에서 이 반환값을 참조하다가 NullPointerException이 터지지 않도록, 실행 경로에 있는 모든 Mock 메서드의 반환값을 꼼꼼히 세팅하십시오.
* **과도한 상호작용 검증 지양:** `then().should()`를 코드의 모든 줄마다 남발하면 테스트 코드가 내부 구현 로직에 너무 강하게 결합되어 추후 리팩토링이 힘들어집니다. 비즈니스 로직의 '최종 결과(상태 반환이나 특정 예외 발생)'를 검증하는 데 더 집중하십시오.

## Related Skills

* `spring-rest-api`: 이 테스트를 통해 서비스 로직이 완벽하게 검증되면, 이를 호출하는 API 컨트롤러를 안전하게 구현할 수 있습니다.
* `spring-rest-api-controller-unit-test`: 서비스 계층 대신, 웹 계층(API 껍데기)만을 격리하여 검증할 때 사용합니다.
* `spring-rest-api-integration-test`: 단위 테스트가 모두 통과한 후, 전체 흐름을 실제 DB와 엮어 최종 검증할 때 사용합니다.
