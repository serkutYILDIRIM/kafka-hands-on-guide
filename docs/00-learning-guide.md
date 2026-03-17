# Apache Kafka Hands-On Guide — Comprehensive Learning Documentation

> **Purpose**: This guide walks you through every concept implemented in this project, from infrastructure setup to advanced producer and consumer patterns. All code examples are taken directly from the source files so you can follow along in your IDE.

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Infrastructure & Setup](#3-infrastructure--setup)
4. [Architecture Overview](#4-architecture-overview)
5. [Message Models](#5-message-models)
6. [Topic Configuration](#6-topic-configuration)
7. [Producer Configuration](#7-producer-configuration)
8. [Consumer Configuration](#8-consumer-configuration)
9. [Producer Patterns](#9-producer-patterns)
10. [Consumer Patterns](#10-consumer-patterns)
11. [Service Layer Design](#11-service-layer-design)
12. [Message Validation](#12-message-validation)
13. [REST API Reference](#13-rest-api-reference)
14. [Testing Strategy](#14-testing-strategy)
15. [Key Kafka Concepts Demonstrated](#15-key-kafka-concepts-demonstrated)
16. [Hands-On Exercises](#16-hands-on-exercises)
17. [Further Reading](#17-further-reading)

---

## 1. Project Overview

This project is a fully runnable Spring Boot application that demonstrates Apache Kafka's most important concepts through a **financial transaction event stream** use case. Instead of toy hello-world examples, every pattern is shown in the context of a real-world-style scenario: transferring money between accounts and routing notifications.

### What This Project Teaches

| Learning Area | Concept | Source File |
|---|---|---|
| Infrastructure | Kafka + Zookeeper + Kafka UI via Docker | `docker-compose.yml` |
| Topic Design | Partition count, replication, DLQ | `KafkaTopicConfig.java` |
| Producer Patterns | 5 different send strategies | `producer/` package |
| Consumer Patterns | 5 different consumption strategies | `consumer/` package |
| Error Handling | Retries, backoff, Dead Letter Queue | `ErrorHandlingConsumer.java` |
| Transactions | Exactly-once semantics | `TransactionalProducer.java` |
| Ordering | Key-based partition assignment | `PartitionedProducer.java` |
| Validation | Message contracts & fail-fast | `MessageValidationService.java` |
| REST API | Triggering patterns via HTTP | `DemoController.java` |
| Testing | Unit testing with mocked Kafka | `test/` package |

---

## 2. Technology Stack

```
Java          21
Spring Boot   3.5.x
Spring Kafka  (bundled with Boot)
Jackson       (JSON serialization)
Lombok        (boilerplate reduction)
Docker        (Kafka infrastructure)
JUnit 5       (testing)
Mockito       (mocking)
```

### Key Dependencies (`pom.xml`)

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 3. Infrastructure & Setup

### Docker Compose Services

The `docker-compose.yml` starts three services that form the complete local development environment:

```
┌──────────────────────────────────────────┐
│           Docker Network (kafka-network) │
│                                          │
│  ┌────────────────┐  ┌────────────────┐  │
│  │   Zookeeper    │  │     Kafka      │  │
│  │   port 2181    │  │ port 9092(int) │  │
│  │                │◄─│ port 9093(ext) │  │
│  └────────────────┘  └────────────────┘  │
│                             │            │
│                    ┌────────────────┐    │
│                    │   Kafka UI     │    │
│                    │   port 8080    │    │
│                    └────────────────┘    │
└──────────────────────────────────────────┘

Spring Boot App: port 8090 (connects via localhost:9093)
```

### Port Mapping

| Service | Internal Port | External Port | Purpose |
|---|---|---|---|
| Zookeeper | 2181 | 2181 | Cluster coordination |
| Kafka (internal) | 9092 | — | Broker-to-broker communication |
| Kafka (external) | 9093 | 9093 | Application connects here |
| Kafka UI | 8080 | 8080 | Visual monitoring |
| Spring Boot App | 8090 | 8090 | REST API |

### Starting the Environment

```bash
# 1. Start Kafka infrastructure
docker-compose up -d

# 2. Build and run the application
mvn spring-boot:run

# 3. Open Kafka UI in browser
open http://localhost:8080

# 4. Verify health
curl http://localhost:8090/api/demo/health
```

> **⚠️ Security Note**: The Docker Compose uses `ALLOW_PLAINTEXT_LISTENER=yes`. This is intentional for local learning. Never deploy this configuration to production — use `SASL_SSL` in real environments.

---

## 4. Architecture Overview

### Package Structure

```
io.github.serkutyildirim.kafka/
├── config/
│   ├── KafkaTopicConfig.java       ← Declares all 4 Kafka topics
│   ├── KafkaProducerConfig.java    ← Standard + transactional producer factories
│   └── KafkaConsumerConfig.java    ← 3 consumer factory variants
├── model/
│   ├── BaseMessage.java            ← Shared metadata (UUID, timestamp, type)
│   ├── DemoTransaction.java        ← Financial transfer event
│   ├── DemoNotification.java       ← Outbound communication event
│   ├── MessageStatus.java          ← CREATED / PROCESSING / COMPLETED / FAILED
│   ├── NotificationType.java       ← EMAIL / SMS / PUSH
│   └── Priority.java               ← LOW / MEDIUM / HIGH / CRITICAL
├── producer/
│   ├── SimpleProducer.java         ← Fire-and-forget
│   ├── ReliableProducer.java       ← Synchronous/blocking
│   ├── AsyncProducer.java          ← Non-blocking with callbacks
│   ├── TransactionalProducer.java  ← Exactly-once, atomic batch
│   └── PartitionedProducer.java    ← Key-based ordering
├── consumer/
│   ├── SimpleConsumer.java         ← Auto-commit, basic processing
│   ├── ManualAckConsumer.java      ← Explicit acknowledgment + retry + DLQ
│   ├── BatchConsumer.java          ← Bulk record processing
│   ├── ErrorHandlingConsumer.java  ← Retry/backoff/DLQ separation
│   └── GroupedConsumer.java        ← Consumer group demonstration
├── service/
│   ├── KafkaService.java           ← Orchestrates all producer calls
│   └── MessageValidationService.java ← Business-rule validation
└── controller/
    └── DemoController.java         ← HTTP endpoints for all patterns
```

### Data Flow

```
HTTP Request
     │
     ▼
DemoController
     │  validates request body (Bean Validation)
     ▼
KafkaService
     │  validates business rules (MessageValidationService)
     ▼
Producer (one of 5)
     │  serializes to JSON
     ▼
Kafka Broker (demo-messages topic)
     │
     ├──► SimpleConsumer       (group: simple-consumer-group)
     ├──► ManualAckConsumer    (group: manual-ack-group)
     ├──► BatchConsumer        (group: batch-consumer-group)
     ├──► ErrorHandlingConsumer(group: error-handling-group)
     └──► GroupedConsumer      (group: grouped-consumer-1)
               │
               └──► demo-dlq (on failure after max retries)
```

---

## 5. Message Models

### BaseMessage — The Foundation

Every Kafka message in this project extends `BaseMessage`, which auto-populates three critical fields:

```java
@SuperBuilder(toBuilder = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DemoTransaction.class, name = "DEMO_TRANSACTION"),
    @JsonSubTypes.Type(value = DemoNotification.class, name = "DEMO_NOTIFICATION")
})
public abstract class BaseMessage {
    @Builder.Default
    private final UUID messageId = UUID.randomUUID();   // idempotency key

    @Builder.Default
    private final Instant timestamp = Instant.now();    // UTC creation time

    private String messageType;                          // set by subclass
}
```

**Why these design choices?**

| Field | Type | Rationale |
|---|---|---|
| `messageId` | `UUID` | Generated independently per producer — no shared sequence/DB needed |
| `timestamp` | `Instant` | UTC absolute time — timezone-agnostic, works across regions |
| `messageType` | `String` | Discriminator for polymorphic deserialization |

**Polymorphic JSON**: The `@JsonTypeInfo` + `@JsonSubTypes` combination adds a `"type"` field to every serialized message. This lets a single consumer topic carry multiple event types safely:

```json
// DemoTransaction on the wire:
{
  "type": "DEMO_TRANSACTION",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-01-24T10:30:00Z",
  "sourceId": "ACC-001",
  "targetId": "ACC-002",
  "amount": 100.50,
  "currency": "USD",
  "status": "CREATED"
}
```

### DemoTransaction

Represents a financial transfer event. Key design decisions:

```java
public class DemoTransaction extends BaseMessage {
    @NotBlank @Size(max = 64)
    private final String sourceId;      // source account

    @NotBlank @Size(max = 64)
    private final String targetId;      // target account

    @NotNull @Positive
    private final BigDecimal amount;    // ← BigDecimal, NOT double!

    @NotBlank @Size(min=3, max=3) @Pattern(regexp = "^[A-Z]{3}$")
    private final String currency;      // ISO 4217 (USD, EUR, GBP...)

    @NotNull
    private final MessageStatus status;

    @Size(max = 255)
    private final String description;
}
```

> **Why `BigDecimal` for money?** Floating-point types (`double`, `float`) have binary precision issues. `0.1 + 0.2` in IEEE 754 is `0.30000000000000004`. In financial systems, this causes incorrect totals, incorrect balances, and audit failures. `BigDecimal` provides exact decimal arithmetic.

### DemoNotification

A lighter model for notification events, intentionally simpler than `DemoTransaction`:

```java
public class DemoNotification extends BaseMessage {
    @NotBlank @Size(max = 64)
    private final String recipientId;

    @NotBlank @Size(max = 500)
    private final String content;

    @NotNull
    private final NotificationType notificationType;  // EMAIL / SMS / PUSH

    @NotNull
    private final Priority priority;                  // LOW / MEDIUM / HIGH / CRITICAL
}
```

### Creating Messages (Builder Pattern)

```java
DemoTransaction tx = DemoTransaction.builder()
    .sourceId("ACC-001")
    .targetId("ACC-002")
    .amount(new BigDecimal("100.50"))
    .currency("USD")
    .status(MessageStatus.CREATED)
    .description("Monthly payment")
    .build();
// messageId and timestamp are auto-populated by BaseMessage
```

---

## 6. Topic Configuration

### The 4 Topics (`KafkaTopicConfig.java`)

| Topic | Partitions | Replicas | Purpose |
|---|---|---|---|
| `demo-messages` | 3 | 1 | Primary financial transactions |
| `demo-notifications` | 2 | 1 | Outbound notifications |
| `demo-priority` | 1 | 1 | Strictly ordered events |
| `demo-dlq` | 1 | 1 | Failed/poison messages |

```java
@Bean
public NewTopic demoMessagesTopic() {
    return TopicBuilder.name(DEMO_MESSAGES_TOPIC)
            .partitions(3)   // allows 3 parallel consumers per group
            .replicas(1)     // single broker setup; use 3 in production
            .build();
}

@Bean
public NewTopic demoDlqTopic() {
    return TopicBuilder.name(DEMO_DLQ_TOPIC)
            .partitions(1)   // DLQ is low-volume; one partition is enough
            .replicas(1)
            .build();
}
```

### Partition Strategy Explained

```
demo-messages (3 partitions):
  Partition 0: [offset 0] [offset 1] [offset 2] ...
  Partition 1: [offset 0] [offset 1] [offset 2] ...
  Partition 2: [offset 0] [offset 1] [offset 2] ...

Consumer Group "simple-consumer-group" (3 threads):
  Thread-0  → Partition 0  (exclusive ownership)
  Thread-1  → Partition 1
  Thread-2  → Partition 2
```

**Rules to remember:**
- **Partition count = max consumer parallelism per group**
- Adding a 4th consumer to a 3-partition topic = one consumer idle
- Multiple consumer groups each get their own copy of all messages

### Naming Conventions

- Lowercase, hyphen-separated: `demo-messages`, `demo-dlq`
- Suffix `-dlq` makes dead-letter topics immediately identifiable in dashboards
- Suffix `-notifications`, `-transactions` signals domain/purpose

> **Production Note**: Topics here use `replicas(1)` for single-broker local setup. In production, use `replicas(3)` and set `min.insync.replicas=2` to tolerate broker failures without data loss.

---

## 7. Producer Configuration

### Standard Producer (`KafkaProducerConfig.java`)

```java
private Map<String, Object> standardProducerConfigs() {
    Map<String, Object> configs = new LinkedHashMap<>();
    configs.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);       // externalized
    configs.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configs.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    configs.put(ACKS_CONFIG, "all");                                // wait for all ISR
    configs.put(RETRIES_CONFIG, 3);
    configs.put(ENABLE_IDEMPOTENCE_CONFIG, true);                   // no duplicate writes
    configs.put(LINGER_MS_CONFIG, 10);                              // batch nearby records
    configs.put(COMPRESSION_TYPE_CONFIG, "snappy");                 // CPU-efficient compression
    configs.put(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
    return configs;
}
```

### Configuration Property Deep-Dive

#### `acks=all`
The producer waits for the **leader AND all in-sync replicas (ISR)** to acknowledge before declaring success.

```
acks=0  → fire-and-forget at network level (fastest, no guarantee)
acks=1  → leader confirms write (leader failure can lose message)
acks=all → all ISR confirm (safest; this project's default)
```

#### `enable.idempotence=true`
Prevents duplicate writes caused by producer retries. Kafka assigns a sequence number per producer. If the broker receives the same sequence twice (retry after transient failure), it deduplicates automatically.

```
Without idempotence:
  Send attempt 1 → broker writes → network fails → producer retries
  Send attempt 2 → broker writes again → DUPLICATE!

With idempotence:
  Send attempt 1 → broker writes (seq=1) → network fails → producer retries
  Send attempt 2 → broker sees seq=1 already committed → IGNORED ✓
```

#### `linger.ms=10`
Producer waits up to 10ms before flushing a batch. Records arriving within this window are grouped into one batch → fewer network round trips → higher throughput.

#### `compression.type=snappy`
Snappy is the sweet spot between compression ratio and CPU usage. Good for general-purpose event streams.

| Algorithm | Compression Ratio | CPU Usage | Use Case |
|---|---|---|---|
| none | 1x | minimal | ultra-low latency |
| snappy | ~2x | low | **general purpose (this project)** |
| lz4 | ~2x | low | lowest latency with compression |
| gzip | ~3x | high | maximum compression |
| zstd | ~3x | medium | best ratio/CPU balance |

### Transactional Producer

```java
private Map<String, Object> transactionalProducerConfigs() {
    Map<String, Object> configs = new LinkedHashMap<>(standardProducerConfigs());
    configs.put(TRANSACTIONAL_ID_CONFIG, transactionIdPrefix + UUID.randomUUID());
    configs.put(ENABLE_IDEMPOTENCE_CONFIG, true); // required for transactions
    return configs;
}
```

> **Key insight**: Transactions require idempotence. The `transactional.id` must be stable per producer instance but unique across instances. Using a prefix + UUID prevents multiple app instances from fencing each other.

---

## 8. Consumer Configuration

### Three Consumer Factory Variants

```java
// 1. Standard auto-commit consumer (demo-consumer-group)
@Bean @Primary
public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> kafkaListenerContainerFactory() {
    factory.setConcurrency(3);           // 3 threads = 3 partitions
    return factory;
}

// 2. Manual acknowledgment consumer (manual-ack-group)
@Bean("manualAckListenerContainerFactory")
public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> manualAckListenerContainerFactory() {
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);
    factory.setConcurrency(3);
    return factory;
}

// 3. Batch consumer (batch-consumer-group)
@Bean("batchListenerContainerFactory")
public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> batchListenerContainerFactory() {
    factory.setBatchListener(true);
    factory.getContainerProperties().setAckMode(AckMode.BATCH);
    factory.setConcurrency(2);           // 2 batch workers
    return factory;
}
```

### Base Consumer Properties

```java
private Map<String, Object> baseConsumerConfigs(String groupId, boolean enableAutoCommit) {
    configs.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configs.put(GROUP_ID_CONFIG, groupId);
    configs.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configs.put(VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class); // ← safety wrapper
    configs.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
    configs.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
    configs.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DemoTransaction.class);
    configs.put(AUTO_OFFSET_RESET_CONFIG, "earliest");  // replay from beginning
    configs.put(ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
    configs.put(SESSION_TIMEOUT_MS_CONFIG, 10000);      // declare dead after 10s
    configs.put(HEARTBEAT_INTERVAL_MS_CONFIG, 3000);    // heartbeat every 3s
    return configs;
}
```

### `ErrorHandlingDeserializer` — Why It Matters

Without this wrapper, a single malformed JSON message crashes the entire consumer thread and halts partition processing. With the wrapper:

```
Malformed JSON arrives
     │
     ▼
ErrorHandlingDeserializer catches the error
     │  wraps it as a DeserializationException in the record header
     ▼
Listener receives record with null value + exception in headers
     │
     ▼
Consumer can route to DLQ instead of crashing
```

### `auto.offset.reset=earliest`

When a new consumer group starts and has no committed offsets, `earliest` reads from the very beginning of the topic. This is ideal for learning projects because you can replay all messages. In production, `latest` is often preferred for real-time processing.

---

## 9. Producer Patterns

### Overview Comparison

| Producer | Pattern | Throughput | Reliability | When to Use |
|---|---|---|---|---|
| `SimpleProducer` | Fire-and-forget | ~100k msg/s | Lowest | Metrics, logs, telemetry |
| `ReliableProducer` | Synchronous | ~5k msg/s | High | Critical, low-volume transactions |
| `AsyncProducer` | Async + callback | ~50k msg/s | High | High-throughput with visibility |
| `TransactionalProducer` | Atomic batch | ~60% of sync | Highest | Multi-message atomic operations |
| `PartitionedProducer` | Key-based routing | ~50k msg/s | High | Per-entity ordering |

---

### Pattern 1: Fire-and-Forget (`SimpleProducer.java`)

```java
public void send(DemoTransaction transaction) {
    try {
        kafkaTemplate.send(TOPIC, transaction);  // returns CompletableFuture, not awaited
        log.info("Sending message: {}", transaction.getMessageId());
    } catch (SerializationException ex) {
        log.error("Serialization failed for messageId={}", transaction.getMessageId(), ex);
    } catch (RuntimeException ex) {
        log.error("Unable to dispatch messageId={}", transaction.getMessageId(), ex);
    }
}
```

**How it works**: `kafkaTemplate.send()` returns a `CompletableFuture` that is never `.get()` called. The producer buffer receives the record and flushes it to the broker asynchronously. The calling thread continues immediately.

**Failure scenario**: If the broker is down after the local buffer flushes, the producer-level retry exhausts and the record is dropped. The caller has already moved on and never knows.

**Best for**: Click-stream events, application metrics, audit logs where occasional loss is acceptable.

---

### Pattern 2: Synchronous Send (`ReliableProducer.java`)

```java
public SendResult<String, DemoTransaction> sendWithConfirmation(DemoTransaction transaction)
        throws ExecutionException, InterruptedException {
    try {
        SendResult<String, DemoTransaction> result = kafkaTemplate
                .send(TOPIC, transaction)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);  // BLOCKS here

        log.info("Message sent to partition {} at offset {}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        return result;

    } catch (TimeoutException ex) {
        log.error("Timed out while sending messageId={}", transaction.getMessageId(), ex);
        throw new ExecutionException(ex);
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw ex;
    }
}
```

**How it works**: `.get(10, TimeUnit.SECONDS)` blocks the calling thread until the broker acknowledges or the timeout expires. On success, you receive the exact `partition` and `offset` where the record was written.

**Failure scenario**: If the broker is unreachable, `.get()` throws after the timeout. The caller gets a clear exception and can react (retry with backoff, reject the request, etc.).

**Best for**: Payment initiation, order placement, any operation where the HTTP caller needs to know if Kafka accepted the event.

---

### Pattern 3: Async with Callbacks (`AsyncProducer.java`)

```java
public CompletableFuture<SendResult<String, DemoTransaction>> sendAsync(DemoTransaction transaction) {
    CompletableFuture<SendResult<String, DemoTransaction>> future = kafkaTemplate.send(TOPIC, transaction);

    future.thenAccept(result -> log.info(
            "Async message {} sent to partition {} at offset {}",
            transaction.getMessageId(),
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset()
    )).exceptionally(ex -> {
        logAsyncFailure(transaction, ex);  // alert, metric, or compensating action
        return null;
    });

    return future;  // caller can chain more operations or ignore
}
```

**How it works**: The calling thread returns the `CompletableFuture` immediately. Success and failure callbacks run on the Kafka client's internal thread pool when the broker responds.

**Important**: Always attach a `.exceptionally()` handler. Without it, broker failures are silently swallowed — a common production bug.

**Best for**: High-throughput event publishing where you want non-blocking sends but still need failure visibility (metrics, alerting, compensation logic).

---

### Pattern 4: Transactional Send (`TransactionalProducer.java`)

```java
public boolean sendTransactional(List<DemoTransaction> transactions) {
    Boolean committed = transactionalKafkaTemplate.executeInTransaction(operations -> {
        for (DemoTransaction transaction : transactions) {
            String accountKey = transaction.getSourceId();

            // All events for ACC-001 go to the same partition → ordering guaranteed
            operations.send(TOPIC, accountKey, transaction)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        return Boolean.TRUE;
        // If any send throws → Spring rolls back the Kafka transaction
    });

    return Boolean.TRUE.equals(committed);
}
```

**How it works**: `executeInTransaction` wraps all sends in a Kafka transaction:
1. `beginTransaction()` — broker assigns a transaction ID
2. All `send()` calls are part of the transaction
3. If the lambda exits normally → `commitTransaction()`
4. If the lambda throws → `abortTransaction()`

**Consumer side requirement**: Consumers must use `isolation.level=read_committed` to not see uncommitted records from aborted transactions.

**Throughput impact**: Transactions add ~20-30% overhead due to coordinator round trips. Use them only when atomicity is genuinely required.

---

### Pattern 5: Key-Based Partitioning (`PartitionedProducer.java`)

```java
public CompletableFuture<SendResult<String, DemoTransaction>> sendWithKey(
        String key, DemoTransaction transaction) {

    // Kafka formula: partition = hash(key) % partitionCount
    // Same key → always same partition → ordering guaranteed for that key
    CompletableFuture<SendResult<String, DemoTransaction>> future =
            kafkaTemplate.send(TOPIC, key, transaction);

    future.thenAccept(result -> log.info(
            "Message with key {} sent to partition {}",
            key,
            result.getRecordMetadata().partition()
    ));

    return future;
}
```

**How it works**: Kafka's `DefaultPartitioner` computes `MurmurHash2(key) % numPartitions`. The same key always maps to the same partition, which guarantees temporal ordering for that key.

**Hot partition problem**: If one key (e.g., `"ACC-001"`) generates 90% of traffic, one partition gets overloaded. Use composite keys or random salting when load is uneven.

```java
// Good: user-level ordering
kafkaTemplate.send(topic, userId, event);  // all events for user X → partition N

// Bad: global timestamp as key
kafkaTemplate.send(topic, Instant.now().toString(), event);  // poor distribution
```

---

## 10. Consumer Patterns

### Overview Comparison

| Consumer | Group ID | Ack Mode | Concurrency | Best For |
|---|---|---|---|---|
| `SimpleConsumer` | `simple-consumer-group` | Auto | 3 | Simple, non-critical processing |
| `ManualAckConsumer` | `manual-ack-group` | MANUAL | 3 | Critical data, at-least-once |
| `BatchConsumer` | `batch-consumer-group` | BATCH | 2 | Bulk inserts, analytics |
| `ErrorHandlingConsumer` | `error-handling-group` | MANUAL | 3 | Complex retry + DLQ routing |
| `GroupedConsumer` | `grouped-consumer-1` | Auto | 3 | Consumer group demonstration |

---

### Pattern 1: Simple Consumer (`SimpleConsumer.java`)

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = GROUP_ID,
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(ConsumerRecord<String, DemoTransaction> record) {
    DemoTransaction message = record.value();
    // Process the message
    // Auto-commit: offset advances automatically in background
}
```

**Offset commitment**: The container periodically calls `commitSync()` based on `auto.commit.interval.ms`. The offset advances whether or not processing succeeded — which is why auto-commit is called "at-most-once" in failure scenarios.

**When auto-commit is acceptable**:
- Metrics collection (losing a few metrics is fine)
- Log aggregation (idempotent storage)
- Any scenario where reprocessing is more expensive than losing a record

---

### Pattern 2: Manual Acknowledgment (`ManualAckConsumer.java`)

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = GROUP_ID,
    containerFactory = "manualAckListenerContainerFactory"
)
public void consume(ConsumerRecord<String, DemoTransaction> record, Acknowledgment ack) {
    try {
        processBusinessLogic(record.value());

        // ← Only after success: advance the committed offset
        ack.acknowledge();
        retryCounters.remove(retryKey(record));

    } catch (Exception ex) {
        int attempts = retryCounters.computeIfAbsent(retryKey, k -> new AtomicInteger())
                                    .incrementAndGet();

        if (attempts >= MAX_RETRY_ATTEMPTS) {
            sendToDlq(record, ex);
            ack.acknowledge();  // ← Move past the poison pill
        }
        // If below max retries: do NOT acknowledge → Kafka redelivers
    }
}
```

**At-least-once delivery**: Not calling `ack.acknowledge()` means the committed offset stays behind. On consumer restart, the record is redelivered. This guarantees no loss but requires **idempotent processing**.

**Retry counter pattern**: Since Kafka doesn't have a built-in per-record retry counter, this consumer maintains an in-memory `ConcurrentHashMap<retryKey, AtomicInteger>`. The key is `"topic-partition-offset"`.

**DLQ promotion**: After 3 failed attempts, the record is forwarded to `demo-dlq` and then acknowledged. This prevents one bad record from blocking the entire partition forever.

---

### Pattern 3: Batch Consumer (`BatchConsumer.java`)

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = GROUP_ID,
    containerFactory = "batchListenerContainerFactory"
)
public void consumeBatch(List<ConsumerRecord<String, DemoTransaction>> records) {
    log.info("Received batch of {} messages", records.size());

    List<DemoTransaction> messages = records.stream()
            .map(r -> requirePayload(r))
            .collect(Collectors.toList());

    // Bulk processing: one DB insert for N records vs N individual inserts
    processInSubBatches(messages, SUB_BATCH_SIZE);
    // AckMode.BATCH commits all offsets after this method returns successfully
}
```

**Sub-batch processing**: The consumer processes 100 records (max poll) in sub-batches of 25. This balances DB write efficiency against memory pressure and per-batch failure blast radius.

**Failure semantics**: If `processInSubBatches` throws, `AckMode.BATCH` does not commit. Kafka redelivers the entire batch of 100. This is "all-or-nothing" batch semantics — simple but means duplicated work when one record in the batch is bad.

**Performance**: Batch consumers are ideal for:
- Bulk database inserts (JDBC batch API)
- Aggregate computation (sum, count per window)
- Data warehouse ingestion

**Configuration** (`KafkaConsumerConfig.java`):
```java
configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
```

---

### Pattern 4: Error Handling with DLQ (`ErrorHandlingConsumer.java`)

This is the most production-relevant pattern. It distinguishes between retryable and non-retryable errors:

```java
private void processMessage(DemoTransaction message) {
    String description = message.getDescription().toUpperCase();

    // Permanent failure → goes to DLQ immediately
    if (description.contains("INVALID") || message.getAmount().signum() <= 0) {
        throw new NonRetryableProcessingException("Validation failed");
    }

    // Transient failure → retry with exponential backoff
    if (description.contains("RETRY") || description.contains("TIMEOUT")) {
        throw new RetryableProcessingException("Simulated downstream timeout");
    }
}
```

**Error routing logic**:

```
Record received
     │
     ├─ Success → ack.acknowledge()
     │
     ├─ RetryableException (e.g., DB timeout)
     │     ├─ attempts < MAX_RETRY → sleep(exponentialBackoff) → don't acknowledge → redelivery
     │     └─ attempts >= MAX_RETRY → sendToDlq() → ack.acknowledge()
     │
     ├─ NonRetryableException (e.g., validation failure)
     │     └─ sendToDlq() → ack.acknowledge()  (immediately)
     │
     └─ DeserializationException
           └─ sendToDlq() → ack.acknowledge()  (immediately, cannot retry)
```

**Exponential backoff**:
```java
private long exponentialBackoff(int attempt) {
    return BASE_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
    // attempt 1 → 250ms, attempt 2 → 500ms, attempt 3 → 1000ms
}
```

**Why the DLQ pattern is essential**: Without a DLQ, one malformed or permanently-failing message blocks an entire partition. All other healthy messages behind it are never processed. The DLQ isolates the poison pill so the partition keeps moving.

---

### Pattern 5: Consumer Groups (`GroupedConsumer.java`)

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = "grouped-consumer-1",           // ← different group
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(ConsumerRecord<String, DemoTransaction> record) {
    // Processes same messages as SimpleConsumer independently
    // No shared offset tracking with other groups
}
```

**Consumer group mechanics**:
```
Topic: demo-messages (3 partitions)

Group "simple-consumer-group":           Group "grouped-consumer-1":
  Consumer-A → Partition 0                 Consumer-X → Partition 0
  Consumer-B → Partition 1                 Consumer-Y → Partition 1, 2
  Consumer-C → Partition 2
```

Both groups receive **all messages** independently. Groups don't interfere with each other's offsets.

**Rebalancing**: Run the app in two terminals. One instance gets partitions {0,1,2}. Stop one terminal — the remaining instance takes over all partitions within the session timeout (10 seconds in this project).

---

## 11. Service Layer Design

`KafkaService.java` is the bridge between the REST layer and the producer layer. It demonstrates several important design principles:

### 1. Abstraction
Controllers call `kafkaService.sendSimple(tx)` — they don't know which producer implementation is used.

### 2. Separation of Concerns
Validation is the service's job. Producers just send bytes.

```java
public void sendSimple(DemoTransaction transaction) {
    // Validate BEFORE sending (fail-fast principle)
    validationService.validateTransaction(transaction);

    // Delegate sending to the appropriate producer
    simpleProducer.send(transaction);
}
```

### 3. Centralized Kafka Logic
Topic names, producer selection, retry strategy — all in one place. Changing from fire-and-forget to reliable send means changing one line.

### ACID in Distributed Systems

The service documentation explains why traditional ACID doesn't automatically extend to Kafka:

| Property | Traditional DB | Kafka |
|---|---|---|
| Atomicity | Single transaction | Transactional producer (limited) |
| Consistency | DB constraints | Application-level validation |
| Isolation | Transaction isolation levels | Consumer group offset tracking |
| Durability | Write-ahead log | `acks=all` + replication |

**Practical patterns for distributed consistency**:
- **Outbox Pattern**: Write to DB + outbox table in one DB transaction; CDC relay publishes to Kafka
- **Saga Pattern**: Sequence of local transactions with compensating events for rollback
- **Idempotency Keys**: `messageId` (UUID) as the deduplication key across retries

---

## 12. Message Validation

`MessageValidationService.java` implements the **fail-fast principle**: reject bad data before it enters Kafka, where remediation is expensive.

### Validation Pipeline

```java
public void validateTransaction(DemoTransaction transaction) {
    if (transaction == null)
        throw new IllegalArgumentException("Transaction must not be null");

    if (isBlank(transaction.getSourceId()))
        throw new IllegalArgumentException("sourceId must not be blank");

    if (isBlank(transaction.getTargetId()))
        throw new IllegalArgumentException("targetId must not be blank");

    if (transaction.getAmount() == null || transaction.getAmount().compareTo(ZERO) <= 0)
        throw new IllegalArgumentException("amount must be positive");

    if (!isValidCurrency(transaction.getCurrency()))
        throw new IllegalArgumentException("currency " + transaction.getCurrency()
                + " not in " + SUPPORTED_CURRENCIES);  // USD, EUR, GBP, JPY, CHF

    if (transaction.getMessageId() == null)
        throw new IllegalArgumentException("messageId must be present");
}
```

### Two Validation Layers

1. **Bean Validation** (`@NotBlank`, `@Positive`, `@Pattern`) — structural contract on the model
2. **Service validation** (currency whitelist, business rules) — domain logic in the service

Both layers complement each other. Bean Validation catches structural errors early (at the HTTP request boundary). Service validation enforces business invariants that don't fit cleanly on annotations.

### Production Extension Points (marked as TODO in code)

```java
// Balance check — currently a mock
public void validateBalance(String accountId, BigDecimal amount) {
    // TODO: Call Account Service via REST/gRPC
    //       Wrap with circuit breaker (Resilience4j)
    //       Cache recent balances with short TTL
}

// Duplicate detection — currently a mock
public void checkDuplicateTransaction(String transactionId) {
    // TODO: Redis SETNX — atomic, fast, TTL-based expiry
    //   OR: Database unique constraint on transaction_id
    //   OR: Outbox pattern for exactly-once + DB write atomicity
}
```

---

## 13. REST API Reference

The application runs on port `8090`. Base URL: `http://localhost:8090/api/demo`

### Endpoints

| Method | Path | Pattern | HTTP Status |
|---|---|---|---|
| POST | `/send-simple` | Fire-and-forget | 201 Created |
| POST | `/send-reliable` | Synchronous | 201 Created |
| POST | `/send-async` | Async callback | 202 Accepted |
| POST | `/send-batch` | Transactional | 201 Created |
| POST | `/send-with-key` | Partitioned | 201 Created |
| GET | `/transaction/{id}/status` | Status query | 200 OK |
| GET | `/health` | Kafka connectivity | 200 / 503 |
| POST | `/generate-test-data` | Bulk test data | 201 Created |

### Request Payload Structure

```json
{
  "sourceId": "ACC001",
  "targetId": "ACC002",
  "amount": 100.50,
  "currency": "USD",
  "status": "CREATED",
  "description": "Optional description"
}
```

**Supported currencies**: `USD`, `EUR`, `GBP`, `JPY`, `CHF`

### Working curl Examples

```bash
# 1. Fire-and-forget (fastest, no confirmation)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.50,"currency":"USD"}'
# Response: {"messageId":"...","status":"SENT","pattern":"fire-and-forget"}

# 2. Synchronous with broker confirmation
curl -X POST http://localhost:8090/api/demo/send-reliable \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC003","targetId":"ACC004","amount":250.00,"currency":"EUR"}'
# Response: {"messageId":"...","status":"CONFIRMED","partition":1,"offset":42}

# 3. Async (returns 202 before Kafka confirms)
curl -X POST http://localhost:8090/api/demo/send-async \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":75.00,"currency":"GBP"}'
# Response: {"messageId":"...","status":"ACCEPTED","pattern":"async-with-callback"}

# 4. Transactional batch (atomic — all succeed or none)
curl -X POST http://localhost:8090/api/demo/send-batch \
  -H "Content-Type: application/json" \
  -d '[
    {"sourceId":"ACC005","targetId":"ACC006","amount":50.00,"currency":"USD"},
    {"sourceId":"ACC007","targetId":"ACC008","amount":75.00,"currency":"GBP"}
  ]'

# 5. Key-based partitioning (all USER001 events → same partition)
curl -X POST "http://localhost:8090/api/demo/send-with-key?key=USER001" \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"USER001","targetId":"ACC009","amount":500.00,"currency":"USD"}'

# 6. Generate bulk test data (triggers all consumer patterns)
curl -X POST "http://localhost:8090/api/demo/generate-test-data?count=20"

# 7. Health check
curl http://localhost:8090/api/demo/health

# 8. Trigger retry scenario (description contains "RETRY")
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD","description":"RETRY this message"}'

# 9. Trigger DLQ scenario (description contains "INVALID")
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD","description":"INVALID message"}'
```

### HTTP Status Code Rationale

| Code | Meaning | When Used |
|---|---|---|
| 201 Created | A Kafka event record was created | send-simple, send-reliable, send-batch |
| 202 Accepted | Message accepted, async delivery pending | send-async |
| 200 OK | Read-only query succeeded | health, status |
| 400 Bad Request | Bean Validation or business rule failure | all endpoints |
| 500 Internal Server Error | Kafka/broker failure | send-reliable, send-batch |
| 503 Service Unavailable | Health check — Kafka unreachable | /health |

---

## 14. Testing Strategy

### Test Structure

```
src/test/java/.../kafka/
├── producer/ProducerPatternsTest.java       ← Tests all 5 producer patterns
├── consumer/ConsumerPatternsTest.java       ← Tests all 5 consumer patterns
├── service/KafkaServiceTest.java            ← Service orchestration tests
├── service/MessageValidationServiceTest.java ← Validation rule tests
└── KafkaHandsOnGuideApplicationTests.java   ← Spring Boot context test
```

### Testing Approach: Unit Tests with Mocks

Instead of running a real Kafka broker in tests, the project uses Mockito to mock `KafkaTemplate`:

```java
@Test
void simpleProducerShouldDispatchFireAndForgetSend() {
    KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
    when(kafkaTemplate.send(anyString(), any(DemoTransaction.class)))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    SimpleProducer producer = new SimpleProducer();
    ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);

    assertDoesNotThrow(() -> producer.send(sampleTransaction()));
    verify(kafkaTemplate).send(eq("demo-messages"), eq(transaction));
}
```

### Key Consumer Test Scenarios

```java
// Test: normal processing succeeds
void simpleConsumerShouldProcessValidRecord() {
    assertDoesNotThrow(() -> simpleConsumer.consume(record(validTransaction("OK"), 0L)));
}

// Test: batch fails when one record has FAIL_BATCH in description
void batchConsumerShouldFailWholeBatchWhenOneRecordFails() {
    List<ConsumerRecord<...>> records = List.of(
        record(validTransaction("BATCH-1"), 0L),
        record(validTransaction("FAIL_BATCH"), 1L)  // ← triggers failure
    );
    assertThrows(IllegalStateException.class, () -> batchConsumer.consumeBatch(records));
}

// Test: manual-ack consumer acknowledges after success
void manualAckConsumerShouldAcknowledgeOnSuccess() {
    manualAckConsumer.consume(record(validTransaction("OK"), 0L), acknowledgment);
    verify(acknowledgment).acknowledge();
}

// Test: manual-ack consumer does NOT acknowledge on retryable failure
void manualAckConsumerShouldNotAcknowledgeOnFirstFailure() {
    manualAckConsumer.consume(record(validTransaction("FAIL_MANUAL"), 0L), acknowledgment);
    verify(acknowledgment, never()).acknowledge();
}
```

### Testing Simulation Keywords

The consumers check the `description` field to simulate different failure scenarios:

| Description Contains | Effect | Consumer |
|---|---|---|
| `FAIL_MANUAL` | Throws `IllegalStateException` | `ManualAckConsumer` |
| `FAIL_BATCH` | Throws `IllegalStateException` | `BatchConsumer` |
| `INVALID` | Throws `NonRetryableProcessingException` → DLQ | `ErrorHandlingConsumer` |
| `RETRY` or `TIMEOUT` | Throws `RetryableProcessingException` → retry | `ErrorHandlingConsumer` |

---

## 15. Key Kafka Concepts Demonstrated

### Delivery Semantics

| Semantic | Producer Side | Consumer Side | Project Example |
|---|---|---|---|
| At-most-once | Fire-and-forget | Auto-commit before processing | `SimpleProducer` + `SimpleConsumer` |
| At-least-once | `acks=all` + retries | Manual ack after processing | `ReliableProducer` + `ManualAckConsumer` |
| Exactly-once | Transactional producer | `isolation.level=read_committed` | `TransactionalProducer` |

### Idempotency in This Project

1. **Producer idempotency**: `enable.idempotence=true` — Kafka deduplicates retried sends
2. **Message idempotency key**: Every message carries a UUID `messageId` — consumers can check for duplicates
3. **Service layer**: `MessageValidationService.checkDuplicateTransaction()` stub shows where Redis/DB deduplication would go

### Dead Letter Queue Pattern

```
Normal flow:
  Topic → Consumer → Business Logic → Success → Acknowledge

DLQ flow:
  Topic → Consumer → Business Logic → Failure (attempt 1, 2, 3)
                                     → Send to demo-dlq → Acknowledge original
  demo-dlq → DLQ Consumer → Manual investigation, replay, or alert
```

**Why DLQ is essential**:
- Prevents one bad message from blocking an entire partition
- Preserves failed events for investigation and controlled replay
- Enables operational alerting on failure rates

### Consumer Group Rebalancing

Rebalancing occurs when:
1. A new consumer joins the group
2. A consumer leaves (normal shutdown)
3. A consumer crashes (session timeout exceeded)
4. A new partition is added to the topic

During rebalancing: **all consumption pauses** in the group. Rebalance duration is affected by `session.timeout.ms` (10s in this project) and `max.poll.interval.ms`.

### Offset Management

```
Partition 0 offsets:
  0   1   2   3   4   5   6   7   8
 [A] [B] [C] [D] [E] [F] [G] [H] [I]
                  ↑
         committed offset = 4
         (D has been processed and committed)
         (E, F, G... will be redelivered on restart)
```

**Auto-commit** (SimpleConsumer): Kafka advances offset every `auto.commit.interval.ms` regardless of processing result.

**Manual commit** (ManualAckConsumer): Only `ack.acknowledge()` advances the offset. A crash between receiving and acknowledging redelivers the record.

---

## 16. Hands-On Exercises

### Exercise 1: Observe Partition Assignment
```bash
# Start the app, then send messages with different keys
curl -X POST "http://localhost:8090/api/demo/send-with-key?key=ACC001" \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":10.00,"currency":"USD"}'

curl -X POST "http://localhost:8090/api/demo/send-with-key?key=ACC001" \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":20.00,"currency":"USD"}'

# Open Kafka UI → demo-messages → Messages
# Observe: both messages with key=ACC001 landed on the same partition
```

### Exercise 2: Trigger the DLQ
```bash
# Send a message that ErrorHandlingConsumer will permanently fail
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD","description":"INVALID payment"}'

# Watch the logs: you'll see the consumer route it to demo-dlq
# Open Kafka UI → demo-dlq → Messages to confirm
```

### Exercise 3: Watch Retry Backoff
```bash
# Send a message that triggers retryable failure
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD","description":"RETRY please"}'

# Watch logs for:
# attempt=1 backoffMs=250
# attempt=2 backoffMs=500
# attempt=3 backoffMs=1000 → then sent to DLQ
```

### Exercise 4: Consumer Group Balancing
```bash
# Terminal 1: start the app (takes partitions 0, 1, 2)
mvn spring-boot:run

# Terminal 2: start a second instance on a different port
mvn spring-boot:run -Dserver.port=8091

# Send test data
curl -X POST "http://localhost:8090/api/demo/generate-test-data?count=30"

# Observe in logs: messages split between the two instances
# Then stop Terminal 2 and watch rebalancing logs
```

### Exercise 5: Compare Transactional vs Non-Transactional
```bash
# Send a batch transactionally (all or nothing)
curl -X POST http://localhost:8090/api/demo/send-batch \
  -H "Content-Type: application/json" \
  -d '[
    {"sourceId":"ACC-A","targetId":"ACC-B","amount":100.00,"currency":"USD"},
    {"sourceId":"ACC-C","targetId":"ACC-D","amount":200.00,"currency":"EUR"}
  ]'

# In Kafka UI: look for the __transaction_state topic entries
# Notice both messages have the same transaction boundary
```

---

## 17. Further Reading

### Internal Project Documentation

| File | Content |
|---|---|
| [01-kafka-basics.md](01-kafka-basics.md) | Kafka fundamentals: topics, partitions, brokers, offsets |
| [02-configuration-explained.md](02-configuration-explained.md) | Deep dive into every configuration property |
| [03-producer-patterns.md](03-producer-patterns.md) | Producer pattern comparison and code walkthrough |
| [04-consumer-patterns.md](04-consumer-patterns.md) | Consumer pattern comparison with failure scenarios |
| [05-common-pitfalls.md](05-common-pitfalls.md) | Most frequent Kafka mistakes and how to avoid them |
| [06-production-checklist.md](06-production-checklist.md) | Production readiness checklist |

### Spring Kafka Documentation

- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [KafkaTemplate API](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/core/KafkaTemplate.html)
- [ConcurrentKafkaListenerContainerFactory](https://docs.spring.io/spring-kafka/docs/current/api/org/springframework/kafka/config/ConcurrentKafkaListenerContainerFactory.html)

### Apache Kafka Official Docs

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kafka Design — Log](https://kafka.apache.org/documentation/#design_filesystem)
- [Producer Configs](https://kafka.apache.org/documentation/#producerconfigs)
- [Consumer Configs](https://kafka.apache.org/documentation/#consumerconfigs)

### Related Concepts to Explore Next

1. **Schema Registry + Avro** — Replace JSON with binary Avro for schema evolution and compact wire format
2. **Kafka Streams** — In-process stream processing on top of Kafka topics
3. **KSQL / ksqlDB** — SQL-like stream processing
4. **Kafka Connect** — Connectors for databases, file systems, cloud services
5. **Outbox Pattern** — Transactional guarantee between DB write and Kafka publish
6. **Saga Pattern** — Long-running distributed transactions via compensating events
7. **Consumer Lag Monitoring** — Prometheus + Grafana dashboards for operational health

---

*Generated from source analysis of `kafka-hands-on-guide` project — all code examples are taken directly from the implementation files.*

