---
name: spring-rest-api
description: Controller, Service, Repository의 3계층(3-Tier) 아키텍처와 최신 Spring Boot 4.x+ 스펙을 활용하여 RESTful API를 구현합니다.
argument-hint: "[API를 만들 대상 도메인, 엔드포인트 목적 (예: 사용자 생성 API, 게시글 목록 조회 API)]"
source: "custom"
tags: ["java", "spring-boot", "rest-api", "backend"]
triggers:
  - "REST API 만들어"
  - "신규 API 엔드포인트 추가"
---

# Spring Boot RESTful API Implementation

견고하고 최적화된 백엔드 API 구현을 위한 상세 지침입니다.

## Overview

- 새로운 도메인의 RESTful API 엔드포인트와 핵심 비즈니스 로직을 처음부터 구축할 때 사용합니다.
- 클라이언트와 통신하기 위한 안정적인 3-Tier(계층형) 아키텍처 기반의 백엔드 코드를 빠르게 스캐폴딩하고 구현하는 데 유용합니다.
- Spring Boot 4.x 이상의 최신 스펙(Native Versioning, Record 기반 DTO 등)을 적극 활용합니다.

## When to Use This Skill

- 데이터베이스 테이블(Entity) 설계가 완료된 후, 이를 조작하기 위한 CRUD API를 만들어야 할 때
- 외부 요청을 받아 검증하고, 특정 비즈니스 로직을 수행한 뒤 응답을 반환하는 새로운 기능을 추가할 때
- 기존의 복잡한 로직을 Service 계층으로 분리하여 리팩토링할 때

## How It Works

### Step 1: [Define DTOs]
클라이언트의 요청을 받을 Request DTO와 응답을 반환할 Response DTO를 Java `Record` 클래스로 생성합니다. 입력값 검증 시 반드시 `jakarta.validation.constraints.*` 패키지의 어노테이션(예: `@NotBlank`, `@NotNull`)만을 엄격하게 사용하십시오 (`javax.*` 패키지 절대 금지).

### Step 2: [Implement Repository]
Spring Data JPA의 `JpaRepository`를 상속받는 인터페이스를 생성합니다. 표준 CRUD 작업을 넘어 복잡한 데이터 조회가 필요한 경우에만 커스텀 쿼리 메서드를 정의하십시오.

### Step 3: [Implement Service]
`@Service` 어노테이션을 사용하고, 생성자 주입(`@RequiredArgsConstructor`)으로 Repository를 주입받아 비즈니스 로직을 작성합니다. 
- 클래스 레벨에 `@Transactional(readOnly = true)`를 적용하여 성능을 최적화하고, 데이터를 변경하는 메서드(Create, Update, Delete)에만 `@Transactional`을 오버라이딩하십시오.
- Entity와 DTO 간의 변환 로직은 반드시 DTO 내부의 정적 팩토리 메서드(예: `from()`)를 사용하십시오. 무분별한 커스텀 예외 대신 표준 예외(예: `IllegalArgumentException`)를 활용하십시오.

### Step 4: [Implement Controller]
`@RestController`와 `@RequestMapping`을 사용하여 라우팅을 설정합니다. 생성자 주입을 사용합니다.
- **Spring Boot 4.x 이상의 Native API Versioning을 적용하여, 매핑 어노테이션의 `version` 속성(예: `@PostMapping(version = "1")`)을 반드시 활용하십시오.** - 항상 명확한 HTTP 상태 코드와 함께 표준 `ResponseEntity<T>`를 반환하고, 생성(`POST`) 성공 시 `201 CREATED` 상태 코드와 함께 생성된 리소스의 URI를 `Location` 헤더에 담아 반환하는 것을 지향하십시오.

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
@Transactional(readOnly = true) // 읽기 전용 기본 설정
public class DocumentService {
    private final DocumentRepository documentRepository;

    @Transactional // 쓰기 작업에만 오버라이딩
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

    // Spring Boot 4.x Native API Versioning 적용
    @PostMapping(version = "1") 
    public ResponseEntity<DocumentResponse> createDocumentV1(@Valid @RequestBody DocumentCreateRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        
        // RESTful 201 Created 표준 (Location 헤더 포함)
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
                
        return ResponseEntity.created(location).body(response);
    }
}

```

## Best Practices

* **단일 책임 원칙 준수:** Controller는 HTTP 요청/응답 처리 및 라우팅만 담당하고, 데이터 가공 및 비즈니스 로직은 반드시 Service에서 수행해야 합니다.
* **불변 객체 지향:** 데이터 전달 객체는 레코드(Record)를 사용하여 불변성을 보장하고 보일러플레이트 코드를 최소화하십시오.

## Common Pitfalls

* **Entity 직접 반환:** Controller에서 JPA Entity를 직접 반환하면 지연 로딩(Lazy Loading)으로 인한 런타임 에러나 민감한 정보 노출 등의 심각한 사이드 이펙트가 발생합니다. 항상 DTO로 변환하여 응답하십시오.
* **구형 패키지 혼용:** 검증 어노테이션 작성 시 IDE의 자동 완성을 잘못 사용하여 `javax.validation`을 임포트하지 않도록 각별히 주의하십시오.

## Related Skills

* `spring-jpa-entity-design`: API를 구현하기 전, 기초가 되는 데이터베이스 스키마와 엔티티를 설계할 때 먼저 사용합니다.
* `spring-querydsl`: 복잡한 검색 조건이나 N+1 문제 해결을 위한 커스텀 레포지토리 조회가 필요할 때 Service 계층에 주입하여 사용합니다.
