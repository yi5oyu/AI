---
name: spring-jpa-entity-design
description: 요구사항이나 ERD를 바탕으로 안전하고 최적화된 Spring Data JPA 엔티티 코드를 설계하고 구현합니다.
argument-hint: "[구현할 엔티티의 이름이나 속성, 또는 ERD 텍스트 (예: User와 Post 엔티티 만들어줘)]"
source: "custom"
tags: ["java", "spring-boot", "jpa", "database", "backend"]
triggers:
  - "엔티티 설계"
  - "엔티티 만들어"
---

# Spring Data JPA Entity Design

데이터베이스 스키마를 설계하고 견고한 Spring Data JPA 엔티티를 구현하기 위한 상세 지침입니다.

## Overview

- 새로운 도메인을 모델링하고 데이터베이스 테이블을 처음부터 설계할 때 사용합니다.
- 안전하고 최적화된 엔티티 생성에 중점을 두어, N+1 문제나 컬렉션의 `NullPointerException`과 같은 흔한 안티 패턴을 근본적으로 방지합니다.

## When to Use This Skill

- 새로운 데이터베이스 테이블이 필요한 신규 기능을 시작할 때
- 기존 도메인 엔티티 간의 관계(예: 1:N, N:M)를 정의하거나 수정할 때
- `database-erd-design` 스킬로 생성된 ERD를 실제 코드로 변환할 때

## How It Works

### Step 1: [Base Auditing 엔티티 정의]
`@MappedSuperclass`와 `@EntityListeners(AuditingEntityListener.class)`를 사용하여 `createdAt`, `updatedAt` 감사(Audit) 필드를 자동 관리하는 추상 클래스 `BaseEntity`를 생성합니다. **반드시 `jakarta.persistence.*` 패키지를 엄격하게 사용해야 하며, `javax.*`는 절대 사용하지 마십시오.**

### Step 2: [엔티티 클래스 구현]
도메인 클래스를 생성하고 `@Entity` 어노테이션을 추가합니다. `@Table(name = "table_names")`를 사용하여 테이블 이름을 복수형 명사로 명시적으로 정의합니다. 안전한 JPA 프록시 객체 생성을 위해 반드시 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 추가해야 합니다.

### Step 3: [컬럼 및 기본 키(PK) 매핑]
`@Id`와 `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용하여 기본 키를 정의합니다. `@Column`을 사용하여 제약 조건을 지정합니다. 예약어 충돌을 피해야 하는 특별한 경우가 아니라면 `@Column(name="...")` 하드코딩은 생략하고 Spring Boot의 기본 snake_case 변환 전략을 따릅니다.

### Step 4: [연관관계 및 컬렉션 정의]
연관관계(`@ManyToOne`, `@OneToMany`, `@OneToOne`)를 주의 깊게 정의합니다.
- **CRITICAL: 모든 `@ManyToOne` 및 `@OneToOne` 어노테이션에는 반드시 `fetch = FetchType.LAZY`를 명시적으로 지정해야 합니다.**
- `@OneToMany`의 경우, 선언과 동시에 빈 컬렉션으로 즉시 초기화합니다 (예: `= new ArrayList<>()`).
- 양방향 연관관계의 경우, 부모 엔티티에 '연관관계 편의 메서드(Convenience Method)'를 작성하여 양쪽 객체의 상태를 안전하게 동기화합니다.

## Examples

```java
// 1. Base Auditing Entity
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

// 2. Domain Entity
@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @Builder
    public Document(String title, String content, User author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public void updateContent(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void addComment(Comment comment) {
        this.comments.add(comment);
        comment.setDocument(this);
    }
}

```

## Best Practices

* **비즈니스 메서드 활용:** 무분별하게 `@Setter`를 노출하는 대신, 상태 변경을 처리하는 의미 있는 비즈니스 메서드(예: `updateContent()`)를 생성하여 도메인의 행위를 표현하십시오.
* **연관관계 편의 메서드:** 양방향 `@OneToMany` 관계에서 자식 엔티티를 추가할 때는 반드시 양쪽 객체의 참조를 모두 업데이트하는 편의 메서드를 구현하십시오.
* **안전한 빌더 패턴:** `@Builder`는 클래스 레벨이 아닌 직접 만든 **생성자** 위에 선언하여, ID나 컬렉션 필드가 빌더를 통해 외부에서 임의로 조작되는 것을 방지하십시오.

## Common Pitfalls

* **무분별한 Lombok 사용:** 엔티티 클래스에 `@Data`, `@ToString`, `@EqualsAndHashCode`를 **절대 사용하지 마십시오**. 양방향 관계에서 무한 루프(순환 참조)를 유발하고 JPA 프록시 객체의 동등성 검사를 실패하게 만듭니다.
* **EAGER 로딩 방치:** `@ManyToOne`의 기본 패치 전략(EAGER)을 `LAZY`로 명시적으로 변경하지 않으면, 예측 불가능하고 방대한 N+1 쿼리가 발생하여 성능이 심각하게 저하됩니다.

## Related Skills

* `spring-rest-api`: 설계된 엔티티를 기반으로 컨트롤러와 핵심 비즈니스 로직을 구현할 때 이 스킬을 이어서 사용합니다.
* `spring-querydsl`: 연관된 엔티티를 한 번에 가져오는 `fetchJoin()`을 사용하여 N+1 문제를 해결하거나 복잡한 동적 쿼리를 작성할 때 사용합니다.
