---
name: spring-querydsl
description: Use to implement complex dynamic queries and custom repositories using Spring Data JPA and QueryDSL.
argument-hint: "[조회할 대상 엔티티와 동적 검색 조건, 또는 Fetch Join이 필요한 연관관계 (예: 동적 제목 검색과 User 페치 조인이 포함된 Document 조회)]"
source: "custom"
tags: ["java", "spring-boot", "querydsl", "database", "backend"]
triggers:
  - "QueryDSL 작성"
  - "동적 쿼리 만들어"
  - "N+1 문제 해결해"
---

# Spring Boot QueryDSL Implementation

Detailed guidelines for implementing QueryDSL custom repositories and writing dynamic queries.

## Overview

- Use when it is difficult to handle complex dynamic queries, multiple search conditions, and pagination using only Spring Data JPA's basic method name creation (`findBy...`) or the `@Query` annotation.
- An essential skill for actively utilizing `fetchJoin()` to optimize performance by solving the critical N+1 query problem that occurs in JPA.

## When to Use This Skill

- When `WHERE` clause conditions need to change dynamically based on user input (search keywords, filtering, etc.).
- When multiple entities are associated, and all related data must be fetched in a single query (resolving the N+1 problem).
- When writing complex, type-safe join logic and pagination.

## How It Works

### Step 1: [Create Custom Interface]
Create a custom interface to be merged with the Spring Data JPA repository. The name MUST follow the `{EntityName}RepositoryCustom` format (e.g., `DocumentRepositoryCustom`).

### Step 2: [Implement Custom Interface]
Write the implementation for the created custom interface. The class name MUST follow the `{EntityName}RepositoryImpl` format for Spring to automatically register it as a bean. Use constructor injection (`@RequiredArgsConstructor`) to inject the `JPAQueryFactory`.

### Step 3: [Write QueryDSL Logic]
Write type-safe queries using the auto-generated `Q` classes (e.g., `QDocument.document`).
- Dynamic search conditions MUST be separated into distinct `private` methods that return a `BooleanExpression`. If the condition value is invalid (null, empty string), return `null` so it is safely ignored in the `where()` clause.
- When querying related entities together, immediately chain `fetchJoin()` after `join()` to prevent N+1 problems.

### Step 4: [Extend Main Repository]
Modify the existing Spring Data JPA repository interface to extend the custom interface alongside `JpaRepository` (multiple inheritance).

## Examples

```java
// 1. Custom Interface
public interface DocumentRepositoryCustom {
    List<Document> searchDocuments(String title, String authorName);
}

// 2. Implementation Class
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements DocumentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Document> searchDocuments(String title, String authorName) {
        QDocument document = QDocument.document;
        QUser user = QUser.user;

        return queryFactory
                .selectFrom(document)
                // Apply fetchJoin to retrieve the associated User in a single query
                .leftJoin(document.author, user).fetchJoin()
                .where(
                        titleContains(title),
                        authorNameEq(authorName)
                )
                .fetch();
    }

    // 3. Dynamic Condition Methods (BooleanExpression)
    private BooleanExpression titleContains(String title) {
        return StringUtils.hasText(title) ? QDocument.document.title.contains(title) : null;
    }

    private BooleanExpression authorNameEq(String authorName) {
        return StringUtils.hasText(authorName) ? QUser.user.username.eq(authorName) : null;
    }
}

// 4. Main Repository Extension
public interface DocumentRepository extends JpaRepository<Document, Long>, DocumentRepositoryCustom {
    // You can now use the searchDocuments method directly from the basic repository.
}

```

## Best Practices

* **Combine BooleanExpressions:** Avoid using `BooleanBuilder`. Instead, write methods that return `BooleanExpression`. If necessary, reuse and combine conditions via method chaining, like `titleContains(title).and(authorNameEq(name))`.
* **Static Import for Q Classes:** To improve code readability, use static imports for instances like `QDocument.document` and shorten them to simply `document`.

## Common Pitfalls

* **Deprecated Paging Methods (QueryDSL 5.0+):** Do NOT use `fetchResults()` and `fetchCount()` for pagination, as they are deprecated. Instead, write a `fetch()` query for retrieving data and a separate count query for the total number, then combine them using `PageableExecutionUtils.getPage()`.
* **Naming Convention Violation:** If the implementation class name does not end with `~Impl`, Spring Data JPA will fail to find it, resulting in a runtime error (`PropertyReferenceException`).

## Related Skills

* `spring-jpa-entity-design`: Use this first to design and optimize the base entities that will be queried using QueryDSL.
* `spring-rest-api`: Use this subsequently to implement the service and controller layers that return responses to the client using the created QueryDSL repository.
