# Consumer Patterns

A detailed guide to all five Kafka consumer implementations in this project, with actual source code, offset management strategies, error handling, and hands-on test scenarios.

## Table of Contents

- [Overview](#overview)
- [1. SimpleConsumer — Auto-Commit](#1-simpleconsumer--auto-commit)
- [2. ManualAckConsumer — Explicit Acknowledgment](#2-manualackconsumer--explicit-acknowledgment)
- [3. BatchConsumer — Bulk Processing](#3-batchconsumer--bulk-processing)
- [4. ErrorHandlingConsumer — Retry and DLQ](#4-errorhandlingconsumer--retry-and-dlq)
- [5. GroupedConsumer — Consumer Group Mechanics](#5-groupedconsumer--consumer-group-mechanics)
- [Consumer Group Architecture](#consumer-group-architecture)
- [Offset Management Deep Dive](#offset-management-deep-dive)
- [Consumer Configuration Reference](#consumer-configuration-reference)

---

## Overview

| Consumer | File | Group ID | Ack Mode | Concurrency | Delivery Guarantee |
|---|---|---|---|---|---|
| `SimpleConsumer` | `consumer/SimpleConsumer.java` | `simple-consumer-group` | Auto | 3 | At-most-once |
| `ManualAckConsumer` | `consumer/ManualAckConsumer.java` | `manual-ack-group` | MANUAL | 3 | At-least-once |
| `BatchConsumer` | `consumer/BatchConsumer.java` | `batch-consumer-group` | BATCH | 2 | At-least-once (batch) |
| `ErrorHandlingConsumer` | `consumer/ErrorHandlingConsumer.java` | `error-handling-group` | MANUAL | 3 | At-least-once + DLQ |
| `GroupedConsumer` | `consumer/GroupedConsumer.java` | `grouped-consumer-1` | Auto | 3 | At-most-once |

All consumers listen on the `demo-messages` topic (3 partitions). Each consumer belongs to a **different consumer group**, so they each receive their own independent copy of every message.

---

## 1. SimpleConsumer — Auto-Commit

**File**: `src/main/java/.../consumer/SimpleConsumer.java`

### What It Does

The simplest possible consumer. Receives one `ConsumerRecord` at a time, logs the payload, simulates work with a brief sleep, then lets the container commit the offset automatically in the background.

### Source Code

```java
@Component
@Slf4j
public class SimpleConsumer {

    private static final String GROUP_ID = "simple-consumer-group";

    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"   // auto-commit factory
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record) {
        long startTime = System.nanoTime();
        String memberId = Thread.currentThread().getName();

        try {
            DemoTransaction message = requirePayload(record);

            log.info("event=consume_start pattern=simple groupId={} memberId={} "
                    + "topic={} partition={} offset={} key={} messageId={}",
                GROUP_ID, memberId, record.topic(), record.partition(),
                record.offset(), record.key(), message.getMessageId());

            // Simulate processing time
            Thread.sleep(100);

            log.info("event=consume_success pattern=simple groupId={} partition={} "
                    + "offset={} durationMs={}",
                GROUP_ID, record.partition(), record.offset(),
                elapsedMillis(startTime));

        } catch (DeserializationException ex) {
            log.error("event=consume_failure errorType=deserialization partition={} offset={}",
                record.partition(), record.offset(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("event=consume_failure errorType=interrupted", ex);
        }
    }
}
```

### How Auto-Commit Works

```
poll() returns records
     │
     ├─► Listener processes record[0]
     ├─► Listener processes record[1]
     ├─► Listener processes record[2]
     │
     │   (every auto.commit.interval.ms = 5000ms in background)
     │
     └─► Container calls commitSync(latestPolledOffset)
         → offset advances regardless of whether processing succeeded
```

**Risk**: If the application crashes after `poll()` but before the background `commitSync()` fires, those records are redelivered on restart — which can be good (at-least-once) or bad (duplicates), depending on your processing logic.

### When to Use

- ✅ Metrics aggregation (a few lost metrics is fine)
- ✅ Log forwarding (idempotent storage downstream)
- ✅ Demo / learning scenarios
- ❌ Financial or business-critical processing
- ❌ Workflows where duplicates cause problems

---

## 2. ManualAckConsumer — Explicit Acknowledgment

**File**: `src/main/java/.../consumer/ManualAckConsumer.java`

### What It Does

Receives one record and an `Acknowledgment` handle. Only calls `ack.acknowledge()` **after** business logic succeeds. Failed records are retried up to `MAX_RETRY_ATTEMTS=3` times; on exhaustion, they are forwarded to the `demo-dlq` topic.

### Source Code

```java
@Component
@Slf4j
public class ManualAckConsumer {

    private static final String GROUP_ID = "manual-ack-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final KafkaTemplate<String, DemoTransaction> kafkaTemplate;
    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "manualAckListenerContainerFactory"  // AckMode.MANUAL
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record, Acknowledgment ack) {
        String retryKey = record.topic() + "-" + record.partition() + "-" + record.offset();

        try {
            DemoTransaction message = requirePayload(record);
            processBusinessLogic(message);

            // Success: advance the committed offset
            ack.acknowledge();
            retryCounters.remove(retryKey);

        } catch (DeserializationException ex) {
            // Non-retryable: immediately to DLQ
            sendToDlq(record, ex);
            ack.acknowledge();   // move past the poison pill

        } catch (Exception ex) {
            int attempts = retryCounters
                    .computeIfAbsent(retryKey, k -> new AtomicInteger())
                    .incrementAndGet();

            if (attempts >= MAX_RETRY_ATTEMPTS) {
                // Give up: DLQ + acknowledge to unblock the partition
                sendToDlq(record, ex);
                ack.acknowledge();
                retryCounters.remove(retryKey);
            }
            // Below threshold: do NOT acknowledge → broker redelivers
        }
    }

    private void processBusinessLogic(DemoTransaction message) throws InterruptedException {
        // Simulate failure trigger for learning demos
        if (message.getDescription() != null
                && message.getDescription().toUpperCase().contains("FAIL_MANUAL")) {
            throw new IllegalStateException("Simulated business failure");
        }
        Thread.sleep(75);
    }

    private void sendToDlq(ConsumerRecord<String, DemoTransaction> record, Exception ex) {
        if (record.value() != null) {
            kafkaTemplate.send(KafkaTopicConfig.DEMO_DLQ_TOPIC, record.key(), record.value());
        }
    }
}
```

### Retry Flow Diagram

```
Record arrives (offset=42)
     │
     ├─ SUCCESS → ack.acknowledge() → committed offset = 43
     │
     ├─ FAILURE (attempt 1/3)
     │     └─ do NOT acknowledge
     │         → consumer restarts → same record redelivered
     │
     ├─ FAILURE (attempt 2/3)
     │     └─ do NOT acknowledge → redelivered again
     │
     └─ FAILURE (attempt 3/3 — MAX_RETRY_ATTEMPTS)
           ├─ sendToDlq(record)     → demo-dlq topic receives the record
           └─ ack.acknowledge()     → committed offset = 43 (moves on)
```

### Why Idempotency Is Required

Because "do NOT acknowledge" causes redelivery, the same record may be processed 2 or 3 times. If your business logic is not idempotent, you may apply the same effect multiple times (e.g., double debit).

```
Idempotency strategies:
1. Check messageId against a processed-IDs store before acting
2. Use database UPSERT / INSERT IGNORE with messageId as a unique key
3. Use Redis SETNX: SET "processed:{messageId}" 1 NX EX 86400
```

### Trigger the Retry Scenario

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD",
       "description":"FAIL_MANUAL test"}'

# Watch logs for:
# attempt=1 → no ack
# attempt=2 → no ack
# attempt=3 → sendToDlq + ack
```

---

## 3. BatchConsumer — Bulk Processing

**File**: `src/main/java/.../consumer/BatchConsumer.java`

### What It Does

Receives a **list** of up to 100 records (configured via `max.poll.records=100`). Processes them in sub-batches of 25, then lets `AckMode.BATCH` commit all offsets after the method returns successfully.

### Source Code

```java
@Component
@Slf4j
public class BatchConsumer {

    private static final String GROUP_ID = "batch-consumer-group";
    private static final int SUB_BATCH_SIZE = 25;

    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "batchListenerContainerFactory"  // batchListener=true, AckMode.BATCH
    )
    public void consumeBatch(List<ConsumerRecord<String, DemoTransaction>> records) {
        long startTime = System.nanoTime();
        log.info("event=batch_receive batchSize={}", records.size());

        try {
            List<DemoTransaction> messages = new ArrayList<>(records.size());
            for (ConsumerRecord<String, DemoTransaction> record : records) {
                messages.add(requirePayload(record));
            }

            // Sub-batch processing: amortize DB round-trips across multiple records
            processInSubBatches(messages, SUB_BATCH_SIZE);

            double throughput = messages.size() * 1000.0 / Math.max(elapsedMillis(startTime), 1L);
            log.info("event=batch_success batchSize={} durationMs={} throughput={:.2f} msg/s",
                messages.size(), elapsedMillis(startTime), throughput);

        } catch (RuntimeException ex) {
            // All-or-nothing: one bad record fails the entire batch → full re-delivery
            log.error("event=batch_failure batchSize={} error={}", records.size(), ex.getMessage());
            throw ex;  // AckMode.BATCH does not commit on exception
        }
    }

    private void processInSubBatches(List<DemoTransaction> messages, int batchSize) {
        for (int i = 0; i < messages.size(); i += batchSize) {
            List<DemoTransaction> subBatch = messages.subList(i,
                    Math.min(i + batchSize, messages.size()));

            if (subBatch.stream().anyMatch(this::shouldFail)) {
                throw new IllegalStateException("Simulated batch failure");
            }

            // In real code: bulkInsertToDatabase(subBatch);
        }
    }

    private boolean shouldFail(DemoTransaction tx) {
        return tx.getDescription() != null
               && tx.getDescription().toUpperCase().contains("FAIL_BATCH");
    }
}
```

### Batch vs Single Record Performance

```
Single-record consumer (100 records):
  poll() → process(1) → poll() → process(1) → ... × 100
  = 100 listener invocations
  = up to 100 separate DB writes

Batch consumer (100 records):
  poll() → process(List<100>) → 1 bulk DB insert
  = 1 listener invocation
  = 1 DB round-trip
```

### AckMode.BATCH Behavior

```
consumeBatch(List<100>) returns normally
     │
     └─► Container calls commitSync(highestOffset in batch)
         → all 100 offsets committed in one call

consumeBatch(List<100>) throws RuntimeException
     │
     └─► Container does NOT commit
         → entire batch of 100 is redelivered on next poll
```

### Sub-Batch Architecture

```
max.poll.records = 100
        │
        ▼
consumeBatch(List<100>)
        │
        ├─ subBatch[0..24]   → bulkInsert(25 rows)
        ├─ subBatch[25..49]  → bulkInsert(25 rows)
        ├─ subBatch[50..74]  → bulkInsert(25 rows)
        └─ subBatch[75..99]  → bulkInsert(25 rows)
                               ↑
                   If any sub-batch throws → entire 100 retried
```

### Trigger the Batch Failure Scenario

```bash
# Generate a batch — one message will have FAIL_BATCH in description
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":10.00,"currency":"USD",
       "description":"FAIL_BATCH trigger"}'

# Generate more messages to fill the batch
curl -X POST "http://localhost:8090/api/demo/generate-test-data?count=10"

# Watch logs: the entire batch (including valid records) is re-delivered
```

### When to Use

- ✅ Bulk database inserts (JDBC batch API)
- ✅ Aggregate computation (counting, summing per window)
- ✅ Data warehouse ingestion
- ✅ High-throughput pipelines where per-record overhead dominates
- ❌ Scenarios requiring per-record error isolation (use ManualAckConsumer)
- ❌ Low-volume processing where latency matters more than throughput

---

## 4. ErrorHandlingConsumer — Retry and DLQ

**File**: `src/main/java/.../consumer/ErrorHandlingConsumer.java`

### What It Does

The most production-relevant pattern. Distinguishes between **retryable errors** (transient, e.g. DB timeout) and **non-retryable errors** (permanent, e.g. validation failure). Retryable errors use exponential backoff before giving up and routing to DLQ.

### Source Code

```java
@Component
@Slf4j
public class ErrorHandlingConsumer {

    private static final String GROUP_ID = "error-handling-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 250L;

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "manualAckListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record, Acknowledgment ack) {
        String retryKey = record.topic() + "-" + record.partition() + "-" + record.offset();

        try {
            processMessage(record.value());

            ack.acknowledge();
            retryCounters.remove(retryKey);

        } catch (RetryableProcessingException ex) {
            int attempts = retryCounters.computeIfAbsent(retryKey, k -> new AtomicInteger())
                                        .incrementAndGet();
            long backoffMs = exponentialBackoff(attempts);

            log.error("Retryable failure attempt={}/{} backoffMs={}", attempts, MAX_RETRY_ATTEMPTS, backoffMs, ex);

            if (attempts >= MAX_RETRY_ATTEMPTS) {
                // Give up after 3 retries
                sendToDlq(record, ex);
                ack.acknowledge();
                retryCounters.remove(retryKey);
            } else {
                // Do not acknowledge → redelivery after sleep
                sleepBackoff(backoffMs);
            }

        } catch (DeserializationException | NonRetryableProcessingException ex) {
            // Permanent failure → DLQ immediately, no retries
            sendToDlq(record, ex);
            ack.acknowledge();
        }
    }

    private void processMessage(DemoTransaction message) {
        String description = message.getDescription() == null
                ? "" : message.getDescription().toUpperCase();

        if (description.contains("INVALID") || message.getAmount().signum() <= 0) {
            throw new NonRetryableProcessingException("Validation failed");
        }

        if (description.contains("RETRY") || description.contains("TIMEOUT")) {
            throw new RetryableProcessingException("Simulated downstream timeout");
        }
    }

    private long exponentialBackoff(int attempt) {
        // attempt 1 → 250ms, attempt 2 → 500ms, attempt 3 → 1000ms
        return BASE_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
    }

    private void sendToDlq(ConsumerRecord<String, DemoTransaction> record, Exception ex) {
        if (record.value() != null) {
            kafkaTemplate.send(KafkaTopicConfig.DEMO_DLQ_TOPIC, record.key(), record.value());
        }
    }
}
```

### Error Classification Decision Tree

```
Exception type?
     │
     ├─ DeserializationException
     │     → send to DLQ immediately (cannot fix malformed bytes by retrying)
     │     → ack.acknowledge()
     │
     ├─ NonRetryableProcessingException (validation, domain rule)
     │     → send to DLQ immediately (same input → same failure every time)
     │     → ack.acknowledge()
     │
     └─ RetryableProcessingException (DB timeout, network blip)
           ├─ attempts < MAX_RETRY_ATTEMPTS
           │     → sleepBackoff(exponential) → do NOT acknowledge → redelivery
           │
           └─ attempts >= MAX_RETRY_ATTEMPTS
                 → send to DLQ (giving up after exhausting retries)
                 → ack.acknowledge()
```

### Exponential Backoff Timeline

```
Attempt 1: sleep 250ms → redelivery
Attempt 2: sleep 500ms → redelivery
Attempt 3: sleep 1000ms → DLQ + ack

Total delay before DLQ: ~1.75 seconds
```

### Triggering Different Error Scenarios

```bash
# 1. Trigger non-retryable error (goes to DLQ immediately)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD",
       "description":"INVALID payment data"}'

# Watch logs: event=permanent_failure → event=dlq_send (no retry attempts)

# 2. Trigger retryable error (retries 3x, then DLQ)
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD",
       "description":"RETRY downstream timeout"}'

# Watch logs:
#   attempt=1/3 backoffMs=250
#   attempt=2/3 backoffMs=500
#   attempt=3/3 backoffMs=1000 → event=retryable_to_dlq

# 3. Verify DLQ received the message
# Open Kafka UI → demo-dlq → Messages
```

### The DLQ Pattern Explained

Without a DLQ, one poison-pill message blocks the entire partition:

```
Partition 2:
  [offset 0: valid]   → processed ✓
  [offset 1: valid]   → processed ✓
  [offset 2: BROKEN]  → fails every time → partition stuck at offset 2
  [offset 3: valid]   → NEVER reached
  [offset 4: valid]   → NEVER reached
```

With a DLQ:

```
Partition 2:
  [offset 2: BROKEN]  → 3 retries → send to demo-dlq → ack
  [offset 3: valid]   → processed normally ✓
  [offset 4: valid]   → processed normally ✓
```

**DLQ monitoring is essential**: The DLQ is a holding area, not a final destination. Set up alerts on `demo-dlq` consumer lag. Investigate, fix, and replay or discard DLQ records.

---

## 5. GroupedConsumer — Consumer Group Mechanics

**File**: `src/main/java/.../consumer/GroupedConsumer.java`

### What It Does

Functionally similar to `SimpleConsumer`, but uses a **different group ID** (`grouped-consumer-1`). This demonstrates that multiple consumer groups can consume the same topic independently with separate offset tracking.

### Source Code

```java
@Component
@Slf4j
public class GroupedConsumer {

    private static final String GROUP_ID = "grouped-consumer-1";

    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record) {
        DemoTransaction message = record.value();

        log.info("event=consume_start pattern=grouped groupId={} topic={} "
                + "partition={} offset={} messageId={}",
            GROUP_ID, record.topic(), record.partition(),
            record.offset(), message.getMessageId());

        // Partition assignment: within this group, each partition goes to
        // exactly one consumer thread. With concurrency=3 and 3 partitions,
        // each thread "owns" one partition.

        // Exercise: run 2 app instances and observe partition rebalancing.
    }
}
```

### Consumer Group Partition Assignment

```
Topic: demo-messages (3 partitions)

Group "simple-consumer-group" (1 instance, 3 threads):
  Thread-0  → Partition 0
  Thread-1  → Partition 1
  Thread-2  → Partition 2

Group "grouped-consumer-1" (1 instance, 3 threads):
  Thread-0  → Partition 0   ← INDEPENDENT from above group
  Thread-1  → Partition 1
  Thread-2  → Partition 2

Both groups receive ALL messages from demo-messages.
They don't share or compete for offsets.
```

### Rebalancing Demo

```
Initial state (1 app instance):
  grouped-consumer-1 instance A owns partitions [0, 1, 2]

Start a second instance:
  Rebalance triggered...
  grouped-consumer-1 instance A → partitions [0, 1]
  grouped-consumer-1 instance B → partition [2]

Stop instance B:
  Rebalance triggered (after session.timeout.ms = 10s)...
  grouped-consumer-1 instance A → partitions [0, 1, 2]   (re-acquired)
```

### Partition Count Limits Parallelism

```
Topic has 3 partitions.

1 consumer in group → handles all 3 partitions
2 consumers in group → ~1.5 partitions each (1 gets 2, 1 gets 1)
3 consumers in group → 1 partition each (maximum utilization)
4 consumers in group → 3 active, 1 idle (4th gets no partition)
10 consumers in group → 3 active, 7 idle (still only 3 partitions)
```

**Takeaway**: To increase parallelism beyond the current partition count, you must increase partition count first.

---

## Consumer Group Architecture

### All 5 Groups on demo-messages

```
                     Kafka Broker
              ┌──── demo-messages ────┐
              │   Partition 0         │
              │   Partition 1         │
              │   Partition 2         │
              └───────────────────────┘
                         │
         ┌───────────────┼────────────────────────────┐
         │               │                            │
         ▼               ▼                            ▼
  simple-consumer   manual-ack-group         batch-consumer-group
  -group            (ManualAckConsumer)       (BatchConsumer)
  (SimpleConsumer)
         │               │                            │
         ▼               ▼                            ▼
  error-handling    grouped-consumer-1
  -group            (GroupedConsumer)
  (ErrorHandlingConsumer)
```

Each group has its own committed offsets. A message published to `demo-messages` is **delivered to all 5 groups** independently.

---

## Offset Management Deep Dive

### Auto-Commit (SimpleConsumer, GroupedConsumer)

```yaml
# application.yml
spring:
  kafka:
    consumer:
      enable-auto-commit: false   # Managed by Spring container
                                   # Not raw Kafka auto-commit
```

In Spring Kafka, `enable-auto-commit: false` means the **Kafka client** doesn't auto-commit. Instead, the **Spring container** commits based on `AckMode`:

- Default `AckMode.BATCH`: commits after each `poll()` batch returns from the listener
- `AckMode.MANUAL`: commits only when `ack.acknowledge()` is called
- `AckMode.RECORD`: commits after each individual record's listener returns

### Offset Reset Scenarios

```yaml
auto-offset-reset: earliest   # Start from offset 0 for new groups (this project's default)
auto-offset-reset: latest     # Start from newest offset for new groups
```

```
Scenario 1: New consumer group, earliest
  → Reads all historical messages from offset 0

Scenario 2: New consumer group, latest
  → Only reads messages published after the consumer started

Scenario 3: Committed offset exists
  → Continues from committed offset regardless of earliest/latest

Scenario 4: Committed offset out of range (data deleted)
  → Falls back to earliest or latest based on auto-offset-reset
```

### Session Timeout and Heartbeat

```java
// KafkaConsumerConfig.java
configs.put(SESSION_TIMEOUT_MS_CONFIG, 10000);     // declare dead after 10s
configs.put(HEARTBEAT_INTERVAL_MS_CONFIG, 3000);   // send heartbeat every 3s
```

```
Rule: heartbeat interval < session timeout / 3
  Here: 3000 < 10000/3 = 3333 ✓

If no heartbeat received within session timeout:
  → Broker declares consumer dead
  → Rebalance triggered
  → Partitions reassigned to remaining consumers
```

---

## Consumer Configuration Reference

From `KafkaConsumerConfig.java`:

| Property | Value | Effect |
|---|---|---|
| `bootstrap.servers` | `localhost:9093` | Cluster entry point |
| `group.id` | varies per consumer | Partition ownership scope |
| `key.deserializer` | `StringDeserializer` | Key bytes → String |
| `value.deserializer` | `ErrorHandlingDeserializer` | Safety wrapper for JSON deserialization |
| `spring.deserializer.value.delegate.class` | `JsonDeserializer` | Actual JSON→Object conversion |
| `spring.json.trusted.packages` | `*` | Allow all packages (sandbox; restrict in production) |
| `spring.json.value.default.type` | `DemoTransaction` | Default target type when no type header |
| `auto.offset.reset` | `earliest` | New groups start from beginning |
| `enable.auto.commit` | `false` | Spring container manages commits |
| `session.timeout.ms` | `10000` | 10s before broker declares consumer dead |
| `heartbeat.interval.ms` | `3000` | 3s between liveness signals |
| `max.poll.records` | `100` (batch only) | Max records per poll for batch consumer |

### Consumer Factory Summary

| Factory Bean | Group | AckMode | Concurrency | Used By |
|---|---|---|---|---|
| `kafkaListenerContainerFactory` | auto | BATCH | 3 | SimpleConsumer, GroupedConsumer |
| `manualAckListenerContainerFactory` | manual-ack | MANUAL | 3 | ManualAckConsumer, ErrorHandlingConsumer |
| `batchListenerContainerFactory` | batch | BATCH | 2 | BatchConsumer |

---

**Related Files:**
- `KafkaConsumerConfig.java` — Consumer factory beans
- `KafkaTopicConfig.java` — Topic definitions
- `KafkaService.java` — Produces messages consumed by these consumers
- `ConsumerPatternsTest.java` — Unit tests for all patterns
