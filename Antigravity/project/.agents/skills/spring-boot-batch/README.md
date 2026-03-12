---
name: spring-boot-batch
description: Spring Boot 4.x (Spring Batch 6) 환경에서 1억 건 이상의 대용량 데이터를 가상 스레드(Virtual Threads)와 청크(Chunk) 기반으로 처리하여 성능을 극대화하고 OOM을 방지합니다.
argument-hint: "[배치 목적, 데이터 소스, Chunk 사이즈 (예: Spring Boot 4.x에서 가상 스레드를 사용하여 매일 정산을 처리하는 배치 Job 작성)]"
source: "custom"
tags: ["java", "spring-boot-4", "batch", "virtual-threads", "performance", "msa", "data-pipeline"]
triggers:
  - "스프링 배치 작성"
  - "Virtual Thread 배치"
---

# Spring Boot 4.x Batch Processing Guide

Spring Boot 4.x의 핵심인 Java 21+ 가상 스레드(Virtual Threads)를 활용하여, 수억 건의 데이터를 적은 리소스로 빠르게 처리하는 최신 배치 아키텍처 지침입니다.

## Overview

- **Spring Batch 6.x 호환:** 낡은 빌더 팩토리를 배제하고, `JobRepository`와 `PlatformTransactionManager`를 명시적으로 주입하는 최신 빌더 패턴을 적용합니다.
- **Virtual Thread 최적화:** I/O 집약적인 배치 작업(DB 읽기/쓰기)에서 가상 스레드를 사용하여 컨텍스트 스위칭 오버헤드를 줄이고 처리량(Throughput)을 극대화합니다.
- **Chunk-oriented Design:** 트랜잭션 안전성을 보장하며, 지정된 Chunk 단위로 대용량 데이터를 일괄 처리합니다.

## How It Works

### Step 1: Enable Virtual Threads
Spring Boot 4.x의 가장 큰 이점을 누리기 위해 `application.yml` 설정을 활성화합니다.
- `spring.threads.virtual.enabled: true`

### Step 2: Modern Builder Implementation
- `@EnableBatchProcessing`을 생략하고 Spring Boot의 자동 구성을 활용합니다.
- `JobBuilder`와 `StepBuilder`에 필요한 인프라 객체들을 직접 주입받아 선언적으로 구성합니다.

### Step 3: Efficient Item Reading
- DB 데이터를 메모리 효율적으로 읽어옵니다. 데이터가 수백만 건 단위라면 `JpaPagingItemReader`를, 1억 건 이상의 극대용량이라면 반드시 `JdbcCursorItemReader`나 No-Offset 기반의 페이징 리더를 구현합니다.

## Examples

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ModernBatchConfig {

    private final EntityManagerFactory entityManagerFactory;

    @Bean
    public Job modernDataJob(JobRepository jobRepository, Step modernStep) {
        return new JobBuilder("modernDataJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(modernStep)
                .build();
    }

    @Bean
    public Step modernStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("modernStep", jobRepository)
                .<InputDto, OutputEntity>chunk(1000, transactionManager)
                .reader(itemReader())
                .processor(itemProcessor())
                .writer(itemWriter())
                // 가상 스레드 기반 병렬 스텝 처리를 원할 때 활성화
                // .taskExecutor(new VirtualThreadTaskExecutor()) 
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<InputDto> itemReader() {
        return new JpaPagingItemReaderBuilder<InputDto>()
                .name("itemReader")
                .entityManagerFactory(entityManagerFactory)
                // 주의: 데이터가 1천만 건 이상이면 LIMIT/OFFSET 방식의 페이징 대신 커서나 No-Offset 쿼리 사용 고려
                .queryString("SELECT i FROM InputDto i WHERE i.processed = false ORDER BY i.id ASC")
                .pageSize(1000)
                .build();
    }

    @Bean
    public ItemProcessor<InputDto, OutputEntity> itemProcessor() {
        return InputDto::toEntity;
    }

    @Bean
    public JpaItemWriter<OutputEntity> itemWriter() {
        return new JpaItemWriterBuilder<OutputEntity>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}

```

## Best Practices

* **Partitioning으로 가상 스레드 극대화:** 1억 건의 데이터를 하나의 스텝으로 처리하면 너무 오래 걸립니다. 데이터를 범위별로(예: ID 1~100만, 100만~200만) 쪼개는 `Partitioner`를 구현하고, 워커 스텝에 `VirtualThreadTaskExecutor`를 부여하여 무한한 동시성을 이끌어내십시오.
* **Grafana 대시보드 연동 모니터링:** 수억 건의 데이터 처리는 HikariCP 커넥션 풀 고갈이나 DB 병목을 유발하기 쉽습니다. Spring Boot 4.x의 `Micrometer`를 연동하여 Prometheus와 Grafana에서 `batch.job.execution` 및 DB 커넥션 지표를 실시간으로 모니터링하십시오.

## Common Pitfalls

* **딥 페이징(Deep Pagination)으로 인한 DB 타임아웃 (Critical):** `JpaPagingItemReader`는 내부적으로 `LIMIT / OFFSET` 쿼리를 사용합니다. 페이지 번호가 높아질수록 DB는 앞의 데이터를 전부 읽고 버리는 동작을 반복하므로 쿼리 성능이 기하급수적으로 하락합니다. 데이터가 방대할 경우 반드시 `WHERE id > :lastId` 형태의 No-Offset 페이징이나, `JdbcCursorItemReader` 스트리밍 방식을 사용해야 합니다.
* **구형 팩토리 사용 (Critical):** `StepBuilderFactory` 등을 사용하면 Spring Boot 4.x에서는 컴파일 에러가 발생합니다. 반드시 `new StepBuilder(...)` 형식을 유지하십시오.
* **가상 스레드 Pinning 현상:** `synchronized` 블록 내에서 무거운 I/O를 수행하면 가상 스레드가 실제 OS 스레드를 점유해버려 성능이 급감합니다. 가급적 `ReentrantLock`을 사용하도록 코드를 점검하십시오.

## Related Skills

* `spring-boot-docker-build`: 배치가 실행되는 컨테이너 환경의 메모리/CPU 자원 한계치(Limits)를 설정할 때 참조합니다.
* `spring-boot-kafka`: 가공이 완료된 데이터를 다른 마이크로서비스로 비동기 전파할 때 사용합니다.
