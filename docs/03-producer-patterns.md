# Producer Patterns

Guide to different Kafka producer implementation patterns in this project.

## Overview

This project demonstrates 5 producer patterns:

1. **SimpleProducer**: Fire-and-forget (fastest, no guarantees)
2. **ReliableProducer**: Synchronous with acknowledgment (slower, reliable)
3. **AsyncProducer**: Asynchronous with callbacks (balanced)
4. **TransactionalProducer**: Exactly-once semantics (strongest guarantees)
5. **PartitionedProducer**: Partition-aware sending (ordering control)

## 1. Simple Producer

**Pattern**: Fire-and-forget

**Use Case**: Non-critical data where occasional loss is acceptable

**Code**: `SimpleProducer.java`

```java
public void sendMessage(DemoTransaction message) {
    logger.info("Sending message with SimpleProducer: {}", message.getMessageId());
    kafkaTemplate.send(KafkaTopicConfig.DEMO_MESSAGES_TOPIC, message.getSourceId(), message);
}
```

**Characteristics**:
- ✅ Fastest performance
- ✅ Non-blocking
- ✅ Simple code
- ❌ No delivery guarantee
- ❌ Potential message loss

**Test**:
```bash
curl -X POST http://localhost:8090/api/demo/send-simple \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.00,"currency":"USD"}'
```

## 2. Reliable Producer

**Pattern**: Synchronous send with acknowledgment

**Use Case**: Critical messages requiring confirmation

**Code**: `ReliableProducer.java`

```java
public boolean sendMessageSync(String key, Object message) {
    try {
        kafkaTemplate.send(topic, key, message).get(); // Blocks until ack
        return true;
    } catch (Exception e) {
        logger.error("Failed to send: {}", e.getMessage());
        return false;
    }
}
```

**Characteristics**:
- ✅ Delivery guarantee (with acks=all)
- ✅ Immediate error detection
- ✅ Retry capability
- ❌ Blocks until acknowledgment
- ❌ Lower throughput

**Test**:
```bash
curl -X POST http://localhost:8090/api/demo/send-reliable \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":1000.00,"currency":"USD"}'
```

## 3. Async Producer

**Pattern**: Asynchronous send with callbacks

**Use Case**: High-throughput with error handling

**Code**: `AsyncProducer.java`

```java
public void sendMessageAsync(String key, Object message) {
    CompletableFuture<SendResult<String, Object>> future = 
        kafkaTemplate.send(topic, key, message);
    
    future.whenComplete((result, ex) -> {
        if (ex == null) {
            logger.info("Sent to partition {} offset {}",
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
        } else {
            logger.error("Failed: {}", ex.getMessage());
        }
    });
}
```

**Characteristics**:
- ✅ High throughput
- ✅ Non-blocking
- ✅ Error handling via callbacks
- ✅ Good balance of performance and reliability

**Test**:
```bash
curl -X POST http://localhost:8090/api/demo/send-async \
  -H "Content-Type: application/json" \
  -d '{"sourceId":"ACC001","targetId":"ACC002","amount":250.00,"currency":"EUR"}'
```

## 4. Transactional Producer

**Pattern**: Exactly-once semantics with transactions

**Use Case**: Critical workflows requiring atomic operations

**Code**: `TransactionalProducer.java`

```java
public boolean sendInTransaction(String topic, String key, Object message) {
    return kafkaTemplate.executeInTransaction(ops -> {
        ops.send(topic, key, message);
        return true;
    });
}

public boolean sendMultipleInTransaction(String topic, Object... messages) {
    return kafkaTemplate.executeInTransaction(ops -> {
        for (Object msg : messages) {
            ops.send(topic, msg);
        }
        return true;
    });
}
```

**Configuration Required**:
```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: tx-
      properties:
        enable.idempotence: true
```

**Characteristics**:
- ✅ Exactly-once semantics
- ✅ Atomic multi-message sends
- ✅ No duplicates
- ❌ Lower throughput
- ❌ Higher latency
- ❌ Requires Kafka 0.11+

