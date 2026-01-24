# Consumer Patterns

Guide to different Kafka consumer implementation patterns in this project.

## Overview

This project demonstrates 5 consumer patterns:

1. **SimpleConsumer**: Basic consumption with auto-commit
2. **ManualAckConsumer**: Manual offset management
3. **BatchConsumer**: Batch message processing
4. **ErrorHandlingConsumer**: Retry and DLQ patterns
5. **GroupedConsumer**: Consumer group coordination

## 1. Simple Consumer

**Pattern**: Basic consumption with automatic offset commit

**Use Case**: Simple message processing where occasional message loss is acceptable

**Code**: `SimpleConsumer.java`

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = "simple-consumer-group"
)
public void consumeMessage(DemoTransaction message) {
    logger.info("[SimpleConsumer] Received message: ID={}, Source={}, Target={}, Amount={}",
        message.getMessageId(),
        message.getSourceId(),
        message.getTargetId(),
        message.getAmount());
    
    // Process message
    logger.debug("[SimpleConsumer] Message processed successfully");
}
```

**Characteristics**:
- ✅ Simple code
- ✅ Automatic offset management
- ✅ Good for non-critical data
- ⚠️ May lose messages on crash
- ⚠️ May process duplicates

**Configuration**:
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Manual in this project
```

**When to Use**:
- Metrics collection
- Log aggregation
- Non-critical events

## 2. Manual Acknowledgment Consumer

**Pattern**: Explicit offset commit after successful processing

**Use Case**: Critical data requiring at-least-once delivery

**Code**: `ManualAckConsumer.java`

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
    groupId = "manual-ack-consumer-group"
)
public void consumeWithManualAck(
        @Payload Object message,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset) {
    
    try {
        // Process message
        processMessage(message);
        
        // Acknowledge only after successful processing
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            logger.info("Message acknowledged at offset {}", offset);
        }
    } catch (Exception e) {
        logger.error("Error processing message at offset {}: {}", offset, e.getMessage());
        // Don't acknowledge - message will be reprocessed
    }
}
```

**Characteristics**:
- ✅ Better error handling
- ✅ Commit only after success
- ✅ At-least-once guarantee
- ⚠️ More complex code
- ⚠️ Potential duplicates on rebalance

**Acknowledgment Modes**:
- **RECORD**: Commit after each record
- **BATCH**: Commit after entire batch
- **MANUAL**: Explicit acknowledge() call
- **MANUAL_IMMEDIATE**: Commit immediately on acknowledge()

**Error Handling Options**:
1. **Don't acknowledge**: Message will be reprocessed
2. **Send to DLQ and acknowledge**: Skip message
3. **Retry with delay**: Implement retry logic

**When to Use**:
- Financial transactions
- Order processing
- Critical business data

## 3. Batch Consumer

**Pattern**: Process multiple messages at once

**Use Case**: High-throughput scenarios with bulk operations

**Code**: `BatchConsumer.java`

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_NOTIFICATIONS_TOPIC,
    groupId = "batch-consumer-group",
    batch = "true"
)
public void consumeBatch(
        @Payload List<Object> messages,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
        @Header(KafkaHeaders.OFFSET) List<Long> offsets) {
    
    logger.info("[BatchConsumer] Received batch of {} messages", messages.size());
    
    try {
        // Process all messages
        for (int i = 0; i < messages.size(); i++) {
            processMessage(messages.get(i));
        }
        
        // Acknowledge entire batch
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    } catch (Exception e) {
        logger.error("Error processing batch: {}", e.getMessage());
        // Handle batch error
    }
}
```

**Characteristics**:
- ✅ Higher throughput
- ✅ Efficient bulk operations
- ✅ Fewer commits
- ⚠️ Larger memory usage
- ⚠️ Higher latency per message

**Configuration**:
```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500        # Max messages per poll
      fetch-min-bytes: 1024        # Min data per fetch
      fetch-max-wait-ms: 500       # Max wait time
```

**Batch Processing Strategies**:

1. **All-or-Nothing**:
```java
// Process all messages, commit if all succeed
try {
    messages.forEach(this::process);
    acknowledgment.acknowledge();
} catch (Exception e) {
    // Entire batch will be reprocessed
}
```

2. **Individual Error Handling**:
```java
// Process each message individually
for (Object message : messages) {
    try {
        process(message);
    } catch (Exception e) {
        sendToDLQ(message, e);
    }
}
acknowledgment.acknowledge();
```

3. **Sub-batch Processing**:
```java
// Process in smaller chunks
int batchSize = 100;
for (int i = 0; i < messages.size(); i += batchSize) {
    List<Object> subBatch = messages.subList(i, 
        Math.min(i + batchSize, messages.size()));
    processSubBatch(subBatch);
}
```

