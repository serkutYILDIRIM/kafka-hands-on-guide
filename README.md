# Apache Kafka Hands-On Guide

A practical learning project for Apache Kafka using Spring Boot. This repository demonstrates core Kafka concepts through working code examples.

## 📚 What You'll Learn

- **Kafka Fundamentals**: Topics, partitions, offsets, consumer groups, and replication
- **Producer Patterns**: Simple, reliable, async, transactional, and partitioned producers
- **Consumer Patterns**: Auto-ack, manual-ack, batch processing, error handling, and consumer groups
- **Message Ordering**: Partition strategies and ordering guarantees
- **Exactly-Once Semantics**: Transactional producers and idempotent operations
- **Error Handling**: Retry mechanisms and Dead Letter Queue (DLQ) patterns
- **Monitoring**: Health checks and Kafka metrics

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### Steps

1. **Start Kafka Infrastructure**
   ```bash
   docker-compose up -d
   ```

2. **Build the Project**
   ```bash
   mvn clean install
   ```

3. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

4. **Access Kafka UI**
   - Open browser: http://localhost:8080
   - View topics, messages, and consumer groups

5. **Test the API**
   ```bash
   curl -X POST http://localhost:8090/api/demo/send-simple \
     -H "Content-Type: application/json" \
     -d '{
       "sourceId": "ACC001",
       "targetId": "ACC002",
       "amount": 100.50,
       "currency": "USD"
     }'
   ```

## 📁 Project Structure

```
src/main/java/io/github/serkutyildirim/kafka/
├── config/               # Kafka configuration classes
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   └── KafkaTopicConfig.java
├── model/                # Message models
│   ├── BaseMessage.java
│   ├── DemoTransaction.java
│   ├── DemoNotification.java
│   └── MessageStatus.java
├── producer/             # Producer implementations
│   ├── SimpleProducer.java
│   ├── ReliableProducer.java
│   ├── AsyncProducer.java
│   ├── TransactionalProducer.java
│   └── PartitionedProducer.java
├── consumer/             # Consumer implementations
│   ├── SimpleConsumer.java
│   ├── ManualAckConsumer.java
│   ├── BatchConsumer.java
│   ├── ErrorHandlingConsumer.java
│   └── GroupedConsumer.java
├── service/              # Business services
│   ├── KafkaService.java
│   └── MessageValidationService.java
└── controller/           # REST API
    └── DemoController.java
```

## 🎯 Learning Path

### For Beginners

1. Read [START-HERE.md](START-HERE.md) for step-by-step setup
2. Review [docs/01-kafka-basics.md](docs/01-kafka-basics.md)
3. Study `KafkaTopicConfig.java` to understand topic configuration
4. Explore `SimpleProducer.java` and `SimpleConsumer.java`
5. Run the application and observe message flow in Kafka UI

### For Intermediate

1. Explore `TransactionalProducer.java` for exactly-once semantics
2. Study `ManualAckConsumer.java` for offset management
3. Review `ErrorHandlingConsumer.java` for error patterns
4. Test partition routing with `PartitionedProducer.java`
5. Read [docs/03-producer-patterns.md](docs/03-producer-patterns.md)

### For Advanced

1. Run multiple application instances to see consumer group rebalancing
2. Test failure scenarios and recovery mechanisms
3. Implement custom serializers and partitioners
4. Analyze performance with different configurations
5. Review [docs/06-production-checklist.md](docs/06-production-checklist.md)

## 🔌 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/demo/health` | GET | Health check |
| `/api/demo/send-simple` | POST | Send via simple producer |
| `/api/demo/send-reliable` | POST | Send via reliable producer |
| `/api/demo/send-async` | POST | Send via async producer |
| `/api/demo/send-notification` | POST | Send notification |
| `/api/demo/demonstrate-all` | POST | Demonstrate all patterns |

## 🛠️ Tech Stack

- **Java 21** - LTS version
- **Spring Boot 3.5+** - Application framework
- **Spring Kafka** - Kafka integration
- **Apache Kafka 3.6+** - Message broker
- **Docker Compose** - Container orchestration
- **Kafka UI** - Web-based Kafka management

## 📖 Documentation

- [START-HERE.md](START-HERE.md) - Quick start guide
- [EXAMPLES.md](EXAMPLES.md) - Code examples
- [FAQ.md](FAQ.md) - Frequently asked questions
- [CONTRIBUTING.md](CONTRIBUTING.md) - Contribution guidelines
- [docs/](docs/) - Technical documentation

## 🔗 Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/)
- [Confluent Developer](https://developer.confluent.io/)

## 📝 License

MIT License

## 👨‍💻 Author

**Serkut Yıldırım**
- GitHub: [@serkutYILDIRIM](https://github.com/serkutYILDIRIM)

---

⭐ Star this repository if you find it helpful!
