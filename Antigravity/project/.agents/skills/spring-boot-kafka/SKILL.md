---
name: spring-boot-kafka
description: Integrate Apache Kafka in a Spring Boot 3.x environment to implement asynchronous communication (Event-Driven Architecture) between microservices, preventing message loss and deserialization errors.
argument-hint: "[발행(Produce)/구독(Consume)할 토픽 이름, 메시지 페이로드 객체, Consumer 그룹 ID (예: 회원가입 완료 이벤트를 User 토픽에 JSON으로 발행하고 소비하는 로직 작성)]"
source: "custom"
tags: ["java", "spring-boot", "kafka", "messaging", "msa", "event-driven", "backend", "async"]
triggers:
  - "Kafka 연동"
  - "메시지 큐 설정"
---

# Spring Boot Apache Kafka Integration

Guidelines for implementing an Event-Driven Architecture that breaks tight coupling (synchronous HTTP calls) between microservices and guarantees high throughput and fault tolerance. Kafka acts strictly as a pure message broker.

## Overview

- **Producer:** Asynchronously sends state-change events to a Kafka Topic (`KafkaTemplate`) after processing business logic, without affecting the main thread's response time.
- **Consumer:** Receives events from other microservices via `@KafkaListener` and processes subsequent tasks (e.g., DB updates, sending notifications).
- **Safe Serialization:** Objects are converted to JSON strings for transmission. To prevent the consumer from crashing or entering an infinite retry loop upon deserialization failures, it is recommended to receive messages as `String` and manually parse them using `ObjectMapper`.

## When to Use This Skill

- **Service Decoupling:** When multiple domains (e.g., Payment, Inventory Deduction, Notification) need to execute simultaneously upon an action like order completion. 
- **Large-scale Traffic Buffering:** When hundreds of thousands of requests pour in per second. Instead of writing directly to the DB, stack them in Kafka first, allowing the Consumer to process them at a pace the DB can handle.
- **Data Synchronization (CQRS/Saga):** When publishing events to achieve eventual consistency across distributed databases in an MSA.

## How It Works

### Step 1: Add Dependency
Add the Spring Kafka dependency to `build.gradle`.
- `org.springframework.kafka:spring-kafka`

### Step 2: Configuration (`application.yml`)
Configure default behaviors for Producer and Consumer.
- **Serializer:** Use `StringSerializer`/`StringDeserializer` as defaults for both Key and Value.
- **Ack-mode:** Change the consumer's commit mode from the default (`BATCH`) to `MANUAL` or `MANUAL_IMMEDIATE` to prevent data loss.

### Step 3: Implement Producer
Inject `KafkaTemplate` to publish events. In Spring Boot 3.0+, the `send()` method returns a `CompletableFuture`, allowing for non-blocking callbacks.

### Step 4: Implement Consumer
Subscribe to topics using the `@KafkaListener` annotation. Call `Acknowledgment.acknowledge()` to commit the message ONLY when the business logic completes successfully.

## Examples

```java
// 1. Kafka Configuration (Manual Commit Consumer Factory)
@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Critical: Set manual commit mode to prevent data loss
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
}

// 2. Producer (Event Publishing)
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "order-events";

    public void sendOrderCreatedEvent(OrderDto orderDto) {
        // Specify a Key to guarantee partition ordering
        String key = String.valueOf(orderDto.getId());
        
        kafkaTemplate.send(TOPIC, key, orderDto).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka message published: topic={}, offset={}", 
                         result.getRecordMetadata().topic(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish Kafka message: {}", ex.getMessage());
            }
        });
    }
}

// 3. Consumer (Event Subscription)
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;

    // Specify groupId to form a Consumer Group
    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void consumeOrderEvent(String message, Acknowledgment ack) {
        try {
            // Safely deserialize the JSON message received as String (Poison Pill defense)
            OrderDto event = objectMapper.readValue(message, OrderDto.class);
            
            log.info("Event received. Processing: {}", event.getId());
            // ... Business Logic ...

            // Commit (ACK) ONLY when logic perfectly succeeds to prevent data loss
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Error consuming Kafka message: {}", e.getMessage());
            // If ack is not called due to an exception, the message will be retried or sent to DLQ
        }
    }
}

```

## Best Practices

* **Force Manual ACK:** If the server forcefully shuts down during business logic execution, messages can be lost under automatic commits. For critical data, set to `MANUAL_IMMEDIATE` and explicitly call `ack.acknowledge()` at the very end of the logic.
* **Utilize Partition Keys:** Events that strictly require ordering (e.g., status changes: Created -> Paid -> Shipped) MUST be published with the identical Key (e.g., `orderId`) to ensure they are assigned to the same partition and processed sequentially.

## Common Pitfalls

* **Poison Pill Infinite Loop (Critical):** If Spring's `JsonDeserializer` is set globally and a malformed JSON arrives in the topic, a `SerializationException` occurs. The consumer will infinite-loop retries, halting the entire partition. It is highly recommended to receive messages as pure `String` and manually parse them with `ObjectMapper` as shown in the example.
* **Async Thread Blocking (Critical):** If you make synchronous calls to slow external APIs inside a `@KafkaListener` method, the consumer thread holding the partition will be blocked, causing a massive drop in overall throughput.
* **Lack of Idempotency:** Due to network latency, the identical message might be delivered more than once (At-least-once delivery). Consumer business logic MUST be idempotent (e.g., by checking a `processed_event_id` column in the DB) to prevent data corruption from duplicate events.

## Related Skills

* `database-erd-design`: Utilize this when adding columns like `processed_event_id` to tables to defend against duplicate processing (idempotency) upon event consumption.
