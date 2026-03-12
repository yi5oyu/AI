---
name: spring-boot-batch
description: Leverage Virtual Threads and Chunk-based processing to maximize performance and prevent OOM errors for 100M+ data points in a Spring Boot 4.x (Spring Batch 6) environment.
argument-hint: "[배치 목적, 데이터 소스, Chunk 사이즈 (예: Spring Boot 4.x에서 가상 스레드를 사용하여 매일 정산을 처리하는 배치 Job 작성)]"
source: "custom"
tags: ["java", "spring-boot-4", "batch", "virtual-threads", "performance", "msa", "data-pipeline"]
triggers:
  - "스프링 배치 작성"
  - "Virtual Thread 배치"
---

# Spring Boot 4.x Batch Processing Guide

Guidelines for high-performance batch architecture that processes hundreds of millions of records with fewer resources by utilizing Java 21+ Virtual Threads, a core feature of Spring Boot 4.x.

## Overview

- **Spring Batch 6.x Compatibility:** Adheres to the modern builder pattern by explicitly injecting `JobRepository` and `PlatformTransactionManager`, bypassing deprecated builder factories.
- **Virtual Thread Optimization:** Maximizes throughput and reduces context-switching overhead in I/O-intensive batch tasks (DB reads/writes) using Virtual Threads.
- **Chunk-oriented Design:** Ensures transactional integrity by processing massive data in specified Chunk units, preventing Out Of Memory (OOM) issues.

## How It Works

### Step 1: Enable Virtual Threads
Activate the following in `application.yml` to unlock the primary performance benefit of Spring Boot 4.x.
- `spring.threads.virtual.enabled: true`

### Step 2: Modern Builder Implementation
- Skip `@EnableBatchProcessing` to rely on Spring Boot's auto-configuration.
- Explicitly inject infrastructure objects into `JobBuilder` and `StepBuilder` for declarative configuration.

### Step 3: Efficient Item Reading
- Fetch DB data memory-efficiently. If dealing with millions of rows, use `JpaPagingItemReader`. For extreme scales (100M+ rows), you MUST implement `JdbcCursorItemReader` or No-Offset paging to avoid DB timeouts.

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
                // Enable Virtual Thread-based parallel processing if needed
                // .taskExecutor(new VirtualThreadTaskExecutor()) 
                .build();
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<InputDto> itemReader() {
        return new JpaPagingItemReaderBuilder<InputDto>()
                .name("itemReader")
                .entityManagerFactory(entityManagerFactory)
                // Caution: For 10M+ rows, consider Cursor or No-Offset queries instead of LIMIT/OFFSET paging
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

* **Maximize Virtual Threads with Partitioning:** Processing 100M+ rows in a single step takes too long. Implement a `Partitioner` to divide the data into ranges (e.g., ID 1~1M, 1M~2M) and assign a `VirtualThreadTaskExecutor` to the worker steps to unleash massive concurrency.
* **Grafana Dashboard Monitoring:** Processing hundreds of millions of records easily causes HikariCP connection pool exhaustion or DB bottlenecks. Integrate with Spring Boot 4.x's `Micrometer` to monitor `batch.job.execution` and DB connection metrics in real-time on Grafana.

## Common Pitfalls

* **DB Timeouts Due to Deep Pagination (Critical):** `JpaPagingItemReader` internally uses `LIMIT / OFFSET` queries. As the page number grows, the DB has to scan and discard preceding rows, causing query performance to degrade exponentially. For massive datasets, you MUST use No-Offset paging (`WHERE id > :lastId`) or the streaming approach of `JdbcCursorItemReader`.
* **Using Legacy Factories (Critical):** Using `StepBuilderFactory` will result in compilation errors in Spring Boot 4.x. Maintain the `new StepBuilder(...)` format.
* **Virtual Thread Pinning:** Performing heavy I/O inside `synchronized` blocks can cause Virtual Threads to pin actual OS threads, severely degrading performance. Check your code to use `ReentrantLock` instead.

## Related Skills

* `spring-boot-docker-build`: Refer to this to configure the container's memory/CPU limits where the batch job will be executed.
* `spring-boot-kafka`: Used to asynchronously propagate the processed data to other microservices.