**When to Use**:
- Bulk database inserts
- Batch API calls
- Analytics processing
- High-volume data ingestion

## 4. Error Handling Consumer

**Pattern**: Comprehensive error handling with retry and DLQ

**Use Case**: Reliable processing with automatic error recovery

**Code**: `ErrorHandlingConsumer.java`

```java
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
    groupId = "error-handling-consumer-group"
)
public void consumeWithErrorHandling(
        @Payload Object message,
        Acknowledgment acknowledgment,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset) {
    
    boolean processed = false;
    int attemptCount = 0;
    
    while (!processed && attemptCount < MAX_RETRY_ATTEMPTS) {
        try {
            attemptCount++;
            processMessage(message);
            processed = true;
            
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        } catch (Exception e) {
            logger.error("Error processing message (attempt {}): {}", 
                attemptCount, e.getMessage());
            
            if (attemptCount < MAX_RETRY_ATTEMPTS) {
                // Exponential backoff
                long backoff = RETRY_BACKOFF_MS * attemptCount;
                Thread.sleep(backoff);
            } else {
                // Max retries exceeded - send to DLQ
                sendToDLQ(message, e);
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                }
            }
        }
    }
}
```

**Error Handling Strategies**:

1. **Immediate Retry**:
```java
for (int i = 0; i < maxRetries; i++) {
    try {
        process(message);
        return;
    } catch (Exception e) {
        if (i == maxRetries - 1) throw e;
    }
}
```

2. **Exponential Backoff**:
```java
long backoff = initialDelay;
for (int i = 0; i < maxRetries; i++) {
    try {
        process(message);
        return;
    } catch (Exception e) {
        Thread.sleep(backoff);
        backoff *= 2; // Double each time
    }
}
```

3. **Dead Letter Queue**:
```java
private void sendToDLQ(Object message, Exception error) {
    DLQRecord dlqRecord = new DLQRecord(
        message,
        error.getMessage(),
        LocalDateTime.now(),
        originalTopic,
        partition,
        offset
    );
    kafkaTemplate.send(DLQ_TOPIC, dlqRecord);
}
```

**Characteristics**:
- ✅ Automatic retry
- ✅ DLQ for failed messages
- ✅ Prevents message loss
- ⚠️ Higher latency
- ⚠️ More complex logic

**When to Use**:
- Network-dependent processing
- External API calls
- Database operations
- Any potentially failing process

## 5. Grouped Consumers

**Pattern**: Multiple consumers working together

**Use Case**: Load balancing and pub-sub patterns

**Code**: `GroupedConsumer.java`

```java
// Consumer 1 in Group A
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
    groupId = "grouped-consumer-group-a",
    id = "consumer-a-1"
)
public void consumeGroupA1(@Payload Object message, ...) {
    logger.info("[GroupA-Consumer1] Received from partition {}", partition);
}

// Consumer 2 in Group A (same group)
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
    groupId = "grouped-consumer-group-a",
    id = "consumer-a-2"
)
public void consumeGroupA2(@Payload Object message, ...) {
    logger.info("[GroupA-Consumer2] Received from partition {}", partition);
}

// Consumer in Group B (different group)
@KafkaListener(
    topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
    groupId = "grouped-consumer-group-b",
    id = "consumer-b-1"
)
public void consumeGroupB1(@Payload Object message, ...) {
    logger.info("[GroupB-Consumer1] Received from partition {}", partition);
}
```

**Consumer Group Patterns**:

1. **Load Balancing** (Same Group):
```
Topic: 5 partitions
Group A: 3 consumers
  - Consumer 1: Partitions 0, 1
  - Consumer 2: Partitions 2, 3
  - Consumer 3: Partition 4
```

2. **Pub-Sub** (Different Groups):
```
Topic: demo-transactions
Group A: Processes transactions
Group B: Updates analytics
Group C: Sends notifications

Each group receives ALL messages
```

3. **Scaling Up**:
```
Start: 1 consumer → All 5 partitions
Add consumer 2 → Rebalance → Each gets ~2-3 partitions
Add consumer 3 → Rebalance → Each gets ~1-2 partitions
```

**Characteristics**:
- ✅ Horizontal scaling
- ✅ Load balancing
- ✅ Independent processing
- ⚠️ Rebalancing overhead
- ⚠️ No ordering across partitions

**Rebalancing**:
- Triggered by: Consumer join/leave, partition change
- During rebalance: No message processing
- Strategy: Range, RoundRobin, Sticky, CooperativeSticky

