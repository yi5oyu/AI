---
name: spring-rest-api-impl
description: Implements RESTful API endpoints focusing on business logic in a Spring Boot environment. Generates code that adheres to the single responsibility principle by clearly separating the 3-tier architecture (Controller, Service, Repository) and mapping DTOs.
# argument-hint: "[Domain name to implement and major requirements (e.g., Markdown document creation API)]"
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
  - "Strictly use 'jakarta.validation.*' and 'jakarta.persistence.*' packages; NEVER use 'javax.*'."
  - "Always return a standard 'ResponseEntity<T>' from Controller methods with precise HTTP status codes (e.g., 201 CREATED, 204 NO CONTENT)."
---

# Spring Boot RESTful API Implementation

Detailed instructions for the agent to implement a robust backend API.

## Overview

- Use when building RESTful API endpoints and core business logic for a new domain from scratch.
- This is useful for quickly scaffolding and implementing backend code based on a stable 3-tier architecture to communicate with frontends or clients.

## When to Use This Skill

- When creating CRUD APIs to manipulate database tables (Entities) after the database design is complete.
- When adding new features that receive and validate external requests, execute specific business logic, and return a response.
- When refactoring by separating existing complex logic into the Service layer.

## How It Works

### Step 1: [Define DTOs]
Create Request DTOs to receive client requests and Response DTOs to return responses using `Record` classes. Enforce input validation strictly using annotations from the `jakarta.validation.constraints.*` package (e.g., `@NotBlank`, `@NotNull`).

### Step 2: [Implement Repository]
Create an interface extending `JpaRepository` from Spring Data JPA. Define custom query methods only if complex data retrieval is required beyond standard CRUD operations.

### Step 3: [Implement Service]
Use the `@Service` annotation and manage transactions with `@Transactional`. Inject the Repository using constructor injection (`@RequiredArgsConstructor`) to write core business logic. Perform conversion logic between Entities and DTOs strictly using static factory methods (e.g., `from()`) located inside the DTO classes. Throw standard exceptions (e.g., `IllegalArgumentException`) or existing predefined custom exceptions instead of generating arbitrary new exception classes.

### Step 4: [Implement Controller]
Configure routing using `@RestController` and `@RequestMapping`. Inject the Service layer using constructor injection. Construct responses using standard `ResponseEntity`. Ensure correct HTTP status codes are explicitly returned (e.g., `HttpStatus.CREATED` for resource creation, `HttpStatus.OK` for retrieval). Utilize the `version` attribute in mapping annotations (e.g., `@PostMapping(version = "1")`) for API versioning.

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
