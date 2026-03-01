---
name: spring-rest-api
description: Implement robust Spring Boot RESTful APIs utilizing a 3-tier architecture (Controller, Service, Repository) and the latest Spring Boot 4.x+ specifications.
argument-hint: "[API를 만들 대상 도메인, 엔드포인트 목적 (예: 사용자 생성 API, 게시글 목록 조회 API)]"
source: "custom"
tags: ["java", "spring-boot", "rest-api", "backend"]
triggers:
  - "REST API 만들어"
  - "신규 API 엔드포인트 추가"
---

# Spring Boot RESTful API Implementation

Detailed guidelines for implementing robust and optimized backend APIs.

## Overview

- Use when building RESTful API endpoints and core business logic for a new domain from scratch.
- Useful for quickly scaffolding and implementing stable 3-tier architecture backend code to communicate with frontends or clients.
- Actively utilizes the latest Spring Boot 4.x+ specifications (Native Versioning, Record-based DTOs, etc.).

## When to Use This Skill

- When creating CRUD APIs to manipulate data after the database tables (Entities) have been designed.
- When adding new features that require receiving external requests, validating them, executing specific business logic, and returning a response.
- When refactoring by separating complex logic from controllers into the Service layer.

## How It Works

### Step 1: [Define DTOs]
Create the Request DTO and Response DTO as Java `Record` classes. For input validation, you MUST strictly use annotations from the `jakarta.validation.constraints.*` package (e.g., `@NotBlank`, `@NotNull`); the use of the `javax.*` package is strictly prohibited.

### Step 2: [Implement Repository]
Create an interface extending Spring Data JPA's `JpaRepository`. Define custom query methods only when complex data retrieval beyond standard CRUD operations is required.

### Step 3: [Implement Service]
Use the `@Service` annotation and implement business logic by injecting the Repository via constructor injection (`@RequiredArgsConstructor`).
- Apply `@Transactional(readOnly = true)` at the class level to optimize performance, and override it with `@Transactional` only on methods that modify data (Create, Update, Delete).
- Always use static factory methods inside DTOs (e.g., `from()`) for Entity-to-DTO conversion. Use standard Java exceptions (e.g., `IllegalArgumentException`) instead of creating arbitrary custom exceptions unnecessarily.

### Step 4: [Implement Controller]
Set up routing using `@RestController` and `@RequestMapping`. Use constructor injection.
- **Apply Spring Boot 4.x Native API Versioning by strictly utilizing the `version` attribute in mapping annotations (e.g., `@PostMapping(version = "1")`).**
- Always return a standard `ResponseEntity<T>` with a clear HTTP status code. Upon successful creation (`POST`), you should aim to return a `201 CREATED` status along with the URI of the newly created resource in the `Location` header.

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
@Transactional(readOnly = true) // Default to read-only for performance
public class DocumentService {
    private final DocumentRepository documentRepository;

    @Transactional // Override for write operations
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

    // Apply Spring Boot 4.x Native API Versioning
    @PostMapping(version = "1") 
    public ResponseEntity<DocumentResponse> createDocumentV1(@Valid @RequestBody DocumentCreateRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        
        // RESTful 201 Created standard (including Location header)
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
                
        return ResponseEntity.created(location).body(response);
    }
}

```

## Best Practices

* **Single Responsibility Principle:** The Controller should only handle HTTP requests/responses and routing. Data processing and business logic must be handled in the Service layer.
* **Immutability:** Ensure immutability and minimize boilerplate code by using `Record` classes for data transfer objects.

## Common Pitfalls

* **Returning Entities Directly:** Returning JPA Entities directly from the Controller causes severe side effects, such as runtime errors due to Lazy Loading or the exposure of sensitive information. Always convert Entities to DTOs before responding.
* **Mixing Legacy Packages:** Be extremely careful not to accidentally import `javax.validation` via IDE auto-completion when writing validation annotations.

## Related Skills

* `spring-jpa-entity-design`: Use this first to design the foundational database schema and entities before implementing the API.
* `spring-querydsl`: Use this by injecting it into the Service layer when custom repository queries are needed for complex search conditions or resolving N+1 problems.
