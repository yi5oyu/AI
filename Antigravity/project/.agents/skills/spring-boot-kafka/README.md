---
name: spring-boot-kafka
description: Spring Boot 3.x 환경에서 Apache Kafka를 연동하여 마이크로서비스 간 비동기 통신(Event-Driven)을 구현하고, 메시지 유실 및 역직렬화 에러를 방어합니다.
argument-hint: "[발행(Produce)/구독(Consume)할 토픽 이름, 메시지 페이로드 객체, Consumer 그룹 ID (예: 회원가입 완료 이벤트를 User 토픽에 JSON으로 발행하고 소비하는 로직 작성)]"
source: "custom"
tags: ["java", "spring-boot", "kafka", "messaging", "msa", "event-driven", "backend", "async"]
triggers:
  - "Kafka 연동"
  - "메시지 큐 설정"
---

# Spring Boot Apache Kafka Integration

마이크로서비스 간의 강결합(동기 HTTP 호출)을 끊어내고, 높은 처리량(Throughput)과 내결함성(Fault Tolerance)을 보장하는 이벤트 주도 아키텍처(Event-Driven Architecture) 구현 지침입니다. 카프카는 순수한 메시지 브로커(전달자) 역할만 수행합니다.

## Overview

- **Producer (발행자):** 비즈니스 로직 처리 후 상태 변경 이벤트를 Kafka 토픽(Topic)으로 비동기 전송(`KafkaTemplate`)합니다. 메인 스레드의 응답 속도에 영향을 주지 않습니다.
- **Consumer (구독자):** 다른 마이크로서비스에서 `@KafkaListener`를 통해 이벤트를 수신하고 자신의 DB에 데이터를 반영하거나 후속 작업(알림 전송 등)을 처리합니다.
- **안전한 직렬화:** 객체를 JSON 문자열로 변환하여 전송하며, 역직렬화 실패 시 컨슈머가 죽거나 무한 재시도에 빠지는 것을 막기 위해 `String`으로 수신 후 `ObjectMapper`로 직접 파싱하는 패턴을 권장합니다.

## When to Use This Skill

- **서비스 분리 (Decoupling):** 주문 완료 시 '결제', '재고 차감', '알림톡 발송' 등 여러 도메인의 로직이 동시에 실행되어야 할 때 (단일 API에 모두 넣으면 하나의 장애가 전체 장애로 번짐).
- **대용량 트래픽 완충 (Buffering):** 초당 수십만 건의 데이터가 쏟아질 때, DB에 바로 쓰지 않고 Kafka에 먼저 쌓아둔 뒤 Consumer가 DB가 버틸 수 있는 속도로 꺼내서 처리하게 할 때.
- **데이터 동기화 (CQRS/Saga):** 여러 마이크로서비스 간에 분산된 데이터베이스의 정합성을 최종적으로 맞추기 위해 이벤트를 발행할 때.

## How It Works

### Step 1: Add Dependency
`build.gradle`에 Spring Kafka 의존성을 추가합니다.
- `org.springframework.kafka:spring-kafka`

### Step 2: Configuration (`application.yml`)
Producer와 Consumer의 기본 동작을 설정합니다.
- **Serializer:** Key와 Value 모두 `StringSerializer`/`StringDeserializer`를 기본으로 사용합니다.
- **Ack-mode:** 데이터 유실을 막기 위해 Consumer의 커밋 방식을 기본값(`BATCH`)에서 `MANUAL` 또는 `MANUAL_IMMEDIATE`로 변경합니다.

### Step 3: Implement Producer
`KafkaTemplate`을 주입받아 이벤트를 발행합니다. Spring Boot 3.0 이상에서는 `send()` 메서드가 `CompletableFuture`를 반환하므로 논블로킹(Non-blocking) 콜백 처리가 가능합니다.

### Step 4: Implement Consumer
`@KafkaListener` 어노테이션을 사용하여 토픽을 구독합니다. 비즈니스 로직이 성공적으로 완료된 경우에만 `Acknowledgment.acknowledge()`를 호출하여 메시지를 커밋(소비 완료 처리)합니다.

## Examples

```java
// 1. Kafka 설정 (Consumer 수동 커밋 팩토리)
@Configuration
public class KafkaConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // 중요: 데이터 유실 방지를 위한 수동 커밋 모드 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
}

// 2. Producer (이벤트 발행)
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "order-events";

    public void sendOrderCreatedEvent(OrderDto orderDto) {
        // 메시지 키(Key)를 지정하여 파티션 순서 보장
        String key = String.valueOf(orderDto.getId());
        
        kafkaTemplate.send(TOPIC, key, orderDto).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Kafka 메시지 발행 성공: topic={}, offset={}", 
                         result.getRecordMetadata().topic(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("Kafka 메시지 발행 실패: {}", ex.getMessage());
            }
        });
    }
}

// 3. Consumer (이벤트 구독)
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;

    // groupId를 명시하여 Consumer Group 구성
    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void consumeOrderEvent(String message, Acknowledgment ack) {
        try {
            // String으로 받은 JSON 메시지를 안전하게 객체로 역직렬화 (Poison Pill 방어)
            OrderDto event = objectMapper.readValue(message, OrderDto.class);
            
            log.info("이벤트 수신 완료. 로직 진행: {}", event.getId());
            // ... 비즈니스 로직 ...

            // 로직이 완벽하게 성공했을 때만 커밋(ACK) 처리하여 메시지 유실 방지
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("Kafka 메시지 소비 중 에러 발생: {}", e.getMessage());
            // 예외 발생 시 ack를 호출하지 않으면 재시도(Retry)되거나 DLQ로 이동함
        }
    }
}

```

## Best Practices

* **수동 커밋(Manual ACK) 강제:** 비즈니스 로직 도중 서버가 강제 종료되면 메시지가 유실될 수 있습니다. 중요한 데이터라면 `MANUAL_IMMEDIATE`로 설정하고 로직의 가장 마지막에 `ack.acknowledge()`를 명시적으로 호출하십시오.
* **토픽 파티션 키(Partition Key) 활용:** 주문 상태 변경처럼 순서가 반드시 보장되어야 하는 이벤트는 발행할 때 항상 동일한 Key(예: `orderId`)를 지정해야 동일한 파티션에 할당되어 순서대로 처리됩니다.

## Common Pitfalls

* **독약 메시지(Poison Pill) 무한 루프 (Critical):** Spring의 `JsonDeserializer`를 기본으로 설정해 두었을 때, 토픽에 잘못된 형식의 JSON이 들어오면 `SerializationException`이 발생하여 컨슈머가 끝없이 재시도(Infinite Loop)하며 멈춰버립니다. 예제처럼 메시지를 순수 `String`으로 받고 로직 내부에서 `ObjectMapper`로 직접 파싱하는 방식을 권장합니다.
* **비동기 스레드 블로킹 (Critical):** `@KafkaListener` 메서드 내부에서 타임아웃이 긴 외부 API를 동기식으로 호출하면, 파티션을 점유한 컨슈머 스레드가 블로킹되어 전체 메시지 처리량(Throughput)이 급감합니다.
* **멱등성(Idempotency) 부재:** 네트워크 지연으로 인해 동일한 메시지가 2번 이상 전달(At-least-once)될 수 있습니다. 컨슈머 비즈니스 로직은 같은 이벤트가 여러 번 들어와도 DB 상태가 꼬이지 않도록 멱등성을 반드시 확보해야 합니다.

## Related Skills

* `database-erd-design`: 컨슈머가 이벤트를 처리할 때 중복 처리를 방어하기 위해 테이블에 `processed_event_id` 컬럼을 추가할 때 활용합니다.