**When to Use**:
- High-volume topics
- Multiple processing pipelines
- Scalability requirements

## Comparison Table

| Pattern | Throughput | Reliability | Complexity | Memory | Use Case |
|---------|-----------|-------------|------------|--------|----------|
| Simple | High | Medium | Low | Low | Non-critical data |
| Manual Ack | Medium | High | Medium | Low | Critical data |
| Batch | Highest | Medium | Medium | High | Bulk operations |
| Error Handling | Low | Highest | High | Medium | Unreliable processing |
| Grouped | Scalable | Medium | Medium | Low | High volume |

## Best Practices

### 1. Offset Management

**Auto-commit**:
```yaml
enable-auto-commit: true
auto-commit-interval: 5000  # 5 seconds
```
- ✅ Simple
- ❌ May lose messages

**Manual commit**:
```yaml
enable-auto-commit: false
```
- ✅ Better control
- ✅ At-least-once guarantee

### 2. Error Handling

**Strategy**:
1. Log error with context
2. Retry with backoff (transient errors)
3. Send to DLQ (permanent errors)
4. Alert on DLQ threshold

**Example**:
```java
try {
    process(message);
} catch (TransientException e) {
    retry(message);
} catch (PermanentException e) {
    sendToDLQ(message);
    alerting.notify("DLQ message", e);
}
```

### 3. Idempotency

Ensure processing is idempotent (safe to process twice):

```java
public void processMessage(Message msg) {
    // Check if already processed
    if (processedIds.contains(msg.getId())) {
        logger.warn("Duplicate message: {}", msg.getId());
        return;
    }
    
    // Process
    doActualProcessing(msg);
    
    // Mark as processed
    processedIds.add(msg.getId());
}
```

### 4. Monitoring

Track these metrics:
- **Consumer lag**: Messages behind real-time
- **Processing time**: Time per message
- **Error rate**: Failed messages %
- **Throughput**: Messages/second

### 5. Graceful Shutdown

```java
@PreDestroy
public void shutdown() {
    // Stop accepting new messages
    // Wait for current processing to complete
    // Commit offsets
    // Close resources
}
```

## Common Pitfalls

### 1. Not Handling Poison Pills

**Problem**: One bad message blocks entire partition

**Solution**:
```java
try {
    process(message);
} catch (Exception e) {
    if (attemptCount >= MAX_RETRIES) {
        sendToDLQ(message);
        acknowledgment.acknowledge(); // Skip it
    }
}
```

### 2. Slow Processing

**Problem**: Consumer lag increases

**Solutions**:
- Add more consumers to group
- Optimize processing logic
- Use batch processing
- Increase max.poll.interval.ms

### 3. Rebalancing Too Often

**Problem**: Frequent rebalances disrupt processing

**Solutions**:
- Increase session.timeout.ms
- Decrease heartbeat.interval.ms
- Use cooperative rebalancing
- Optimize processing time

### 4. Memory Issues in Batch Processing

**Problem**: OutOfMemoryError with large batches

**Solutions**:
- Reduce max.poll.records
- Process in sub-batches
- Increase heap size
- Stream processing instead of loading all

## Performance Tuning

### High Throughput

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 1000
      fetch-min-bytes: 1024
      fetch-max-wait-ms: 500
```

### Low Latency

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 1
      fetch-min-bytes: 1
      fetch-max-wait-ms: 0
```

### Reliable Processing

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
      isolation-level: read_committed
      max-poll-interval-ms: 300000
```

## Testing Consumer Patterns

### 1. Test Simple Consumer

```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"}'
```

Watch logs for: `[SimpleConsumer] Received message`

### 2. Test Batch Consumer

Send multiple notifications:
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8090/api/demo/send-notification \
    -H "Content-Type: application/json" \
    -d "{\"recipientId\":\"U$i\",\"recipientEmail\":\"u$i@example.com\",\"title\":\"Test\",\"content\":\"Message $i\"}"
done
```

Watch logs for: `[BatchConsumer] Received batch of X messages`

### 3. Test Consumer Groups

Run multiple application instances:
```bash
# Terminal 1
mvn spring-boot:run

# Terminal 2
mvn spring-boot:run -Dserver.port=8091
```

Send messages and observe partition distribution in logs.

## Related Files

- `SimpleConsumer.java`: Basic consumption
- `ManualAckConsumer.java`: Manual offset management
- `BatchConsumer.java`: Batch processing
- `ErrorHandlingConsumer.java`: Error handling and DLQ
- `GroupedConsumer.java`: Consumer groups

---

**Next**: [05-common-pitfalls.md](05-common-pitfalls.md)
