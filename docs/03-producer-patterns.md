# Producer Patterns

A detailed guide to all five Kafka producer implementations in this project, with actual source code, performance characteristics, failure scenarios, and practical test commands.

## Table of Contents

- [Overview](#overview)
- [1. SimpleProducer — Fire-and-Forget](#1-simpleproducer--fire-and-forget)
- [2. ReliableProducer — Synchronous Send](#2-reliableproducer--synchronous-send)
- [3. AsyncProducer — Non-Blocking with Callbacks](#3-asyncproducer--non-blocking-with-callbacks)
- [4. TransactionalProducer — Exactly-Once Semantics](#4-transactionalproducer--exactly-once-semantics)
- [5. PartitionedProducer — Key-Based Routing](#5-partitionedproducer--key-based-routing)
- [Choosing the Right Pattern](#choosing-the-right-pattern)
- [Producer Configuration Reference](#producer-configuration-reference)

---

## Overview

| Producer | File | HTTP Endpoint | Pattern | Approx. Throughput | Guarantee |
|---|---|---|---|---|---|
| `SimpleProducer` | `producer/SimpleProducer.java` | `POST /send-simple` | Fire-and-forget | ~100k msg/s | None |
| `ReliableProducer` | `producer/ReliableProducer.java` | `POST /send-reliable` | Synchronous | ~5k msg/s | At-least-once |
| `AsyncProducer` | `producer/AsyncProducer.java` | `POST /send-async` | Async callback | ~50k msg/s | At-least-once |
| `TransactionalProducer` | `producer/TransactionalProducer.java` | `POST /send-batch` | Transactional | ~50–70% of sync | Exactly-once |
| `PartitionedProducer` | `producer/PartitionedProducer.java` | `POST /send-with-key` | Key-based | ~50k msg/s | At-least-once + Ordered |

All producers operate on the `demo-messages` topic (3 partitions) and send `DemoTransaction` payloads serialized as JSON.

---

## 1. SimpleProducer — Fire-and-Forget

**File**: `src/main/java/.../producer/SimpleProducer.java`

### What It Does

Enqueues the record into the producer's internal buffer and **returns immediately**. No callback is attached, and no future is awaited. If the broker later fails to accept the record, the producer retries up to 3 times (configured globally) and then silently drops it.

### Source Code

```java
@Component
@Slf4j
public class SimpleProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    public void send(DemoTransaction transaction) {
        try {
            log.info("Sending message: {}", transaction.getMessageId());
            kafkaTemplate.send(TOPIC, transaction);
            // The CompletableFuture returned by send() is intentionally ignored.
            // Producer-level retries (retries=3) are the only safety net.
        } catch (SerializationException ex) {
            log.error("Serialization failed for fire-and-forget messageId={}",
                    transaction.getMessageId(), ex);
        } catch (RuntimeException ex) {
            log.error("Unable to dispatch fire-and-forget messageId={} to topic={}",
                    transaction.getMessageId(), TOPIC, ex);
        }
    }
}
```

### How It Works Internally

```
kafkaTemplate.send(TOPIC, transaction)
     │
     ▼
Serialized to JSON bytes  ←  JsonSerializer
     │
     ▼
Producer internal buffer (RecordAccumulator)
     │
     ▼  (flushed after linger.ms=10 or batch is full)
Network I/O thread sends to broker
     │
     ├─ Success: Kafka internally increments sequence number (idempotence)
     └─ Failure: Retried up to 3 times, then silently dropped
```

### Failure Scenarios

| Failure | Outcome |
|---|---|
| Broker temporarily unreachable | Producer retries 3 times, then drops silently |
| Serialization fails | `SerializationException` logged; caller is NOT notified |
| Broker permanently down | Message lost after retry exhaustion |
| Application crash after `.send()` | Message in buffer is lost (not persisted to disk) |

### When to Use

- ✅ Application metrics, click-stream events
- ✅ Audit logs where occasional loss is acceptable
- ✅ Telemetry, monitoring data
- ✅ Scenarios where raw throughput >> delivery guarantee
- ❌ Financial transactions
- ❌ Any workflow requiring delivery confirmation

### Test It

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.50,"currency":"USD"}'

# Expected response (201 Created):
# {"messageId":"...","status":"SENT","pattern":"fire-and-forget"}
```

---

## 2. ReliableProducer — Synchronous Send

**File**: `src/main/java/.../producer/ReliableProducer.java`

### What It Does

Calls `.get()` on the `CompletableFuture` returned by `kafkaTemplate.send()`, **blocking the calling thread** until the broker acknowledges the write or the 10-second timeout expires. On success, returns the exact partition and offset from the broker's response.

### Source Code

```java
@Component
@Slf4j
public class ReliableProducer {

    private static final String TOPIC = "demo-messages";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    public SendResult<String, DemoTransaction> sendWithConfirmation(DemoTransaction transaction)
            throws ExecutionException, InterruptedException {
        try {
            // .get() blocks until broker ACK or timeout
            SendResult<String, DemoTransaction> result = kafkaTemplate
                    .send(TOPIC, transaction)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Message sent to partition {} at offset {}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return result;

        } catch (TimeoutException ex) {
            log.error("Timed out while sending messageId={}", transaction.getMessageId(), ex);
            throw new ExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();   // Always restore interrupt flag!
            throw ex;
        } catch (ExecutionException ex) {
            log.error("Kafka rejected messageId={}", transaction.getMessageId(), ex);
            throw ex;
        }
    }
}
```

### Blocking Sequence Diagram

```
HTTP Request Thread                   Kafka Broker
        │                                   │
        │── kafkaTemplate.send(tx) ─────────►│
        │                                   │
        │   ◄─── blocked waiting ──────────► │
        │                                   │
        │◄── ACK (partition=1, offset=42) ──│
        │                                   │
        │── returns SendResult ─────────────   (HTTP response)
```

The HTTP request thread is blocked for the entire network round-trip duration (typically 10–50ms on a local network).

### What You Get Back

```java
SendResult<String, DemoTransaction> result = producer.sendWithConfirmation(tx);
int partition  = result.getRecordMetadata().partition();   // e.g., 1
long offset    = result.getRecordMetadata().offset();      // e.g., 42
long timestamp = result.getRecordMetadata().timestamp();   // broker append time
```

Use partition + offset as an idempotency reference: if the caller retries, they can check whether that exact offset already exists.

### Failure Scenarios

| Failure | Exception Thrown | Caller Impact |
|---|---|---|
| Broker down | `ExecutionException` wrapping `TimeoutException` | Immediate 500 response |
| Serialization fails | `SerializationException` | Immediate 500 response |
| Leader election in progress | `ExecutionException` | Retried at producer level, then exception |
| Thread interrupted | `InterruptedException` | Thread interrupt flag restored |

### When to Use

- ✅ Payment initiation, order placement
- ✅ Low-volume, high-importance events
- ✅ When the HTTP caller must know if Kafka accepted the event
- ✅ Audit-critical event creation
- ❌ High-throughput endpoints (blocks request thread per message)
- ❌ Background event publishing at scale

### Test It

```bash
curl -X POST http://localhost:8090/api/demo/send-reliable \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC003","targetId":"ACC004","amount":1000.00,"currency":"EUR"}'

# Expected response (201 Created):
# {"messageId":"...","status":"CONFIRMED","partition":1,"offset":42}
```

---

## 3. AsyncProducer — Non-Blocking with Callbacks

**File**: `src/main/java/.../producer/AsyncProducer.java`

### What It Does

Returns a `CompletableFuture` immediately. Success and failure handling happens in **callback methods** on the Kafka client's internal thread pool. The calling thread is never blocked.

### Source Code

```java
@Component
@Slf4j
public class AsyncProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    public CompletableFuture<SendResult<String, DemoTransaction>> sendAsync(
            DemoTransaction transaction) {
        try {
            CompletableFuture<SendResult<String, DemoTransaction>> future =
                    kafkaTemplate.send(TOPIC, transaction);

            // Success callback — runs on Kafka client thread
            future.thenAccept(result -> log.info(
                    "Async message {} sent to partition {} at offset {}",
                    transaction.getMessageId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            // Failure callback — ALWAYS attach; unhandled failures are invisible
            )).exceptionally(ex -> {
                logAsyncFailure(transaction, ex);
                return null;
            });

            return future;  // caller can chain further operations or discard

        } catch (SerializationException ex) {
            log.error("Serialization failed for async messageId={}", transaction.getMessageId(), ex);
            return CompletableFuture.failedFuture(ex);
        } catch (RuntimeException ex) {
            logAsyncFailure(transaction, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
```

### Threading Model

```
HTTP Request Thread                  Kafka Client Thread Pool
        │                                       │
        │── kafkaTemplate.send(tx) ─────────────►│ (enqueued)
        │                                        │
        │◄── returns CompletableFuture immediately│
        │                                        │
        │── HTTP 202 Accepted → caller ──────────  (response sent)
                                                  │
                (later, asynchronously)           │
                                                  │── thenAccept() ──► success log
                                                  │── exceptionally() ► failure log
```

### The Critical Mistake: Ignored Futures

```java
// ❌ WRONG: failure is completely invisible
asyncProducer.sendAsync(tx);   // returned future discarded

// ✅ CORRECT: failures are observed and acted on
asyncProducer.sendAsync(tx)
    .exceptionally(ex -> {
        alertingService.alert("Kafka send failed: " + ex.getMessage());
        metricsService.increment("kafka.send.error");
        return null;
    });
```

### When to Use

- ✅ High-throughput event publishing (user activity, IoT sensor data)
- ✅ Scenarios where HTTP must stay responsive under load
- ✅ When you need failure visibility but can handle it asynchronously
- ✅ The best balance between SimpleProducer and ReliableProducer
- ❌ When the HTTP caller needs immediate Kafka confirmation
- ❌ When failure must block the caller synchronously

### Test It

```bash
curl -X POST http://localhost:8090/api/demo/send-async \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":75.00,"currency":"GBP"}'

# Expected response (202 Accepted — returned before Kafka confirms):
# {"messageId":"...","status":"ACCEPTED","pattern":"async-with-callback"}
```

---

## 4. TransactionalProducer — Exactly-Once Semantics

**File**: `src/main/java/.../producer/TransactionalProducer.java`

### What It Does

Wraps a list of `DemoTransaction` messages inside a **Kafka transaction**. Either all messages are committed atomically, or none are (the transaction is aborted). Uses a dedicated `KafkaTemplate` backed by a transactional producer factory.

### Source Code

```java
@Component
@Slf4j
public class TransactionalProducer {

    private static final String TOPIC = "demo-messages";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    @Autowired
    @Qualifier("transactionalKafkaTemplate")
    private KafkaTemplate<String, DemoTransaction> transactionalKafkaTemplate;

    public boolean sendTransactional(List<DemoTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return false;
        }

        try {
            Boolean committed = transactionalKafkaTemplate.executeInTransaction(operations -> {
                for (DemoTransaction transaction : transactions) {
                    String accountKey = transaction.getSourceId();

                    // accountKey routes to same partition → ordering + atomicity
                    operations.send(TOPIC, accountKey, transaction)
                            .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    // If any send() throws → Spring calls abortTransaction()
                }
                return Boolean.TRUE;
                // Normal exit → Spring calls commitTransaction()
            });

            log.info("Kafka transaction committed for {} messages", transactions.size());
            return Boolean.TRUE.equals(committed);

        } catch (Exception ex) {
            log.error("Kafka transaction rolled back for firstMessageId={}",
                    transactions.get(0).getMessageId(), ex);
            return false;
        }
    }
}
```

### Transaction Lifecycle

```
executeInTransaction(operations -> {
          │
    beginTransaction()
          │
    operations.send(msg1).get()   → broker buffers (seq=1)
    operations.send(msg2).get()   → broker buffers (seq=2)
    operations.send(msg3).get()   → broker buffers (seq=3)
          │
  (lambda exits normally)
          │
    commitTransaction()    → broker makes all 3 visible atomically
})

  OR  (if any send() throws):
    abortTransaction()     → broker discards all 3
```

### Consumer Side Requirement

To respect transaction boundaries, consumers must set `isolation.level=read_committed`:

```yaml
spring:
  kafka:
    consumer:
      properties:
        isolation.level: read_committed
```

Without this, consumers see records from **aborted** transactions (the default `read_uncommitted` mode).

### Performance Trade-offs

| Aspect | Non-Transactional | Transactional |
|---|---|---|
| Throughput | 100% | ~50–70% |
| Latency | Low | Higher (coordinator round-trip) |
| Failure atomicity | None | All-or-nothing |
| Consumer complexity | Simple | Requires `read_committed` |

### When to Use

- ✅ Multi-step financial operations (debit + credit as one atomic batch)
- ✅ Workflows where partial failure is worse than total failure
- ✅ Fan-out events that must all succeed or all roll back
- ❌ Single-message publishes (overhead not justified)
- ❌ High-throughput hot paths

### Test It

```bash
# Two-message atomic batch
curl -X POST http://localhost:8090/api/demo/send-batch \
  -H "Content-Type: application/json" \
  -d '[
    {"sourceId":"ACC005","targetId":"ACC006","amount":50.00,"currency":"USD"},
    {"sourceId":"ACC007","targetId":"ACC008","amount":75.00,"currency":"GBP"}
  ]'

# Expected (201 Created): both messages committed atomically
# {"batchSize":2,"status":"COMMITTED","pattern":"transactional-exactly-once"}
```

---

## 5. PartitionedProducer — Key-Based Routing

**File**: `src/main/java/.../producer/PartitionedProducer.java`

### What It Does

Sends a message with an explicit **key**. Kafka's `DefaultPartitioner` routes the record to a partition using `MurmurHash2(key) % numPartitions`. The same key always maps to the same partition, guaranteeing **temporal ordering** for all events with that key.

### Source Code

```java
@Component
@Slf4j
public class PartitionedProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    public CompletableFuture<SendResult<String, DemoTransaction>> sendWithKey(
            String key, DemoTransaction transaction) {
        try {
            // partition = MurmurHash2(key) % partitionCount
            // Same key → always same partition → ordering guaranteed per key
            CompletableFuture<SendResult<String, DemoTransaction>> future =
                    kafkaTemplate.send(TOPIC, key, transaction);

            future.thenAccept(result -> log.info(
                    "Message with key {} sent to partition {}",
                    key,
                    result.getRecordMetadata().partition()
            )).exceptionally(ex -> {
                logPartitionFailure(transaction, key, ex);
                return null;
            });

            return future;

        } catch (SerializationException ex) {
            log.error("Serialization failed for keyed messageId={} key={}",
                    transaction.getMessageId(), key, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }
}
```

### Partition Assignment Formula

```
For topic demo-messages with 3 partitions:

  key="ACC001"  → MurmurHash2("ACC001") % 3 = 0  → Partition 0 (always)
  key="ACC002"  → MurmurHash2("ACC002") % 3 = 1  → Partition 1 (always)
  key="ACC003"  → MurmurHash2("ACC003") % 3 = 2  → Partition 2 (always)
  key="ACC001"  → MurmurHash2("ACC001") % 3 = 0  → Partition 0 (same!)
```

### Ordering Guarantee

```
Producer sends for ACC001:            Partition 0 receives:
  send("ACC001", txn_A) ─────────►   [offset 0: txn_A]
  send("ACC001", txn_B) ─────────►   [offset 1: txn_B]
  send("ACC001", txn_C) ─────────►   [offset 2: txn_C]

Consumer reads Partition 0:
  txn_A → txn_B → txn_C    (strict order for ACC001, guaranteed)
```

### Hot Partition Problem

```java
// ❌ BAD: 90% of traffic uses the same key
kafkaTemplate.send(topic, "GLOBAL_ACCOUNT", tx);   // bottleneck!

// ✅ GOOD: fine-grained entity keys
kafkaTemplate.send(topic, "ACC-" + accountId, tx); // balanced distribution

// ✅ OPTION: salt hot keys if needed (loses strict single-partition order)
kafkaTemplate.send(topic, accountId + "-" + (txId % 3), tx);
```

### When to Use

- ✅ User activity streams (all events per user → same partition)
- ✅ Account transaction history (all txns for ACC-001 → same partition)
- ✅ Order state machine events (all events for ORDER-100 → same partition)
- ✅ Any entity-level ordering requirement
- ❌ Keys are highly skewed (hot partition risk)
- ❌ Global ordering across entities is needed

### Test It

```bash
# Send 3 messages with the same key — observe they all land on the same partition
for i in 1 2 3; do
  curl -s -X POST "http://localhost:8090/api/demo/send-with-key?key=ACC001" \
    -H "Content-Type: application/json" \
    -d "{\"sourceId\":\"ACC001\",\"targetId\":\"ACC002\",\"amount\":$((i*10)).00,\"currency\":\"USD\"}"
done

# Open Kafka UI → demo-messages → Messages
# Filter by key=ACC001: all 3 messages are on the same partition
```

---

## Choosing the Right Pattern

```
Is per-entity ordering required?
    YES → PartitionedProducer (key = entity ID, e.g. accountId)
    NO  → continue ↓

Must multiple messages commit atomically (all-or-nothing)?
    YES → TransactionalProducer
    NO  → continue ↓

Does the HTTP caller need immediate confirmation from Kafka?
    YES → ReliableProducer (blocking)
    NO  → continue ↓

Do you need failure visibility without blocking the caller?
    YES → AsyncProducer (with .exceptionally() callback attached)
    NO  → SimpleProducer (fire-and-forget, max throughput)
```

---

## Producer Configuration Reference

All producers share the base configuration from `KafkaProducerConfig.java`:

| Property | Value | Why |
|---|---|---|
| `bootstrap.servers` | `localhost:9093` | Externalized via `@Value` |
| `key.serializer` | `StringSerializer` | Human-readable keys for partition routing |
| `value.serializer` | `JsonSerializer` | JSON payloads for readability during learning |
| `acks` | `all` | Wait for all in-sync replicas (safest durability) |
| `retries` | `3` | Automatic recovery from transient failures |
| `enable.idempotence` | `true` | Prevent duplicate records from retries |
| `linger.ms` | `10` | Batch nearby records for network efficiency |
| `compression.type` | `snappy` | CPU-efficient bandwidth reduction |
| `max.in.flight.requests.per.connection` | `5` | Pipelining with idempotence safe up to 5 |

> **Production upgrade**: Increase `retries` to `Integer.MAX_VALUE` and control the total send window with `delivery.timeout.ms` instead of a fixed retry count.

---

**Related Files:**
- `KafkaProducerConfig.java` — Factory and template beans
- `KafkaService.java` — Service layer that delegates to these producers
- `DemoController.java` — REST endpoints that trigger each pattern
- `ProducerPatternsTest.java` — Unit tests for all patterns
