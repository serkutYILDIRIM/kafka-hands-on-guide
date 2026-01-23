# Frequently Asked Questions (FAQ)

## General Questions

### What is this project for?

This is a **hands-on learning project** designed to help developers understand Apache Kafka through practical examples. It covers producer patterns, consumer patterns, error handling, and best practices.

### Do I need prior Kafka experience?

No! This project is beginner-friendly. Start with [START-HERE.md](START-HERE.md) and follow the learning path in [README.md](README.md).

### Can I use this in production?

This project is designed for **learning purposes**. While it demonstrates production-ready patterns, you should:
- Add proper security configurations
- Implement comprehensive monitoring
- Add authentication and authorization
- Review [docs/06-production-checklist.md](docs/06-production-checklist.md)

## Setup & Installation

### Why do I need Docker?

Docker provides an easy way to run Kafka, Zookeeper, and Kafka UI without manual installation. The `docker-compose.yml` sets up everything with one command.

### Can I run Kafka without Docker?

Yes, but it requires manual installation of:
- Zookeeper
- Kafka broker
- Configuration of all settings

Docker is **highly recommended** for learning.

### What ports does this project use?

- **8090**: Spring Boot application
- **8080**: Kafka UI
- **9092**: Kafka internal listener
- **9093**: Kafka external listener (used by application)
- **2181**: Zookeeper

### Port 8080 is already in use. What should I do?

Option 1: Stop the conflicting service

Option 2: Change the port in `docker-compose.yml`:
```yaml
kafka-ui:
  ports:
    - "8081:8080"  # Change left side to any available port
```

## Kafka Concepts

### What is a topic?

A **topic** is a category or stream name to which messages are published. Think of it as a channel or queue.

This project creates 4 topics:
- `demo-messages`: General messages
- `demo-transactions`: Transaction messages
- `demo-notifications`: Notification messages
- `demo-dlq`: Dead Letter Queue for failed messages

### What is a partition?

A **partition** is a division of a topic. Partitions enable:
- **Parallel processing**: Multiple consumers can read different partitions simultaneously
- **Ordering**: Messages within a partition are ordered
- **Scalability**: More partitions = more parallelism

Example: `demo-messages` has 3 partitions (0, 1, 2)

### What is a consumer group?

A **consumer group** is a set of consumers that work together to consume a topic. 

Key points:
- Each partition is consumed by **one consumer** in the group
- Multiple groups can consume the **same topic** independently
- Enables both **load balancing** and **pub-sub** patterns

### What is offset?

An **offset** is a unique identifier for a message within a partition. It's like a bookmark showing where a consumer has read up to.

### What is replication factor?

**Replication factor** determines how many copies of data are maintained. This project uses RF=1 (single broker, no replication) for simplicity.

In production, use RF=3 for fault tolerance.

## Producer Questions

### When should I use SimpleProducer?

Use for:
- ✅ Non-critical messages
- ✅ High throughput scenarios
- ✅ When occasional message loss is acceptable

Examples: Metrics, logs, user activity tracking

### When should I use ReliableProducer?

Use for:
- ✅ Critical business data
- ✅ Financial transactions
- ✅ When you need delivery confirmation

Examples: Payment processing, order creation, account updates

### When should I use AsyncProducer?

Use for:
- ✅ High-throughput scenarios
- ✅ When you need callbacks
- ✅ Balance between performance and reliability

Examples: Notification systems, event processing

### When should I use TransactionalProducer?

Use for:
- ✅ Exactly-once semantics required
- ✅ Multiple messages must be atomic
- ✅ Database + Kafka coordination

Examples: Multi-step workflows, distributed transactions

### What's the difference between sync and async send?

**Synchronous (Reliable)**:
- Blocks until acknowledgment
- Guarantees delivery before returning
- Lower throughput

**Asynchronous (Async)**:
- Returns immediately
- Uses callback for result
- Higher throughput

## Consumer Questions

### Why is enable-auto-commit set to false?

Manual commit provides better control over offset management:
- Commit only after successful processing
- Prevent data loss on consumer crashes
- Implement custom retry logic

### What happens if a consumer crashes?

1. Kafka detects the consumer is down (heartbeat timeout)
2. Partition rebalancing occurs
3. Another consumer in the group takes over
4. Processing resumes from last committed offset

### Why do I see duplicate messages?

Common reasons:
- Consumer crashed before committing offset
- Rebalancing occurred
- Network issues

This is **at-least-once delivery** (default in Kafka). Use transactions for exactly-once.

### How do I check consumer lag?

