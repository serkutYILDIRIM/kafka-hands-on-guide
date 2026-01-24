# Production Checklist

Comprehensive checklist for deploying Kafka applications to production.

## Table of Contents

- [Infrastructure](#infrastructure)
- [Configuration](#configuration)
- [Security](#security)
- [Monitoring](#monitoring)
- [Performance](#performance)
- [Reliability](#reliability)
- [Operational Readiness](#operational-readiness)

## Infrastructure

### Kafka Cluster

- [ ] **Minimum 3 brokers** for fault tolerance
- [ ] **Dedicated hardware/VMs** (not shared with other services)
- [ ] **Sufficient disk space** (consider retention × daily volume × 1.5)
- [ ] **Fast disks** (SSDs preferred, or RAID 10 for HDDs)
- [ ] **Network bandwidth** adequate for expected throughput
- [ ] **Separate Zookeeper ensemble** (3-5 nodes)
- [ ] **Cross-datacenter setup** if needed for disaster recovery

**Recommended Hardware**:
```
CPU: 12+ cores
RAM: 64GB+
Disk: 1TB+ SSD or 2TB+ HDD RAID 10
Network: 10Gb/s
```

### Zookeeper

- [ ] **Dedicated nodes** (don't run on Kafka brokers)
- [ ] **Odd number of nodes** (3 or 5)
- [ ] **Fast disks** for transaction logs
- [ ] **Low latency network** between nodes
- [ ] **Sufficient heap** (typically 1-4GB)

### Network

- [ ] **Low latency** between brokers (<10ms)
- [ ] **Sufficient bandwidth** (10Gb/s recommended)
- [ ] **Firewall rules** configured
- [ ] **DNS resolution** working correctly
- [ ] **Load balancer** for client connections (optional)

## Configuration

### Broker Configuration

```properties
# Broker Identity
broker.id=1
listeners=PLAINTEXT://:9092
advertised.listeners=PLAINTEXT://broker1.example.com:9092

# Zookeeper
zookeeper.connect=zk1:2181,zk2:2181,zk3:2181

# Replication
default.replication.factor=3
min.insync.replicas=2
unclean.leader.election.enable=false
auto.create.topics.enable=false

# Data Retention
log.retention.hours=168  # 7 days
log.retention.bytes=-1   # Unlimited
log.segment.bytes=1073741824  # 1GB

# Performance
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Log Compaction
log.cleanup.policy=delete
compression.type=producer
```

**Checklist**:
- [ ] `broker.id` unique per broker
- [ ] `advertised.listeners` uses resolvable hostnames
- [ ] `default.replication.factor >= 3`
- [ ] `min.insync.replicas = replication.factor - 1`
- [ ] `unclean.leader.election.enable=false`
- [ ] `auto.create.topics.enable=false`
- [ ] Retention configured based on needs

### Producer Configuration

```yaml
spring:
  kafka:
    producer:
      # Serialization
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      
      # Reliability
      acks: all
      retries: 2147483647  # Max retries
      
      # Idempotence
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        
        # Performance
        linger.ms: 10
        batch.size: 32768
        compression.type: lz4
        buffer.memory: 67108864  # 64MB
        
        # Timeouts
        request.timeout.ms: 30000
        delivery.timeout.ms: 120000
```

**Checklist**:
- [ ] `acks=all` for critical data
- [ ] `enable.idempotence=true`
- [ ] High `retries` value
- [ ] Compression enabled
- [ ] Appropriate timeouts

### Consumer Configuration

```yaml
spring:
  kafka:
    consumer:
      # Deserialization
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      
      # Offset Management
      enable-auto-commit: false
      auto-offset-reset: latest
      
      properties:
        # Deserialization
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "com.example.models"  # Not "*"
        
        # Consumer Group
        session.timeout.ms: 10000
        heartbeat.interval.ms: 3000
        max.poll.interval.ms: 300000
        max.poll.records: 500
        
        # Transactions
        isolation.level: read_committed
```

**Checklist**:
- [ ] `enable-auto-commit=false`
- [ ] `spring.json.trusted.packages` specific (not "*")
- [ ] `max.poll.interval.ms` > max processing time
- [ ] `isolation.level=read_committed` for transactional producers

### Topic Configuration

```java
@Bean
public NewTopic criticalTopic() {
    return TopicBuilder.name("critical-topic")
            .partitions(12)
            .replicas(3)
            .config("min.insync.replicas", "2")
            .config("retention.ms", "604800000")  // 7 days
            .config("segment.ms", "86400000")     // 1 day
            .config("cleanup.policy", "delete")
            .config("compression.type", "producer")
            .build();
}
```

**Checklist**:
- [ ] Appropriate partition count (= max parallelism)
- [ ] `replicas >= 3`
- [ ] `min.insync.replicas = replicas - 1`
- [ ] Retention based on business needs
- [ ] Cleanup policy set correctly

## Security

### Authentication & Authorization

- [ ] **Enable SASL/SSL** for client authentication
- [ ] **Enable ACLs** for topic/group authorization
- [ ] **Use separate users** for each application
- [ ] **Principle of least privilege** (minimal permissions)
- [ ] **Rotate credentials** regularly

**Example ACL**:
```bash
# Allow producer app to write to topic
kafka-acls --add \
  --allow-principal User:producer-app \
  --operation Write \
  --topic critical-topic

# Allow consumer app to read from topic
kafka-acls --add \
  --allow-principal User:consumer-app \
  --operation Read \
  --topic critical-topic \
  --group consumer-group
```

### Encryption

- [ ] **Enable SSL/TLS** for data in transit
- [ ] **Encrypt data at rest** (disk encryption)
- [ ] **Secure Zookeeper** communication

**SSL Configuration**:
```yaml
spring:
  kafka:
    bootstrap-servers: broker1:9093
    properties:
      security.protocol: SSL
      ssl.truststore.location: /path/to/truststore.jks
      ssl.truststore.password: password
      ssl.keystore.location: /path/to/keystore.jks
      ssl.keystore.password: password
      ssl.key.password: password
```

### Network Security

- [ ] **Firewall rules** restrict access
- [ ] **Private network** for broker communication
- [ ] **VPN/bastion** for administrative access
- [ ] **No public internet exposure**

## Monitoring

### Key Metrics

**Broker Metrics**:
- [ ] **UnderReplicatedPartitions** (should be 0)
- [ ] **OfflinePartitionsCount** (should be 0)
- [ ] **ActiveControllerCount** (should be 1)
- [ ] **BytesInPerSec / BytesOutPerSec**
- [ ] **MessagesInPerSec**
- [ ] **RequestsPerSec**
- [ ] **Disk usage** (< 80%)

**Producer Metrics**:
- [ ] **record-send-rate**
- [ ] **record-error-rate**
- [ ] **request-latency-avg**
- [ ] **buffer-available-bytes**

**Consumer Metrics**:
- [ ] **Consumer lag** (should be < 1000)
- [ ] **records-consumed-rate**
- [ ] **commit-latency-avg**
- [ ] **fetch-latency-avg**

### Monitoring Tools

- [ ] **Kafka UI** or similar web interface
- [ ] **Prometheus + Grafana** for metrics
- [ ] **ELK Stack** for logs
- [ ] **JMX monitoring** enabled

**Example Prometheus Config**:
```yaml
# JMX Exporter for Kafka metrics
javaagent: /opt/jmx_exporter/jmx_prometheus_javaagent.jar=7071:/opt/jmx_exporter/kafka-broker.yml
```

### Alerting

- [ ] **High consumer lag** (> threshold)
- [ ] **Under-replicated partitions** (> 0)
- [ ] **Offline partitions** (> 0)
- [ ] **Broker down**
- [ ] **Disk usage** (> 80%)
- [ ] **High error rate**
- [ ] **DLQ messages** (> threshold)

**Example Alert**:
```yaml
# Prometheus alert
- alert: HighConsumerLag
  expr: kafka_consumer_lag > 1000
  for: 5m
  annotations:
    summary: "High consumer lag on {{ $labels.topic }}"
```

## Performance

### Throughput Optimization

- [ ] **Compression enabled** (lz4 or snappy)
- [ ] **Batching configured** (linger.ms > 0)
- [ ] **Large batch size** (32KB+)
- [ ] **Multiple partitions** for parallelism
- [ ] **Async sends** where possible
- [ ] **Connection pooling** in applications

### Latency Optimization

- [ ] **Low linger.ms** (0-10ms)
- [ ] **Few partitions** (reduce routing)
- [ ] **Fast disk** (SSD)
- [ ] **Dedicated network**
- [ ] **Close proximity** (low network latency)

### Resource Allocation

**JVM Settings**:
```bash
# Kafka broker
export KAFKA_HEAP_OPTS="-Xmx6g -Xms6g"
export KAFKA_JVM_PERFORMANCE_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=20"

# Application
java -Xmx4g -Xms4g -XX:+UseG1GC -jar app.jar
```

**Checklist**:
- [ ] Kafka broker heap: 6GB (don't exceed 8GB)
- [ ] Application heap: Based on load
- [ ] Use G1GC for large heaps
- [ ] Monitor GC pauses

## Reliability

### Data Durability

- [ ] **Replication factor >= 3**
- [ ] **min.insync.replicas = RF - 1**
- [ ] **acks=all** for producers
- [ ] **Unclean leader election disabled**
- [ ] **Adequate retention** period

### Fault Tolerance

- [ ] **Multiple brokers** (>= 3)
- [ ] **Rack awareness** configured
- [ ] **Cross-datacenter** replication (if needed)
- [ ] **Backup Zookeeper** ensemble
- [ ] **Disaster recovery** plan

### Error Handling

- [ ] **Retry mechanism** in producers
- [ ] **Error handling** in consumers
- [ ] **Dead Letter Queue** implemented
- [ ] **Alerting** on errors
- [ ] **Idempotent processing**

**Example DLQ**:
```java
@Bean
public NewTopic dlqTopic() {
    return TopicBuilder.name("app-dlq")
            .partitions(1)
            .replicas(3)
            .config("retention.ms", "2592000000")  // 30 days
            .build();
}
```

### Testing

- [ ] **Unit tests** for message processing
- [ ] **Integration tests** with embedded Kafka
- [ ] **Load testing** with production-like volumes
- [ ] **Failure testing** (chaos engineering)
- [ ] **Recovery testing** (restart scenarios)

## Operational Readiness

### Documentation

- [ ] **Architecture diagram** 
- [ ] **Topic inventory** (purpose, owner, retention)
- [ ] **Consumer group inventory**
- [ ] **Runbook** for common issues
- [ ] **Disaster recovery** procedures
- [ ] **Scaling** procedures

### Backup & Recovery

- [ ] **Configuration backup** (broker, topic configs)
- [ ] **Offset backup** (consumer positions)
- [ ] **Data backup** (if applicable)
- [ ] **Recovery procedures** tested
- [ ] **RTO/RPO defined**

### Capacity Planning

- [ ] **Current usage** measured
- [ ] **Growth projections** calculated
- [ ] **Scaling plan** documented
- [ ] **Resource headroom** (30%+)
- [ ] **Cost optimization** reviewed

**Capacity Calculation**:
```
Daily Volume = 1B messages × 1KB avg = 1TB/day
Retention = 7 days
Replication = 3x
Total Storage = 1TB × 7 × 3 = 21TB
With 30% headroom = 27TB
```

### Deployment

- [ ] **Blue-green deployment** or canary
- [ ] **Rolling updates** for zero downtime
- [ ] **Health checks** configured
- [ ] **Graceful shutdown** implemented
- [ ] **Rollback plan** ready

**Health Check**:
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        // Check Kafka connectivity
        if (kafkaHealthy()) {
            return ResponseEntity.ok("UP");
        }
        return ResponseEntity.status(503).body("DOWN");
    }
}
```

### Team Readiness

- [ ] **On-call rotation** established
- [ ] **Runbooks** accessible
- [ ] **Training** completed
- [ ] **Escalation path** defined
- [ ] **Post-mortem process** in place

## Pre-Launch Checklist

### 1 Week Before

- [ ] Load testing completed
- [ ] Monitoring dashboards ready
- [ ] Alerts configured and tested
- [ ] Runbooks reviewed
- [ ] Team trained

### 1 Day Before

- [ ] Final configuration review
- [ ] Backup procedures verified
- [ ] Rollback plan confirmed
- [ ] Communication plan ready
- [ ] Stakeholders notified

### Launch Day

- [ ] Deploy to production
- [ ] Verify topics created
- [ ] Test producer/consumer
- [ ] Monitor metrics closely
- [ ] Ready for issues

### Post-Launch

- [ ] Monitor for 24 hours
- [ ] Review metrics
- [ ] Optimize if needed
- [ ] Document lessons learned
- [ ] Celebrate success! 🎉

## Quick Reference Card

### Critical Settings

| Component | Setting | Production Value |
|-----------|---------|------------------|
| Broker | `default.replication.factor` | 3 |
| Broker | `min.insync.replicas` | 2 |
| Broker | `unclean.leader.election.enable` | false |
| Producer | `acks` | all |
| Producer | `enable.idempotence` | true |
| Producer | `compression.type` | lz4 |
| Consumer | `enable-auto-commit` | false |
| Consumer | `isolation.level` | read_committed |

### Emergency Contacts

- **Kafka Team**: [contact info]
- **Platform Team**: [contact info]
- **On-Call Engineer**: [contact info]

### Useful Commands

```bash
# Check broker health
kafka-broker-api-versions --bootstrap-server broker:9092

# List topics
kafka-topics --list --bootstrap-server broker:9092

# Check consumer lag
kafka-consumer-groups --describe --group my-group --bootstrap-server broker:9092

# Increase partitions
kafka-topics --alter --topic my-topic --partitions 20 --bootstrap-server broker:9092
```

## Additional Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Best Practices](https://docs.confluent.io/platform/current/kafka/deployment.html)
- [LinkedIn Kafka Paper](https://engineering.linkedin.com/kafka/benchmarking-apache-kafka-2-million-writes-second-three-cheap-machines)
- [Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide/)

---

**Remember**: Production readiness is a journey, not a destination. Continuously monitor, optimize, and improve!
