---
name: spring-boot-querydsl
description: Implement complex dynamic queries, pagination, and custom repositories using Spring Data JPA and QueryDSL.
argument-hint: "[조회할 대상 엔티티와 동적 검색 조건, 페이징 여부, 또는 Fetch Join/DTO 반환 요구사항 (예: 동적 제목 검색이 포함된 Document 페이징 조회 로직 작성)]"
source: "custom"
tags: ["java", "spring-boot", "querydsl", "database", "backend", "performance"]
triggers:
  - "QueryDSL 작성"
  - "N+1 문제 해결"
---

# Spring Boot QueryDSL Implementation

Detailed guidelines for implementing QueryDSL custom repositories, dynamic querying, and pagination.

## Overview

- Used to implement complex dynamic queries, multi-condition searches, and pagination in a type-safe manner, which are difficult to handle with Spring Data JPA's basic methods (`findBy...`) or `@Query`.
- Essential for solving the N+1 problem utilizing `fetchJoin()` and optimizing performance through direct DTO projections.

## When to Use This Skill

- When `WHERE` conditions must dynamically change based on user inputs like search terms or filters.
- When associated data from multiple entities needs to be fetched in a single query (N+1 query burst prevention).
- When safely fetching specific fields into a DTO (instead of the entire entity) along with pagination.

## How It Works

### Step 1: JPAQueryFactory Configuration

Spring Boot does not automatically register `JPAQueryFactory` as a Bean. You must pre-configure it in a `@Configuration` class by injecting the `EntityManager` and registering the `JPAQueryFactory` Bean.

### Step 2: Create Custom Interface

Create a custom interface to be merged with the Spring Data JPA repository.
- The name MUST follow the `{EntityName}RepositoryCustom` format.
- For multi-record retrieval, use `List<T>` or `Page<T>` as the return type.

### Step 3: Implement Custom Interface

Write the implementation of the custom interface.
- The class name MUST follow the `{EntityName}RepositoryImpl` format.
- Inject `JPAQueryFactory` using `@RequiredArgsConstructor`.

### Step 4: Write QueryDSL Logic & Projections

Use static imports for Q classes to write type-safe queries.
- **Dynamic Conditions:** Extract them into `private` methods that return a `BooleanExpression`. Return `null` if the value is invalid, which `where()` safely ignores.
- **DTO Projections:** For performance optimization, use `Projections.constructor()` or `@QueryProjection` to fetch directly into a DTO instead of an entity.
- **N+1 Prevention:** If returning an entity and associated objects are needed, explicitly append `fetchJoin()` immediately after the `join()`.

### Step 5: Pagination (QueryDSL 5.0+)

When pagination is required, strictly separate the data retrieval query from the total count query.
- Fetch content using `fetch()` instead of `fetchResults()`.
- Optimize the count query by targeting the PK, such as `select(entity.id.count())`.
- Return `PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne)` to construct an optimized pagination response.

## Examples

```java
// 1. Custom Interface
public interface DocumentRepositoryCustom {
    Page<DocumentDto> searchDocuments(String title, String authorName, Pageable pageable);
}

// 2. Implementation Class
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements DocumentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<DocumentDto> searchDocuments(String title, String authorName, Pageable pageable) {
        
        List<DocumentDto> content = queryFactory
                .select(Projections.constructor(DocumentDto.class,
                        document.id,
                        document.title,
                        user.username))
                .from(document)
                .leftJoin(document.author, user)
                .where(
                        titleContains(title),
                        authorNameEq(authorName)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                // Target PK for counting to ensure Hibernate 6 compatibility and optimize performance
                .select(document.id.count()) 
                .from(document)
                .leftJoin(document.author, user)
                .where(
                        titleContains(title),
                        authorNameEq(authorName)
                );

        // Optimizes by skipping the count query if content size is smaller than pageSize or if it's the last page
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 3. Dynamic Conditions
    private BooleanExpression titleContains(String title) {
        return StringUtils.hasText(title) ? document.title.contains(title) : null;
    }

    private BooleanExpression authorNameEq(String authorName) {
        return StringUtils.hasText(authorName) ? user.username.eq(authorName) : null;
    }
}

```

## Best Practices

* **BooleanExpression Combination:** Avoid `BooleanBuilder` and write methods that return `BooleanExpression` to enhance reusability and readability.
* **Static Import Q Classes:** For cleaner code, ALWAYS statically import instances like `QDocument.document`.
* **Covering Index Consideration:** When returning DTOs, maximize database performance by constructing the `select` clause to include only the indexed columns.

## Common Pitfalls

* **Mixing Collection Fetch Join with Pagination (Critical OOM):** If you apply `.offset().limit()` while `fetchJoin()` is used on a collection (`@OneToMany` relationship), JPA abandons database pagination. Instead, it pulls **ALL data into memory to perform In-memory Paging**, causing an immediate Out Of Memory (OOM) crash. For collection pagination, remove `fetchJoin` and rely on IN queries using the `default_batch_fetch_size` (BatchSize) option.
* **Deprecated Paging Methods (Critical):** Do NOT use `fetchResults()` and `fetchCount()` as they are deprecated in QueryDSL 5.0+. You MUST explicitly separate the data query and the count query as shown in the example.
* **Naming Convention Violation:** If the custom interface implementation does not end with `~Impl`, Spring Boot fails to find the Bean at runtime, throwing a `PropertyReferenceException`.
* **BooleanExpression NPE:** When chaining methods like `a.and(b)`, if `a` is `null`, a NullPointerException occurs. Pass multiple conditions as varargs to `where(a, b)` separated by commas, which safely ignores nulls internally.
* **Auto Sorting Unavailability:** The `Sort` object inside `Pageable` cannot be directly applied to QueryDSL. If dynamic sorting is needed, you must write a separate utility to translate `Sort` into QueryDSL's `OrderSpecifier`.

## Related Skills

* `spring-boot-jpa-entity`: Refer to this when designing the base Entities that will be queried using QueryDSL.
* `spring-rest-api`: Used when implementing the Controller logic that responds to the client with the results paginated by the QueryDSL repository.
