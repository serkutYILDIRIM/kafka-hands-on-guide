# Kafka Basics

This guide introduces fundamental Apache Kafka concepts with practical examples from the Hands-On Guide project.

## Table of Contents

- [What is Apache Kafka?](#what-is-apache-kafka)
- [Core Concepts](#core-concepts)
- [Architecture](#architecture)
- [Key Features](#key-features)
- [When to Use Kafka](#when-to-use-kafka)

## What is Apache Kafka?

Apache Kafka is a **distributed event streaming platform** designed for:
- High-throughput message processing
- Real-time data pipelines
- Event-driven architectures
- Log aggregation
- Stream processing

### Key Characteristics

- **Distributed**: Runs as a cluster across multiple servers
- **Scalable**: Handles millions of messages per second
- **Durable**: Messages are persisted to disk
- **Fault-tolerant**: Replicates data across multiple nodes
- **High-performance**: Low latency message delivery

## Core Concepts

### 1. Topics

A **topic** is a category or feed name to which records are published.

**Analogy**: Think of topics like channels in Slack or subreddits on Reddit.

**In this project:**
```java
// Defined in KafkaTopicConfig.java
public static final String DEMO_MESSAGES_TOPIC = "demo-messages";
public static final String DEMO_TRANSACTIONS_TOPIC = "demo-transactions";
public static final String DEMO_NOTIFICATIONS_TOPIC = "demo-notifications";
public static final String DEMO_DLQ_TOPIC = "demo-dlq";
```

**Topic Properties:**
- Name (unique identifier)
- Number of partitions
- Replication factor
- Retention period
- Compaction settings

### 2. Partitions

A **partition** is an ordered, immutable sequence of records within a topic.

**Why partitions?**
- **Parallelism**: Multiple consumers can read different partitions simultaneously
- **Scalability**: Distribute data across multiple brokers
- **Ordering**: Messages within a partition are ordered

**Visual Representation:**
```
Topic: demo-messages (3 partitions)

Partition 0: [Msg1] [Msg4] [Msg7] [Msg10]
Partition 1: [Msg2] [Msg5] [Msg8] [Msg11]
Partition 2: [Msg3] [Msg6] [Msg9] [Msg12]
```

**In this project:**
```java
@Bean
public NewTopic demoMessagesTopic() {
    return TopicBuilder.name(DEMO_MESSAGES_TOPIC)
            .partitions(3)      // 3 partitions
            .replicas(1)        // 1 replica (single broker)
            .build();
}
```

### 3. Producers

A **producer** is a client that publishes (writes) records to Kafka topics.

**Producer Responsibilities:**
- Serialize data
- Determine partition (by key or round-robin)
- Handle retries
- Manage batching and compression

**In this project:**
- `SimpleProducer`: Fire-and-forget pattern
- `ReliableProducer`: Synchronous with acknowledgment
- `AsyncProducer`: Non-blocking with callbacks
- `TransactionalProducer`: Exactly-once semantics
- `PartitionedProducer`: Explicit partition control

### 4. Consumers

A **consumer** is a client that reads records from Kafka topics.

**Consumer Responsibilities:**
- Deserialize data
- Track offsets
- Handle rebalancing
- Process messages
- Commit offsets

**In this project:**
- `SimpleConsumer`: Basic consumption
- `ManualAckConsumer`: Manual offset management
- `BatchConsumer`: Batch processing
- `ErrorHandlingConsumer`: Retry and DLQ
- `GroupedConsumer`: Consumer group demonstration

### 5. Consumer Groups

A **consumer group** is a set of consumers working together to consume a topic.

**Key Rules:**
- Each partition is consumed by **exactly one consumer** in the group
- Multiple groups can consume the **same topic** independently
- Enables both **load balancing** and **pub-sub** patterns

**Example:**
```
Topic: demo-transactions (5 partitions)
Consumer Group A (3 consumers):
  - Consumer 1: Partitions 0, 1
  - Consumer 2: Partitions 2, 3
  - Consumer 3: Partition 4

Consumer Group B (2 consumers):
  - Consumer 1: Partitions 0, 1, 2
  - Consumer 2: Partitions 3, 4
```

**In this project:**
```java
@KafkaListener(
    topics = "demo-transactions",
    groupId = "manual-ack-consumer-group"
)
```

### 6. Offsets

An **offset** is a unique sequential ID assigned to messages within a partition.

**Offset Tracking:**
- Consumer tracks last read offset
- Committed offset = "bookmark" for consumer restart
- Kafka stores committed offsets in special topic

**Example:**
```
Partition 0:
Offset: 0    1    2    3    4    5    6    7
Msg:   [A]  [B]  [C]  [D]  [E]  [F]  [G]  [H]
                        ↑
                   Last committed
```

**Commit Strategies:**
- **Auto-commit**: Kafka commits periodically (default)
- **Manual-commit**: Application controls when to commit
- **Sync-commit**: Blocking commit
- **Async-commit**: Non-blocking commit

### 7. Brokers

A **broker** is a Kafka server that stores data and serves clients.

**Broker Responsibilities:**
- Store partition data
- Handle read/write requests
- Replicate data
- Coordinate with other brokers

**In this project:**
```yaml
# docker-compose.yml
kafka:
  image: bitnami/kafka:latest
  environment:
    - KAFKA_BROKER_ID=1
```

### 8. Zookeeper

**Zookeeper** coordinates the Kafka cluster (in older versions).

**Zookeeper Roles:**
- Leader election
- Configuration management
- Cluster membership
- Partition assignment

**Note**: Kafka is moving away from Zookeeper (KRaft mode in newer versions).

## Architecture

### High-Level Architecture

```
Producers                  Kafka Cluster              Consumers
┌─────────┐               ┌────────────┐             ┌─────────┐
│Producer1│──────────────▶│  Broker 1  │◀────────────│Consumer1│
└─────────┘               │  Broker 2  │             └─────────┘
┌─────────┐               │  Broker 3  │             ┌─────────┐
│Producer2│──────────────▶└────────────┘◀────────────│Consumer2│
└─────────┘                     ▲                    └─────────┘
                                │
                           ┌─────────┐
                           │Zookeeper│
                           └─────────┘
```

### Message Flow

1. **Producer sends message**:
   - Serializes message
   - Determines partition (by key or algorithm)
   - Sends to broker

2. **Broker stores message**:
   - Appends to partition log
   - Replicates to followers (if RF > 1)
   - Sends acknowledgment

3. **Consumer reads message**:
   - Polls broker for new messages
   - Deserializes message
   - Processes message
   - Commits offset

## Key Features

### 1. Durability

Messages are persisted to disk and replicated across brokers.

**Configuration:**
```yaml
# application.yml
spring.kafka.producer.acks: all  # Wait for all replicas
```

### 2. Scalability

Add more partitions and brokers to scale horizontally.

**Example:**
- 1 partition → 1 consumer → 1x throughput
- 10 partitions → 10 consumers → 10x throughput

### 3. Ordering Guarantees

Messages are ordered **within a partition** but not across partitions.

**To guarantee order:**
- Use same partition key for related messages
- Single consumer per partition (automatic in consumer groups)

**Example:**
```java
// All messages from ACC001 go to same partition
kafkaTemplate.send(topic, "ACC001", message1);
kafkaTemplate.send(topic, "ACC001", message2);
kafkaTemplate.send(topic, "ACC001", message3);
// Order: message1 → message2 → message3 (guaranteed)
```

### 4. Retention

Messages are retained for a configurable period.

**Default**: 7 days

**Configuration:**
```java
TopicBuilder.name("my-topic")
    .config("retention.ms", "604800000")  // 7 days in ms
    .build();
```

### 5. Compression

Reduce network and storage overhead.

**Supported formats**: snappy, lz4, gzip, zstd

**In this project:**
```yaml
spring.kafka.producer.properties.compression.type: snappy
```

## When to Use Kafka

### ✅ Good Use Cases

1. **Event Streaming**: Real-time event processing
2. **Log Aggregation**: Centralized log collection
3. **Metrics Collection**: System and application metrics
4. **Change Data Capture**: Database change events
5. **Microservices Communication**: Async service integration
6. **Activity Tracking**: User activity streams
7. **IoT Data**: Sensor data collection

### ❌ Not Ideal For

1. **Request-Response**: Use REST/gRPC instead
2. **Simple Queues**: RabbitMQ might be simpler
3. **Small Scale**: Overkill for low-volume systems
4. **Complex Routing**: Use message brokers with routing

## Message Delivery Semantics

### At-Most-Once (0 or 1)

- Message may be lost
- No duplicates
- Fastest

**When to use**: Non-critical data (metrics, logs)

### At-Least-Once (1 or more)

- Message always delivered
- Duplicates possible
- Default in Kafka

**When to use**: Most use cases with idempotent processing

**In this project**: All consumers use at-least-once

### Exactly-Once (1)

- Message delivered exactly once
- No loss, no duplicates
- Requires transactions

**When to use**: Financial transactions, critical workflows

**In this project**: `TransactionalProducer`

## Performance Considerations

### Producer Performance

**Factors:**
- Batching (`linger.ms`)
- Compression
- Async vs Sync send
- Number of partitions

### Consumer Performance

**Factors:**
- Batch size (`max.poll.records`)
- Number of consumers in group
- Processing time per message
- Commit frequency

## Monitoring Metrics

Key metrics to track:

1. **Throughput**: Messages/second
2. **Latency**: Time to deliver message
3. **Consumer Lag**: Messages behind real-time
4. **Error Rate**: Failed message percentage
5. **Partition Balance**: Even distribution

**In this project**: View metrics in Kafka UI (http://localhost:8080)

## Best Practices

1. **Use appropriate partition count**: Balance parallelism and overhead
2. **Choose correct replication factor**: RF=3 for production
3. **Set proper retention**: Based on storage and use case
4. **Enable compression**: Save network and storage
5. **Use consumer groups**: For scalability
6. **Handle errors gracefully**: Implement DLQ pattern
7. **Monitor consumer lag**: Prevent backpressure
8. **Use appropriate serialization**: JSON for flexibility, Avro for schema evolution

## Next Steps

- [02-configuration-explained.md](02-configuration-explained.md) - Deep dive into configurations
- [03-producer-patterns.md](03-producer-patterns.md) - Producer implementation patterns
- [04-consumer-patterns.md](04-consumer-patterns.md) - Consumer implementation patterns

---

**Related Code Files:**
- `KafkaTopicConfig.java`: Topic creation
- `SimpleProducer.java`: Basic producer example
- `SimpleConsumer.java`: Basic consumer example
