---
name: spring-boot-querydsl
description: Spring Data JPA와 QueryDSL을 사용하여 복잡한 동적 쿼리, 페이징, 그리고 커스텀 레포지토리를 구현할 때 사용합니다.
argument-hint: "[조회할 대상 엔티티와 동적 검색 조건, 페이징 여부, 또는 Fetch Join/DTO 반환 요구사항 (예: 동적 제목 검색이 포함된 Document 페이징 조회 로직 작성)]"
source: "custom"
tags: ["java", "spring-boot", "querydsl", "database", "backend", "performance"]
triggers:
  - "QueryDSL 작성"
  - "N+1 문제 해결"
---

# Spring Boot QueryDSL Implementation

QueryDSL 커스텀 레포지토리 구현, 동적 쿼리 및 페이징 처리를 위한 상세 지침입니다.

## Overview

- Spring Data JPA의 기본 메서드(`findBy...`)나 `@Query`로 처리하기 어려운 복잡한 동적 쿼리, 다중 검색 조건, 페이징을 타입 세이프(Type-safe)하게 구현할 때 사용합니다.
- `fetchJoin()`을 활용한 N+1 문제 해결과 DTO 직접 조회를 통한 성능 최적화에 필수적입니다.

## When to Use This Skill

- 검색어, 필터링 등 사용자 입력에 따라 `WHERE` 조건이 동적으로 변할 때.
- 여러 엔티티의 연관 데이터를 한 번의 쿼리로 가져와야 할 때 (N+1 쿼리 폭발 방지).
- 엔티티 전체가 아닌 특정 필드만 DTO로 안전하게 조회하고 페이징 처리를 해야 할 때.

## How It Works

### Step 1: JPAQueryFactory Configuration

Spring Boot는 `JPAQueryFactory`를 자동으로 빈 등록하지 않습니다. `@Configuration` 클래스에서 `EntityManager`를 주입받아 `JPAQueryFactory` 빈을 등록하는 설정이 선행되어야 합니다.

### Step 2: Create Custom Interface

Spring Data JPA 레포지토리와 병합될 커스텀 인터페이스를 생성합니다.
- 이름은 반드시 `{EntityName}RepositoryCustom` 형식이어야 합니다.
- 다건 조회 시 반환 타입은 `List<T>` 또는 `Page<T>`를 사용합니다.

### Step 3: Implement Custom Interface

커스텀 인터페이스의 구현체를 작성합니다.
- 클래스명은 반드시 `{EntityName}RepositoryImpl` 형식이어야 합니다.
- `@RequiredArgsConstructor`를 통해 `JPAQueryFactory`를 주입받습니다.

### Step 4: Write QueryDSL Logic & Projections

Q 클래스를 static import 하여 타입에 안전한 쿼리를 작성합니다.
- **동적 조건:** `private` 메서드로 분리하여 `BooleanExpression`을 반환하게 하고, 값이 무효하면 `null`을 반환하여 `where()`에서 무시되게 합니다.
- **DTO 반환:** 성능 최적화가 필요하다면 `Projections.constructor()` 또는 `@QueryProjection`을 사용하여 엔티티 대신 DTO로 바로 조회합니다.
- **N+1 방지:** 엔티티로 반환할 때 연관 객체가 필요하다면 `join()` 직후에 `fetchJoin()`을 명시합니다.

### Step 5: Pagination (QueryDSL 5.0+)

페이징이 필요한 경우, 데이터 조회 쿼리와 총 개수(Count) 조회 쿼리를 엄격히 분리합니다.
- `fetchResults()` 대신 `fetch()`로 컨텐츠를 가져옵니다.
- 카운트 쿼리 작성 시 `select(entity.id.count())`처럼 PK를 지정하여 성능을 최적화합니다.
- `PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne)`를 반환하여 최적화된 페이징 응답을 만듭니다.

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
                // Hibernate 6 호환성 및 카운트 성능 향상을 위해 PK로 카운트 지정
                .select(document.id.count()) 
                .from(document)
                .leftJoin(document.author, user)
                .where(
                        titleContains(title),
                        authorNameEq(authorName)
                );

        // content가 pageSize보다 작거나 마지막 페이지일 경우 count 쿼리 생략 최적화
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

* **BooleanExpression 조합:** `BooleanBuilder`를 지양하고, `BooleanExpression`을 반환하는 메서드들을 작성하여 재사용성과 가독성을 높이십시오.
* **Q 클래스 Static Import:** 코드 가독성을 위해 `QDocument.document`와 같은 인스턴스는 반드시 static import 하여 축약해 사용하십시오.
* **커버링 인덱스 고려:** DTO 반환 시 인덱스가 걸려있는 컬럼들만 Select 하도록 구성하면 DB 성능을 극대화할 수 있습니다.

## Common Pitfalls

* **컬렉션 페치 조인과 페이징 혼용 (Critical OOM):** `@OneToMany` 관계의 컬렉션을 `fetchJoin()` 하면서 동시에 `.offset().limit()`을 주면, JPA는 DB에서 페이징을 하지 못하고 **모든 데이터를 메모리로 끌고 와서 페이징(In-memory Paging)**을 시도하여 즉각적인 OOM을 유발합니다. 컬렉션 페이징이 필요하다면 `fetchJoin`을 제거하고 `default_batch_fetch_size` (BatchSize) 옵션을 활용한 IN 쿼리로 해결해야 합니다.
* **Deprecated 페이징 메서드 (Critical):** QueryDSL 5.0+ 부터 `fetchResults()`와 `fetchCount()`는 더 이상 사용하지 마십시오(Deprecated). 예제처럼 반드시 데이터 쿼리와 카운트 쿼리를 명시적으로 분리해야 합니다.
* **명명 규칙 위반:** 커스텀 인터페이스 구현체의 이름이 `~Impl`로 끝나지 않으면 런타임 시 Spring Boot가 빈을 찾지 못해 `PropertyReferenceException`이 발생합니다.
* **BooleanExpression NPE:** `a.and(b)` 형태로 메서드 체이닝을 할 때, 앞의 `a`가 `null`이면 NullPointerException이 발생합니다. 다중 조건을 결합할 때는 가변 인자인 `where(a, b)`로 콤마(,)를 통해 넘기면 내부적으로 null을 안전하게 무시합니다.
* **자동 정렬 불가:** `Pageable`의 `Sort` 객체는 QueryDSL에 바로 적용할 수 없습니다. 동적 정렬이 필요하다면 별도의 `OrderSpecifier` 변환 유틸리티를 작성해야 합니다.

## Related Skills

* `spring-boot-jpa-entity`: QueryDSL로 조회할 기반 엔티티(Entity)를 설계할 때 참조합니다.
* `spring-rest-api`: 작성된 QueryDSL 레포지토리와 페이징 결과를 클라이언트에게 응답하는 컨트롤러 로직을 구현할 때 사용합니다.
