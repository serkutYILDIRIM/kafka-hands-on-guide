# Common Pitfalls and How to Avoid Them

Guide to common mistakes when working with Kafka and their solutions.

## Table of Contents

- [Configuration Issues](#configuration-issues)
- [Producer Problems](#producer-problems)
- [Consumer Problems](#consumer-problems)
- [Performance Issues](#performance-issues)
- [Data Issues](#data-issues)
- [Operational Issues](#operational-issues)

## Configuration Issues

### 1. Wrong Bootstrap Server Address

**Problem**: Application can't connect to Kafka

**Symptoms**:
```
ERROR org.apache.kafka.clients.NetworkClient - Connection to node -1 failed
```

**Solution**:
```yaml
# Development (external access)
spring.kafka.bootstrap-servers: localhost:9093

# Production (multiple brokers)
spring.kafka.bootstrap-servers: broker1:9092,broker2:9092,broker3:9092
```

**Prevention**: Always test connectivity with `telnet` or `nc`:
```bash
telnet localhost 9093
```

### 2. Mismatched Serializers/Deserializers

**Problem**: Messages fail to serialize/deserialize

**Symptoms**:
```
org.apache.kafka.common.errors.SerializationException
```

**Common Mistakes**:
```yaml
# ❌ Wrong: Producer uses JSON, Consumer expects String
producer.value-serializer: JsonSerializer
consumer.value-deserializer: StringDeserializer

# ✅ Correct: Both use JSON
producer.value-serializer: JsonSerializer
consumer.value-deserializer: JsonDeserializer
```

**Solution**:
- Match serializer/deserializer types
- Use `ErrorHandlingDeserializer` wrapper
- Set `spring.json.trusted.packages`

### 3. Auto-Offset-Reset Confusion

**Problem**: Consumer missing messages or reprocessing old ones

**Scenarios**:
```yaml
# Scenario 1: First time consumer
auto-offset-reset: latest
# Result: Misses all existing messages

auto-offset-reset: earliest
# Result: Processes all messages from beginning

# Scenario 2: Offset out of range (data deleted)
auto-offset-reset: latest
# Result: Skips to end, loses data

auto-offset-reset: earliest
# Result: Reprocesses from oldest available
```

**Solution**:
- Development: Use `earliest` to see all messages
- Production: Use `latest` for new consumers
- Critical data: Implement offset monitoring

### 4. Enable-Auto-Commit Misunderstanding

**Problem**: Messages lost or duplicated

**With auto-commit enabled**:
```java
@KafkaListener(...)
public void consume(Message msg) {
    // Offset committed automatically before processing
    processMessage(msg);  // If this fails, message is lost!
}
```

**Solution**:
```yaml
enable-auto-commit: false
```

```java
@KafkaListener(...)
public void consume(Message msg, Acknowledgment ack) {
    processMessage(msg);  // Process first
    ack.acknowledge();    // Then commit
}
```

### 5. Insufficient Session Timeout

**Problem**: Frequent rebalancing

**Symptoms**:
```
WARN org.apache.kafka.clients.consumer.internals.ConsumerCoordinator - 
  Member consumer-1 sending LeaveGroup request due to consumer poll timeout
```

**Solution**:
```yaml
spring:
  kafka:
    consumer:
      properties:
        # Processing time < max.poll.interval.ms
        max.poll.interval.ms: 300000      # 5 minutes
        session.timeout.ms: 10000         # 10 seconds
        heartbeat.interval.ms: 3000       # 3 seconds
```

**Rule**: `heartbeat.interval.ms` < `session.timeout.ms` < `max.poll.interval.ms`

## Producer Problems

### 1. Not Handling Send Failures

**Problem**: Silent message loss

**Bad Code**:
```java
// ❌ Fire-and-forget - no error handling
kafkaTemplate.send(topic, message);
```

**Good Code**:
```java
// ✅ Handle the result
CompletableFuture<SendResult> future = kafkaTemplate.send(topic, message);
future.whenComplete((result, ex) -> {
    if (ex != null) {
        logger.error("Failed to send message", ex);
        handleFailure(message, ex);
    }
});
```

**Better Code**:
```java
// ✅ Synchronous with explicit error handling
try {
    kafkaTemplate.send(topic, message).get();
} catch (ExecutionException e) {
    if (e.getCause() instanceof RetriableException) {
        retry(message);
    } else {
        sendToDLQ(message);
    }
}
```

### 2. Large Message Payloads

**Problem**: Messages rejected by broker

**Symptoms**:
```
org.apache.kafka.common.errors.RecordTooLargeException: 
  The message is 2000000 bytes when serialized which is larger than 1048576
```

**Solutions**:

**Option 1: Increase limits** (not recommended)
```yaml
# Producer
spring.kafka.producer.properties.max.request.size: 2097152  # 2MB

# Broker (require restart)
message.max.bytes: 2097152
```

**Option 2: Compress data**
```yaml
spring.kafka.producer.properties.compression.type: snappy
```

**Option 3: Store externally** (recommended)
```java
// Store large data in S3/database
String fileUrl = s3.upload(largeData);

// Send only reference
Message message = new Message(id, fileUrl);
kafkaTemplate.send(topic, message);
```

### 3. Not Using Idempotence

**Problem**: Duplicate messages on retry

**Without idempotence**:
```
Send attempt 1: Network timeout (message actually delivered)
Send attempt 2: Retry succeeds
Result: Duplicate message
```

**Solution**:
```yaml
spring:
  kafka:
    producer:
      properties:
        enable.idempotence: true
```

**Effect**: Kafka deduplicates using producer ID and sequence number

### 4. Incorrect Partition Key

**Problem**: Uneven partition distribution or ordering issues

**Bad Code**:
```java
// ❌ Random partition - no ordering guarantee
kafkaTemplate.send(topic, message);

// ❌ Bad key distribution
kafkaTemplate.send(topic, "fixed-key", message);
```

**Good Code**:
```java
// ✅ Use natural key for related messages
String key = transaction.getAccountId();
kafkaTemplate.send(topic, key, message);

// ✅ Ensures related messages go to same partition
```

### 5. Blocking on Synchronous Sends

**Problem**: Low throughput

**Bad Code**:
```java
for (Message msg : messages) {
    // ❌ Blocks for each message
    kafkaTemplate.send(topic, msg).get();
}
```

**Good Code**:
```java
// ✅ Send all asynchronously, then wait
List<CompletableFuture> futures = new ArrayList<>();
for (Message msg : messages) {
    futures.add(kafkaTemplate.send(topic, msg));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
```

## Consumer Problems

### 1. Poison Pill Messages

**Problem**: One bad message blocks entire partition

**Scenario**:
```java
@KafkaListener(...)
public void consume(Message msg) {
    parse(msg);  // Throws exception for malformed message
    // Consumer stuck - can't progress past bad message
}
```

**Solution**:
```java
@KafkaListener(...)
public void consume(Message msg, Acknowledgment ack) {
    try {
        parse(msg);
        process(msg);
        ack.acknowledge();
    } catch (ParseException e) {
        logger.error("Poison pill detected: {}", msg);
        sendToDLQ(msg, e);
        ack.acknowledge();  // Skip it!
    }
}
```

### 2. Slow Processing Causing Rebalance

**Problem**: Consumer kicked out of group

**Symptoms**:
```
WARN ConsumerCoordinator - 
  Member consumer-1 sending LeaveGroup request due to consumer poll timeout
```

**Cause**: Processing time > `max.poll.interval.ms`

**Solutions**:

**Option 1: Increase timeout**
```yaml
spring.kafka.consumer.properties.max.poll.interval.ms: 600000  # 10 minutes
```

**Option 2: Reduce batch size**
```yaml
spring.kafka.consumer.max-poll-records: 100  # Smaller batches
```

**Option 3: Async processing**
```java
@KafkaListener(...)
public void consume(Message msg, Acknowledgment ack) {
    // Hand off to thread pool
    executor.submit(() -> {
        process(msg);
        ack.acknowledge();
    });
    // Poll continues immediately
}
```

### 3. Not Handling Duplicates

**Problem**: Duplicate processing with at-least-once delivery

**Bad Code**:
```java
@KafkaListener(...)
public void consume(Order order) {
    // ❌ May process same order twice
    chargeCustomer(order.getAmount());
}
```

**Good Code**:
```java
@KafkaListener(...)
public void consume(Order order, Acknowledgment ack) {
    // ✅ Idempotent processing
    if (processedOrders.contains(order.getId())) {
        logger.warn("Duplicate order: {}", order.getId());
        ack.acknowledge();
        return;
    }
    
    chargeCustomer(order.getAmount());
    processedOrders.add(order.getId());
    ack.acknowledge();
}
```

**Better**: Use database unique constraint
```java
@Transactional
public void consume(Order order) {
    try {
        orderRepository.save(order);  // Unique constraint on ID
        chargeCustomer(order.getAmount());
    } catch (DuplicateKeyException e) {
        logger.warn("Duplicate order: {}", order.getId());
    }
}
```

### 4. Consumer Lag Building Up

**Problem**: Consumer can't keep up with producer

**Detection**:
- Check Kafka UI → Consumers → Consumer Lag
- Lag > 1000 messages is concerning

**Solutions**:

**Option 1: Scale horizontally**
```bash
# Run multiple instances
# Partitions will be distributed
```

**Option 2: Optimize processing**
```java
// ❌ Slow: Individual database calls
for (Message msg : messages) {
    db.save(msg);
}

// ✅ Fast: Batch insert
db.batchSave(messages);
```

**Option 3: Increase partitions**
```java
// More partitions = more parallelism
TopicBuilder.name("topic").partitions(20).build();
```

### 5. Committing Too Frequently

**Problem**: Poor performance

**Bad Code**:
```java
@KafkaListener(...)
public void consume(Message msg, Acknowledgment ack) {
    process(msg);
    ack.acknowledge();  // ❌ Commit after every message
}
```

**Good Code**:
```java
private int processedCount = 0;

@KafkaListener(...)
public void consume(Message msg, Acknowledgment ack) {
    process(msg);
    processedCount++;
    
    if (processedCount >= 100) {  // ✅ Batch commits
        ack.acknowledge();
        processedCount = 0;
    }
}
```

## Performance Issues

### 1. Not Using Compression

**Problem**: High network and storage usage

**Solution**:
```yaml
spring:
  kafka:
    producer:
      properties:
        compression.type: snappy  # or lz4, gzip, zstd
```

**Comparison**:
- `none`: No CPU, larger messages
- `snappy`: Fast, good compression
- `lz4`: Fastest, good compression
- `gzip`: Best compression, higher CPU
- `zstd`: Best of both (Kafka 2.1+)

### 2. Small Batch Sizes

**Problem**: Low throughput

**Bad Configuration**:
```yaml
spring:
  kafka:
    producer:
      properties:
        linger.ms: 0       # ❌ Send immediately
        batch.size: 16384  # Default, may be too small
```

**Good Configuration**:
```yaml
spring:
  kafka:
    producer:
      properties:
        linger.ms: 100     # ✅ Wait 100ms to batch
        batch.size: 32768  # Larger batches
```

### 3. Too Many Topics/Partitions

**Problem**: Broker overhead

**Issue**: Each partition = file handles + memory

**Guideline**:
- Max partitions per broker: ~4000
- Total partitions in cluster: ~100,000

**Solution**: Consolidate topics or increase brokers

### 4. Not Using Connection Pooling

**Problem**: Creating new connections repeatedly

**Bad Code**:
```java
// ❌ New KafkaTemplate each time
public void sendMessage(Message msg) {
    KafkaTemplate template = new KafkaTemplate(...);
    template.send(topic, msg);
}
```

**Good Code**:
```java
// ✅ Inject shared KafkaTemplate
@Autowired
private KafkaTemplate kafkaTemplate;

public void sendMessage(Message msg) {
    kafkaTemplate.send(topic, msg);
}
```

## Data Issues

### 1. Schema Evolution Problems

**Problem**: Old consumers can't read new messages

**Scenario**:
```java
// Version 1
class User {
    String name;
}

// Version 2
class User {
    String firstName;  // Field renamed!
    String lastName;
}
```

**Solutions**:

**Option 1: Additive changes only**
```java
// ✅ Add new fields, keep old ones
class User {
    String name;           // Keep for backward compatibility
    String firstName;      // New field
    String lastName;       // New field
}
```

**Option 2: Use Schema Registry** (recommended)
```yaml
# Use Avro with Confluent Schema Registry
spring.kafka.producer.value-serializer: KafkaAvroSerializer
```

### 2. Message Ordering Violations

**Problem**: Messages processed out of order

**Cause**: Multiple partitions or multiple consumers

**Example**:
```
Partition 0: [A] [C] [E]
Partition 1: [B] [D] [F]

Processing order: A, B, C, D, E, F (NOT guaranteed!)
```

**Solution**: Use partition keys
```java
// ✅ Same userId always goes to same partition
kafkaTemplate.send(topic, user.getId(), message);
```

### 3. Message Loss

**Problem**: Messages disappear

**Common Causes**:

1. **Producer doesn't wait for ack**:
```yaml
# ❌ Risk of loss
spring.kafka.producer.acks: 0
```

2. **Consumer crashes before commit**:
```java
// ❌ Process then commit
process(msg);
ack.acknowledge();  // Crash here = message lost
```

3. **Data retention expired**:
```java
// Messages deleted after 7 days
TopicBuilder.name("topic")
    .config("retention.ms", "604800000")
    .build();
```

**Solutions**:
```yaml
# 1. Wait for all replicas
spring.kafka.producer.acks: all

# 2. Use manual commit
spring.kafka.consumer.enable-auto-commit: false

# 3. Increase retention for critical data
retention.ms: 2592000000  # 30 days
```

## Operational Issues

### 1. Not Monitoring Consumer Lag

**Problem**: Issues discovered too late

**Solution**: Monitor lag in Kafka UI or with metrics
```java
@Component
public class LagMonitor {
    @Scheduled(fixedRate = 60000)
    public void checkLag() {
        // Get lag from Kafka
        long lag = getLag();
        if (lag > 1000) {
            alerting.notify("High consumer lag: " + lag);
        }
    }
}
```

### 2. Inadequate Replication

**Problem**: Data loss on broker failure

**Bad Configuration** (single broker):
```java
TopicBuilder.name("topic")
    .partitions(3)
    .replicas(1)  // ❌ No redundancy
    .build();
```

**Good Configuration** (production):
```java
TopicBuilder.name("topic")
    .partitions(3)
    .replicas(3)  // ✅ Can survive 2 broker failures
    .config("min.insync.replicas", "2")
    .build();
```

### 3. Not Handling Rebalancing

**Problem**: Processing disruption during rebalance

**Solution**: Implement rebalance listener
```java
@Bean
public ConsumerAwareRebalanceListener rebalanceListener() {
    return new ConsumerAwareRebalanceListener() {
        @Override
        public void onPartitionsAssigned(Consumer consumer, 
                                        Collection<TopicPartition> partitions) {
            logger.info("Partitions assigned: {}", partitions);
        }
        
        @Override
        public void onPartitionsRevoked(Consumer consumer, 
                                       Collection<TopicPartition> partitions) {
            logger.info("Partitions revoked: {}", partitions);
            // Commit offsets, close resources
        }
    };
}
```

### 4. Insufficient Testing

**Common Issues**:
- Not testing failure scenarios
- Not testing with production-like volumes
- Not testing rebalancing

**Solution**: Create test scenarios
```java
@Test
public void testConsumerFailure() {
    // Send messages
    sendMessages(100);
    
    // Simulate consumer crash
    consumer.stop();
    
    // Start new consumer
    consumer.start();
    
    // Verify all messages processed
    assertThat(processedMessages).hasSize(100);
}
```

## Quick Reference

### Checklist Before Going to Production

- [ ] Set `acks=all` for critical data
- [ ] Enable idempotence for producers
- [ ] Use manual commit for consumers
- [ ] Implement error handling and DLQ
- [ ] Set replication factor >= 3
- [ ] Monitor consumer lag
- [ ] Implement idempotent processing
- [ ] Test failure scenarios
- [ ] Configure appropriate timeouts
- [ ] Enable compression
- [ ] Set up alerting
- [ ] Document schema evolution strategy

### Common Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| `TimeoutException` | Broker unavailable | Check network, verify bootstrap servers |
| `SerializationException` | Serializer mismatch | Match producer/consumer serializers |
| `RecordTooLargeException` | Message > max.request.size | Compress or store externally |
| `CommitFailedException` | Consumer kicked out | Increase max.poll.interval.ms |
| `TopicAuthorizationException` | No topic permissions | Check ACLs |
| `OffsetOutOfRangeException` | Offset deleted | Check retention, set auto-offset-reset |

---

**Next**: [06-production-checklist.md](06-production-checklist.md)
