---
name: spring-rest-api
description: Implement RESTful APIs utilizing a 3-Tier architecture (Controller, Service, Repository) and the latest Spring Boot specifications (Java Records, Jakarta Validation).
argument-hint: "[API를 만들 대상 도메인, 엔드포인트 목적 (예: 사용자 생성 API, 게시글 목록 조회 API)]"
source: "custom"
tags: ["java", "spring-boot", "rest-api", "backend", "architecture"]
triggers:
  - "REST API 만들어"
  - "신규 API 엔드포인트 추가"
---

# Spring Boot RESTful API Implementation

Detailed guidelines for implementing robust and optimized backend APIs.

## Overview

- Use when building RESTful API endpoints and core business logic for a new domain from scratch.
- Useful for quickly scaffolding and implementing backend code based on a stable 3-Tier architecture to communicate with clients.
- Actively utilizes Java `Record`-based immutable DTOs and `jakarta.validation`.

## When to Use This Skill

- When creating CRUD APIs to manipulate data after the database table (Entity) design is complete.
- When adding a new feature that receives external requests, validates them, performs specific business logic, and returns a response.
- When refactoring existing complex logic by separating it into the Service layer.

## How It Works

### Step 1: Define DTOs (Records)

Create Request DTOs to receive client inputs and Response DTOs to return outputs as Java `Record` classes.
- When validating input values, strictly use annotations from the `jakarta.validation.constraints.*` package (e.g., `@NotBlank`, `@NotNull`). **Never use the `javax.*` package.**

### Step 2: Implement Repository

Create an interface extending Spring Data JPA's `JpaRepository`. Define custom query methods or utilize QueryDSL interfaces only when complex data retrieval is required beyond standard CRUD operations.

### Step 3: Implement Service

Write business logic using the `@Service` annotation and inject the Repository via constructor injection (`@RequiredArgsConstructor`).
- Optimize performance by applying `@Transactional(readOnly = true)` at the class level, and override with `@Transactional` only on methods that modify data (Create, Update, Delete).
- Conversion logic between Entities and DTOs must use static factory methods (e.g., `from()`) inside the DTO.
- In case of business exceptions, utilize standard exceptions (e.g., `IllegalArgumentException`) or domain-specific business exceptions instead of indiscriminate custom exceptions.

### Step 4: Implement Controller

Configure routing using `@RestController` and `@RequestMapping`.
- **API Versioning:** Standardize API versioning by including an explicit version URI at the class-level `@RequestMapping` (e.g., `/api/v1/...`).
- **RESTful Responses:** Always return standard `ResponseEntity<T>` with clear HTTP status codes.
- Upon successful creation (`POST`), you must return a `201 CREATED` status code along with the access URI of the created resource in the `Location` header.

## Examples

```java
// 1. DTO
public record DocumentCreateRequest(
    @NotBlank(message = "Title is required.") String title,
    @NotBlank(message = "Content is required.") String content
) {}

public record DocumentResponse(
    @NotNull Long id,
    @NotBlank String title,
    @NotBlank String content
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
            document.getId(), 
            document.getTitle(), 
            document.getContent()
        );
    }
}

// 2. Service
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // Default to read-only
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
@RequestMapping("/api/v1/documents") // URI-based standard versioning
@RequiredArgsConstructor
public class DocumentController {
    
    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<DocumentResponse> createDocument(@Valid @RequestBody DocumentCreateRequest request) {
        DocumentResponse response = documentService.createDocument(request);
        
        // RESTful 201 Created standard (Includes Location header)
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/documents/{id}")
                .buildAndExpand(response.id())
                .toUri();
                
        return ResponseEntity.created(location).body(response);
    }
}

```

## Best Practices

* **Adhere to the Single Responsibility Principle (SRP):** The Controller should only handle HTTP requests/responses and routing. Data processing and business logic must be executed in the Service layer.
* **Immutable Object Orientation:** Ensure immutability and minimize boilerplate code by using Records for Data Transfer Objects.

## Common Pitfalls

* **Returning Entities Directly:** Returning JPA Entities directly from the Controller causes severe side effects such as runtime errors (`LazyInitializationException`), circular references, and exposure of sensitive information. Always convert them to DTOs before responding.
* **Mixing Legacy Packages:** Be extremely careful not to accidentally import `javax.validation` using IDE auto-completion when writing validation annotations.

## Related Skills

* `spring-jpa-entity-design`: Use first to design the foundational database schema and entities before implementing the API.
* `spring-boot-querydsl`: Inject into the Service layer when complex search conditions or pagination are needed.
* `spring-rest-api-exception-handler`: Use to globally handle validation failures (`@Valid`) or business exceptions that occur outside the controller logic and return consistent error response DTOs.
