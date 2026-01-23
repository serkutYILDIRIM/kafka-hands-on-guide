# Configuration Explained

Comprehensive guide to Kafka configuration in the Hands-On Guide project.

## Table of Contents

- [Application Configuration](#application-configuration)
- [Producer Configuration](#producer-configuration)
- [Consumer Configuration](#consumer-configuration)
- [Topic Configuration](#topic-configuration)
- [Environment-Specific Configuration](#environment-specific-configuration)

## Application Configuration

### application.yml

Main configuration file for the Spring Boot application.

```yaml
spring:
  application:
    name: kafka-hands-on-guide
  kafka:
    bootstrap-servers: localhost:9093
```

**bootstrap-servers**: Comma-separated list of Kafka broker addresses
- Development: `localhost:9093`
- Production: Multiple brokers for fault tolerance

## Producer Configuration

### Basic Producer Settings

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
```

### Key Configuration Properties

#### key-serializer / value-serializer

Converts Java objects to bytes for transmission.

**Options:**
- `StringSerializer`: For String keys/values
- `JsonSerializer`: For JSON objects (used in this project)
- `ByteArraySerializer`: For raw bytes
- Custom serializers: Implement `Serializer` interface

#### acks (Acknowledgment Level)

Controls durability guarantees.

**Options:**
- `0`: Fire-and-forget (no acknowledgment)
- `1`: Leader acknowledgment only
- `all` or `-1`: All in-sync replicas must acknowledge (most durable)

**In this project**: `all` for maximum durability

#### retries

Number of retry attempts for failed sends.

**Default**: 0
**This project**: 3

**Best practice**: Set to high value (Int.MAX_VALUE) with `max.in.flight.requests.per.connection=1` for ordering

### Advanced Producer Settings

```yaml
properties:
  enable.idempotence: true
  linger.ms: 10
  compression.type: snappy
```

#### enable.idempotence

Ensures exactly-once semantics by preventing duplicates.

**Default**: false (true in Kafka 3.0+)
**This project**: true

**Effect**: Sets `acks=all`, `retries=MAX`, and `max.in.flight.requests=5`

#### linger.ms

Time to wait before sending batch (batching delay).

**Default**: 0 (send immediately)
**This project**: 10ms

**Trade-off**:
- Higher value → Better batching, higher throughput, higher latency
- Lower value → Lower latency, lower throughput

#### compression.type

Compression algorithm for message batches.

**Options:**
- `none`: No compression (default)
- `gzip`: Best compression ratio, highest CPU
- `snappy`: Good compression, balanced CPU (used in this project)
- `lz4`: Fast compression, low CPU
- `zstd`: Best of both worlds (Kafka 2.1+)

**This project**: `snappy` for balance

## Consumer Configuration

### Basic Consumer Settings

```yaml
spring:
  kafka:
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
```

### Key Configuration Properties

#### key-deserializer / value-deserializer

Converts bytes back to Java objects.

**This project uses**:
- `StringDeserializer` for keys
- `ErrorHandlingDeserializer` wrapper for error handling

#### auto-offset-reset

Behavior when no initial offset or offset out of range.

**Options:**
- `earliest`: Start from beginning of partition
- `latest`: Start from end (only new messages)
- `none`: Throw exception

**This project**: `earliest` for learning (see all messages)
**Production**: Usually `latest`

#### enable-auto-commit

Automatic offset commit after poll.

**This project**: `false` (manual commit for better control)

**Auto-commit**:
- ✅ Simpler code
- ❌ May lose messages on crash
- ❌ May process duplicates

**Manual commit**:
- ✅ Better error handling
- ✅ Commit only after successful processing
- ❌ More complex code

### Advanced Consumer Settings

```yaml
properties:
  spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
  spring.json.trusted.packages: "*"
  session.timeout.ms: 10000
  heartbeat.interval.ms: 3000
```

#### spring.deserializer.value.delegate.class

Delegate deserializer for `ErrorHandlingDeserializer`.

**This project**: `JsonDeserializer` for JSON messages

#### spring.json.trusted.packages

Whitelist of packages for deserialization security.

**This project**: `"*"` (allow all - development only)
**Production**: Specify exact packages: `"com.example.models"`

#### session.timeout.ms

Max time before consumer is considered dead.

**Default**: 10000ms (10 seconds)
**Range**: 6000-300000ms

**If consumer doesn't send heartbeat within this time**: Rebalancing triggered

#### heartbeat.interval.ms

Frequency of heartbeat messages to broker.

**Default**: 3000ms (3 seconds)
**Best practice**: Set to 1/3 of `session.timeout.ms`

## Topic Configuration

### Topic Creation (KafkaTopicConfig.java)

```java
@Bean
public NewTopic demoMessagesTopic() {
    return TopicBuilder.name(DEMO_MESSAGES_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
}
```

### Topic Properties

#### partitions

Number of partitions for the topic.

**This project**:
- `demo-messages`: 3 partitions
- `demo-transactions`: 5 partitions
- `demo-notifications`: 3 partitions
- `demo-dlq`: 1 partition

**Considerations**:
- More partitions → More parallelism
- More partitions → More overhead
- Rule of thumb: Max(producers, consumers)

#### replicas (Replication Factor)

Number of copies of each partition.

**This project**: 1 (single broker)
**Production**: 3 (minimum for fault tolerance)

**Formula**: RF = 2N + 1 (where N is acceptable failures)

### Advanced Topic Configuration

```java
TopicBuilder.name("my-topic")
    .partitions(3)
    .replicas(1)
    .config("retention.ms", "604800000")           // 7 days
    .config("segment.ms", "86400000")              // 1 day
    .config("cleanup.policy", "delete")             // delete or compact
    .config("min.insync.replicas", "2")            // Min replicas for ack=all
    .build();
```

## Environment-Specific Configuration

### application-dev.yml

Development-specific overrides:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9093
    producer:
      acks: 1              # Less strict for dev
      retries: 1
    consumer:
      auto-offset-reset: latest

logging:
  level:
    root: DEBUG
    io.github.serkutyildirim.kafka: TRACE
```

**Activate**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Production Configuration (Example)

```yaml
spring:
  kafka:
    bootstrap-servers: kafka1:9092,kafka2:9092,kafka3:9092
    producer:
      acks: all
      retries: 2147483647
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        compression.type: lz4
    consumer:
      enable-auto-commit: false
      properties:
        spring.json.trusted.packages: "io.github.serkutyildirim.kafka.model"
        max.poll.records: 500
        max.poll.interval.ms: 300000
```

## Docker Configuration

### docker-compose.yml

```yaml
kafka:
  environment:
    - KAFKA_CFG_LISTENERS=CLIENT://:9092,EXTERNAL://:9093
    - KAFKA_CFG_ADVERTISED_LISTENERS=CLIENT://kafka:9092,EXTERNAL://localhost:9093
    - KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CLIENT:PLAINTEXT,EXTERNAL:PLAINTEXT
```

**Listeners**:
- `CLIENT://:9092`: Internal (container-to-container)
- `EXTERNAL://:9093`: External (host-to-container)

**This project uses**: Port 9093 for external access

## Performance Tuning

### High Throughput Configuration

**Producer**:
```yaml
batch.size: 32768                # Larger batches
linger.ms: 100                   # More batching time
compression.type: lz4            # Fast compression
buffer.memory: 67108864          # 64MB buffer
```

**Consumer**:
```yaml
fetch.min.bytes: 1024            # Min data per fetch
fetch.max.wait.ms: 500           # Max wait for fetch.min.bytes
max.poll.records: 1000           # More records per poll
```

### Low Latency Configuration

**Producer**:
```yaml
linger.ms: 0                     # Send immediately
compression.type: none           # No compression overhead
acks: 1                          # Don't wait for replicas
```

**Consumer**:
```yaml
fetch.min.bytes: 1               # Return immediately
max.poll.records: 100            # Process fewer at once
```

## Security Configuration (Future)

### TODO: SSL/TLS

```yaml
spring:
  kafka:
    ssl:
      trust-store-location: classpath:truststore.jks
      trust-store-password: password
      key-store-location: classpath:keystore.jks
      key-store-password: password
```

### TODO: SASL Authentication

```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: PLAIN
      sasl.jaas.config: "..."
```

## Troubleshooting Configuration Issues

### Consumer Not Receiving Messages

Check:
1. `auto-offset-reset`: Try `earliest`
2. Consumer group ID: Ensure unique or reset offset
3. Deserializer: Match producer serializer

### Producer Send Failures

Check:
1. `bootstrap-servers`: Correct address
2. `acks`: May need lower value for single broker
3. `retries`: Increase for unreliable networks

### Performance Issues

Check:
1. `linger.ms`: Increase for better batching
2. `batch.size`: Increase for larger batches
3. `compression.type`: Enable compression
4. Partition count: Increase for more parallelism

## Configuration Best Practices

1. **Use environment-specific profiles**: dev, staging, prod
2. **Externalize sensitive config**: Use environment variables
3. **Set appropriate timeouts**: Based on network latency
4. **Enable idempotence**: For production reliability
5. **Use manual commit**: For critical data
6. **Monitor configuration impact**: Track metrics after changes
7. **Document configuration decisions**: Why certain values chosen

## Related Files

- `application.yml`: Main configuration
- `application-dev.yml`: Development overrides
- `KafkaProducerConfig.java`: Custom producer beans
- `KafkaConsumerConfig.java`: Custom consumer beans
- `KafkaTopicConfig.java`: Topic definitions

---

**Next**: [03-producer-patterns.md](03-producer-patterns.md)