1. Open Kafka UI (http://localhost:8080)
2. Go to "Consumers"
3. Select consumer group
4. View lag per partition

**Lag = 0** means consumer is up to date.

### Can multiple consumers in the same group process the same partition?

**No**. Each partition is assigned to exactly one consumer in a group. This ensures ordering within partitions.

## Error Handling

### What is a Dead Letter Queue (DLQ)?

A **DLQ** is a special topic for messages that fail processing after max retries. It prevents:
- Infinite retry loops
- Blocking of message processing
- Data loss

### When should I send a message to DLQ?

After:
- Max retries exceeded
- Unrecoverable errors (e.g., invalid data format)
- Business rule violations

### How do I reprocess DLQ messages?

1. Fix the underlying issue
2. Read messages from DLQ topic
3. Re-send to original topic OR process manually

## Performance

### How can I increase throughput?

Producer side:
- Use async producers
- Increase `linger.ms` (batching)
- Enable compression (`snappy`, `lz4`, `gzip`)
- Increase `batch.size`

Consumer side:
- Use batch consumers
- Increase `max.poll.records`
- Use multiple consumers in group

### How many partitions should I use?

General rule: **Partitions = Desired parallelism**

Example:
- 1 partition: Only 1 consumer can process
- 3 partitions: Up to 3 consumers can process in parallel
- 10 partitions: Up to 10 consumers

**Too many partitions**: Overhead on broker
**Too few partitions**: Limited parallelism

### What is the best batch size?

It depends on:
- Message size
- Processing time
- Latency requirements

Start with defaults and tune based on metrics.

## Monitoring

### How do I monitor Kafka?

This project provides:
- **Kafka UI**: Web interface (http://localhost:8080)
- **Spring Boot Actuator**: Metrics endpoint
- **Application logs**: Detailed processing information

### What metrics should I watch?

Key metrics:
- **Consumer lag**: Are consumers keeping up?
- **Throughput**: Messages/second
- **Error rate**: Failed message percentage
- **Partition distribution**: Even load across partitions

## Troubleshooting

### Application won't start

Check:
1. Is Kafka running? `docker-compose ps`
2. Is port 8090 available?
3. Check logs: `mvn spring-boot:run`

### Messages not being consumed

Check:
1. Is consumer listening to correct topic?
2. Check consumer group in Kafka UI
3. Check application logs for errors
4. Verify offset position

### Cannot connect to Kafka

Check:
1. Docker containers running: `docker-compose ps`
2. Kafka fully started (wait 30 seconds after start)
3. Using correct port: `localhost:9093`
4. Firewall settings

### Kafka UI shows no brokers

Wait 30 seconds after `docker-compose up`. Kafka takes time to initialize.

If still not working:
```bash
docker-compose logs kafka
```

### Topics not created

Check application startup logs for:
```
Creating topic: demo-messages
Creating topic: demo-transactions
```

If missing, Kafka may not have been ready. Restart the application.

## Development

### How do I add a new topic?

1. Add constant in `KafkaTopicConfig.java`
2. Create `@Bean` method for the topic
3. Restart application

### How do I add a new producer?

1. Create class in `producer/` package
2. Inject `KafkaTemplate`
3. Implement send logic
4. Add method in `KafkaService.java`

### How do I add a new consumer?

1. Create class in `consumer/` package
2. Add `@KafkaListener` annotation
3. Specify topic and group ID
4. Implement processing logic

### Can I use a different serializer?

Yes! Modify `application.yml`:
```yaml
spring.kafka.producer.value-serializer: YourCustomSerializer
spring.kafka.consumer.value-deserializer: YourCustomDeserializer
```

## Best Practices

### Should I use auto-commit?

Generally **no** for:
- Critical data processing
- When you need error handling
- When you want control over offset management

Use auto-commit only for:
- Simple use cases
- When occasional message loss is acceptable

### How do I ensure message ordering?

1. Use the same **partition key** for related messages
2. Single consumer per partition (automatic in consumer groups)
3. Process messages synchronously within consumer

### Should I use transactions?

Use transactions when:
- You need exactly-once semantics
- Multiple messages must be atomic
- Coordinating with database operations

Don't use if:
- Performance is critical and at-least-once is acceptable
- Messages are independent

## Additional Resources

### Where can I learn more?

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [Confluent Developer](https://developer.confluent.io/)

### How do I contribute?

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

### Found a bug?

Open an issue on GitHub with:
- Steps to reproduce
- Expected vs actual behavior
- Logs and error messages

---

Still have questions? Open an issue on GitHub!
