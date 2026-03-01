---
name: spring-querydsl
description: Spring Data JPA와 QueryDSL을 사용하여 복잡한 동적 쿼리와 커스텀 레포지토리를 구현할 때 사용합니다.
argument-hint: "[조회할 대상 엔티티와 동적 검색 조건, 또는 Fetch Join이 필요한 연관관계 (예: 동적 제목 검색과 User 페치 조인이 포함된 Document 조회)]"
source: "custom"
tags: ["java", "spring-boot", "querydsl", "database", "backend"]
triggers:
  - "QueryDSL 작성"
  - "동적 쿼리 만들어"
  - "N+1 문제 해결해"
---

# Spring Boot QueryDSL Implementation

QueryDSL 커스텀 레포지토리 구현 및 동적 쿼리 작성 상세 지침입니다.

## Overview

- Spring Data JPA의 기본 메서드 이름 생성(`findBy...`)이나 `@Query` 어노테이션만으로는 처리하기 어려운 복잡한 동적 쿼리, 다중 검색 조건, 페이징 처리를 구현할 때 사용합니다.
- `fetchJoin()`을 적극 활용하여 JPA에서 발생하는 치명적인 N+1 쿼리 문제를 성능적으로 최적화하는 데 필수적인 스킬입니다.

## When to Use This Skill

- 사용자의 입력(검색어, 필터링 등)에 따라 `WHERE` 절의 조건이 동적으로 변해야 할 때
- 여러 엔티티가 연관되어 있어 한 번의 쿼리로 연관된 데이터를 모두 가져와야 할 때 (N+1 문제 해결)
- 타입 안정성(Type-safe)이 보장되는 복잡한 조인(Join) 로직 및 페이징 처리를 작성해야 할 때

## How It Works

### Step 1: [Create Custom Interface]
Spring Data JPA 레포지토리와 병합될 커스텀 인터페이스를 생성합니다. 이름은 반드시 `{EntityName}RepositoryCustom` 형식으로 작성하십시오 (예: `DocumentRepositoryCustom`).

### Step 2: [Implement Custom Interface]
생성한 커스텀 인터페이스의 구현체를 작성합니다. 클래스명은 반드시 `{EntityName}RepositoryImpl` 형식이어야 Spring이 자동으로 빈으로 인식합니다. 생성자 주입(`@RequiredArgsConstructor`)을 통해 `JPAQueryFactory`를 주입받으십시오.

### Step 3: [Write QueryDSL Logic]
자동 생성된 `Q` 클래스(예: `QDocument.document`)를 사용하여 타입에 안전한 쿼리를 작성합니다. 
- 동적 검색 조건은 반드시 별도의 `private` 메서드로 분리하여 `BooleanExpression`을 반환하도록 작성하고, 조건 값이 유효하지 않을 경우(null, 빈 문자열) `null`을 반환하여 `where()` 절에서 무시되도록 처리하십시오.
- 연관된 엔티티를 함께 조회할 때는 `join()` 직후에 반드시 `fetchJoin()`을 명시하여 N+1 문제를 방지하십시오.

### Step 4: [Extend Main Repository]
기존의 Spring Data JPA 레포지토리 인터페이스가 커스텀 인터페이스를 다중 상속하도록 수정하십시오.

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
    // searchDocuments 메서드를 기본 레포지토리에서 바로 사용할 수 있습니다.
}

```

## Best Practices

* **BooleanExpression 조합:** `BooleanBuilder` 사용을 지양하고, `BooleanExpression`을 반환하는 메서드들을 작성하십시오. 필요하다면 `titleContains(title).and(authorNameEq(name))` 처럼 메서드 체이닝을 통해 조건들을 재사용하고 결합하십시오.
* **Q 클래스 Static Import:** 코드의 가독성을 높이기 위해 `QDocument.document`와 같은 인스턴스는 가급적 static import를 활용하여 `document`로 축약해 사용하십시오.

## Common Pitfalls

* **Deprecated 페이징 메서드 사용 (QueryDSL 5.0+):** 페이징 처리 시 `fetchResults()`와 `fetchCount()`는 더 이상 사용하지 마십시오(Deprecated). 대신 데이터를 조회하는 `fetch()` 쿼리와 총 개수를 구하는 카운트 쿼리를 별도로 작성하여 `PageableExecutionUtils.getPage()`로 조합하십시오.
* **명명 규칙 위반:** 구현체(Implementation Class)의 이름이 `~Impl`로 끝나지 않으면 Spring Data JPA가 해당 클래스를 찾지 못해 런타임 에러(`PropertyReferenceException`)가 발생합니다.

## Related Skills

* `spring-jpa-entity-design`: QueryDSL로 조회할 기반 엔티티를 설계하고 최적화할 때 먼저 사용합니다.
* `spring-rest-api`: 작성된 QueryDSL 레포지토리를 활용하여 클라이언트에게 응답을 반환하는 서비스 및 컨트롤러 레이어를 구현할 때 사용합니다.