**TODO**: Implement endpoint in DemoController

## 5. Partitioned Producer

**Pattern**: Explicit partition control

**Use Case**: Ordering guarantees and load distribution

**Code**: `PartitionedProducer.java`

```java
// Send with partition key (same key = same partition)
public void sendWithPartitionKey(String topic, String key, Object message) {
    kafkaTemplate.send(topic, key, message);
}

// Send to specific partition
public void sendToPartition(String topic, int partition, String key, Object message) {
    kafkaTemplate.send(topic, partition, key, message);
}
```

**Partition Selection**:
1. **Explicit partition**: Directly specify partition number
2. **Partition key**: Hash of key determines partition
3. **Round-robin**: No key, distributed evenly

**Characteristics**:
- ✅ Ordering control
- ✅ Load balancing
- ✅ Colocation of related data
- ⚠️ Hot partitions if keys not balanced

## Comparison Table

| Pattern | Throughput | Latency | Reliability | Complexity |
|---------|-----------|---------|-------------|------------|
| Simple | Highest | Lowest | Low | Low |
| Reliable | Low | Highest | High | Medium |
| Async | High | Low | High | Medium |
| Transactional | Lowest | Highest | Highest | High |
| Partitioned | High | Low | Medium | Low |

## When to Use Each Pattern

### Simple Producer
- ✅ Metrics and monitoring
- ✅ Log aggregation
- ✅ User activity tracking
- ❌ Financial transactions
- ❌ Critical business data

### Reliable Producer
- ✅ Payment processing
- ✅ Order creation
- ✅ Account updates
- ❌ High-volume streams
- ❌ Real-time dashboards

### Async Producer
- ✅ Notification systems
- ✅ Event sourcing
- ✅ Data pipelines
- ✅ Most production use cases

### Transactional Producer
- ✅ Multi-step workflows
- ✅ Exactly-once requirements
- ✅ Database + Kafka coordination
- ❌ Simple fire-and-forget scenarios

### Partitioned Producer
- ✅ Ordered message processing
- ✅ Session-based processing
- ✅ User-specific streams
- ✅ Time-series data

## Best Practices

1. **Default to Async**: Good balance for most cases
2. **Use Transactions Sparingly**: Only when exactly-once needed
3. **Always Use Keys**: For related message ordering
4. **Handle Errors Gracefully**: Implement proper error handling
5. **Monitor Send Latency**: Track producer performance
6. **Use Batching**: Configure `linger.ms` > 0
7. **Enable Compression**: Save bandwidth and storage
8. **Set Reasonable Timeouts**: Based on SLA requirements

## Error Handling

### Retriable Errors
- Network issues
- Broker temporarily unavailable
- Leader election in progress

**Solution**: Retry automatically (configure `retries`)

### Non-Retriable Errors
- Message too large
- Invalid topic
- Serialization errors

**Solution**: Log and send to DLQ or alert

### Producer Error Handling Example

```java
try {
    kafkaTemplate.send(topic, key, message).get();
} catch (ExecutionException e) {
    if (e.getCause() instanceof RetriableException) {
        // Retry logic
        retry(topic, key, message);
    } else {
        // Send to DLQ
        sendToDLQ(message, e);
    }
}
```

## Performance Tips

1. **Batching**: Increase `linger.ms` (10-100ms)
2. **Compression**: Use `snappy` or `lz4`
3. **Buffer Size**: Increase `buffer.memory`
4. **Async Sends**: Don't block on `.get()`
5. **Partition Count**: Match expected parallelism
6. **Idempotence**: Enable for deduplication

## Monitoring

Key metrics to track:

- **record-send-rate**: Messages/second
- **request-latency-avg**: Average send latency
- **record-error-rate**: Failed sends/second
- **buffer-available-bytes**: Available buffer space

---

**Next**: [04-consumer-patterns.md](04-consumer-patterns.md)
