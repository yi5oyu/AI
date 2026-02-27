---
name: spring-rest-api-impl
description: Spring Boot 기반 RESTful API (Controller, Service, Repository) 생성 및 DTO 매핑 코드를 구현할 때 사용
# argument-hint: "[구현할 도메인 이름 및 주요 요구사항 (예: 마크다운 문서 생성 API)]"
source: "custom"
tags: ["java", "spring-boot", "rest-api", "backend"]
triggers:
  - "REST API 만들어"
  - "신규 API 엔드포인트 추가"
tech_stack:
  java_version: 21
  framework: "Spring Boot 4.x, Spring Data JPA"
rules_path: "../rules/spring-convention.md"
rules:
  - "'jakarta.validation.*' 및 'jakarta.persistence.*' 패키지를 엄격하게 사용하세요. 'javax.*'는 절대 사용하지 마세요."
  - "Controller 메서드에서는 항상 명확한 HTTP 상태 코드(예: 201 CREATED, 204 NO CONTENT)와 함께 표준 'ResponseEntity<T>'를 반환하세요."
---

# Spring Boot RESTful API Implementation

에이전트를 위한 견고한 백엔드 API 구현 상세 지침

## Overview

- 새로운 도메인의 RESTful API 엔드포인트와 핵심 비즈니스 로직을 처음부터 구축할 때 사용합니다.
- 이것은 프론트엔드나 클라이언트와 통신하기 위한 안정적인 3-Tier(계층형) 아키텍처 기반의 백엔드 코드를 빠르게 스캐폴딩하고 구현하는 데 유용합니다.

## When to Use This Skill

- 데이터베이스 테이블(Entity) 설계가 완료된 후, 이를 조작하기 위한 CRUD API를 만들어야 할 때
- 외부 요청을 받아 검증하고, 특정 비즈니스 로직을 수행한 뒤 응답을 반환하는 새로운 기능을 추가할 때
- 기존의 복잡한 로직을 Service 계층으로 분리하여 리팩토링할 때



## How It Works

### Step 1: [DTO 정의]
클라이언트의 요청을 받을 Request DTO와 응답을 반환할 Response DTO를 `Record` 클래스로 생성합니다. `jakarta.validation.constraints.*` 패키지의 어노테이션(예: `@NotBlank`, `@NotNull`)만을 사용하여 입력값 검증을 엄격하게 적용하세요.

### Step 2: [Repository 구현]
Spring Data JPA의 `JpaRepository`를 상속받는 인터페이스를 생성합니다. 표준 CRUD 작업을 넘어 복잡한 데이터 조회가 필요한 경우에만 커스텀 쿼리 메서드를 정의하세요.

### Step 3: [Service 구현]
`@Service` 어노테이션을 사용하고, `@Transactional`을 통해 트랜잭션을 관리합니다. 생성자 주입(`@RequiredArgsConstructor`)을 통해 Repository를 주입받아 핵심 비즈니스 로직을 작성합니다. Entity와 DTO 간의 변환 로직은 반드시 DTO 클래스 내부에 위치한 정적 팩토리 메서드(예: `from()`)를 사용하여 수행하세요. 임의의 새로운 예외 클래스를 무분별하게 생성하지 말고, 표준 예외(예: `IllegalArgumentException`)나 기존에 사전 정의된 커스텀 예외를 던지세요.

### Step 4: [Controller 구현]
`@RestController`와 `@RequestMapping`을 사용하여 라우팅을 설정합니다. 생성자 주입을 사용하여 Service 계층을 주입받습니다. 표준 `ResponseEntity`를 사용하여 응답을 구성하세요. 올바른 HTTP 상태 코드(예: 리소스 생성 시 `HttpStatus.CREATED`, 조회 시 `HttpStatus.OK`)가 명시적으로 반환되도록 보장하세요. API 버저닝을 위해 매핑 어노테이션의 `version` 속성(예: `@PostMapping(version = "1")`)을 활용하세요.

## Examples

```java
// 1. DTO
public record DocumentCreateRequest(
    @NotBlank String title,
    @NotBlank String content
) {}

public record DocumentResponse(
    @NonNull Long id,
    @NotBlank String title,
    @NotBlank String content
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(document.getId(), document.getTitle(), document.getContent());
    }
}

// 2. Service
@Service
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentRepository documentRepository;

    @Transactional
    public DocumentResponse createDocument(DocumentCreateRequest request) {
        Document document = Document.builder()
            .title(request.title())
            .content(request.content())
            .build();
        
        Document savedDocument = documentRepository.save(document);
        return DocumentResponse.from(savedDocument);
    }
}

// 3. Controller
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping(version = "1") 
    public ResponseEntity<DocumentResponse> createDocumentV1(@Valid @RequestBody DocumentCreateRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```
