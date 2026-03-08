package io.github.serkutyildirim.kafka.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the learning project's Kafka topics programmatically at application startup.
 *
 * <p><b>Topic design principles:</b></p>
 * <ul>
 *   <li>Keep topic purpose narrow and explicit so producers and consumers share a clear contract.</li>
 *   <li>Choose partition counts based on expected throughput, parallelism, and ordering requirements.</li>
 *   <li>Prefer descriptive names such as {@code demo-messages} and {@code demo-dlq} so topic intent is obvious in logs and dashboards.</li>
 * </ul>
 *
 * <p><b>Partition strategy considerations:</b></p>
 * <ul>
 *   <li>More partitions improve horizontal consumer parallelism and throughput, but they also increase broker metadata, open files, and rebalance cost.</li>
 *   <li>A single partition gives the simplest mental model and strict ordering, but it caps throughput to one active consumer per consumer group.</li>
 *   <li>Partition count sets the upper bound for consumer concurrency inside one group.</li>
 * </ul>
 *
 * <p><b>Replication and fault tolerance:</b></p>
 * <ul>
 *   <li>Replication factor {@code 1} is fine for local learning because it keeps setup simple and works with a single broker.</li>
 *   <li>Production clusters usually prefer replication factor {@code 3} so one broker failure does not make the topic unavailable.</li>
 *   <li>Higher replication improves resilience but uses more storage and network bandwidth.</li>
 * </ul>
 *
 * <p><b>Naming conventions:</b></p>
 * <ul>
 *   <li>Use lowercase, hyphen-separated names to stay shell-friendly and easy to scan.</li>
 *   <li>Reserve suffixes like {@code -dlq} for operational patterns so teams can spot retry/error topics quickly.</li>
 * </ul>
 *
 * <p><b>Programmatic vs manual topic creation:</b></p>
 * <ul>
 *   <li>Programmatic creation is great for demos, tests, and local development because the environment becomes reproducible.</li>
 *   <li>Manual or platform-managed creation is often preferred in production so retention, ACLs, quotas, and ownership are reviewed centrally.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class KafkaTopicConfig {

    public static final String DEMO_MESSAGES_TOPIC = "demo-messages";
    public static final String DEMO_NOTIFICATIONS_TOPIC = "demo-notifications";
    public static final String DEMO_PRIORITY_TOPIC = "demo-priority";
    public static final String DEMO_DLQ_TOPIC = "demo-dlq";

    /**
     * Legacy constant kept for other learning examples that still reference a transaction-specific topic name.
     * Topic creation for this exercise focuses on the four topics requested above.
     */
    public static final String DEMO_TRANSACTIONS_TOPIC = "demo-transactions";

    @Bean
    public NewTopic demoMessagesTopic() {
        log.info("Creating topic bean for {}", DEMO_MESSAGES_TOPIC);
        return TopicBuilder.name(DEMO_MESSAGES_TOPIC)
                // 3 partitions allow up to 3 consumers in the same group to work in parallel.
                // This is a good teaching default because it demonstrates partition-based scaling without overcomplicating local setup.
                // Remember: partition count determines the maximum consumer parallelism for one consumer group.
                .partitions(3)
                // Replication factor 1 is enough for local development with a single Kafka broker.
                // In production, use 3 replicas in most cases so the topic stays available during broker failure.
                .replicas(1)
                // Retention is left at the broker default here, which is commonly 7 days in Kafka.
                .build();
    }

    @Bean
    public NewTopic demoNotificationsTopic() {
        log.info("Creating topic bean for {}", DEMO_NOTIFICATIONS_TOPIC);
        return TopicBuilder.name(DEMO_NOTIFICATIONS_TOPIC)
                // Fewer partitions make sense when throughput is moderate and operational simplicity matters more than scale.
                // Notification-style workloads often prioritize easy reasoning and smaller rebalance overhead over maximum concurrency.
                .partitions(2)
                // Still 1 replica for local development; raise this in production when availability matters.
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoPriorityTopic() {
        log.info("Creating topic bean for {}", DEMO_PRIORITY_TOPIC);
        return TopicBuilder.name(DEMO_PRIORITY_TOPIC)
                // A single partition guarantees strict ordering for messages in this topic.
                // That's useful when downstream logic must observe events exactly in the order they were produced.
                .partitions(1)
                // Single replica keeps the local demo lightweight; production fault tolerance would need more replicas.
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoDlqTopic() {
        log.info("Creating topic bean for {}", DEMO_DLQ_TOPIC);
        return TopicBuilder.name(DEMO_DLQ_TOPIC)
                // DLQs are usually low-volume operational topics, so one partition is often enough for investigation and replay.
                // The dead-letter queue pattern isolates poison messages so normal consumers can keep making progress.
                // It also preserves failed events for later inspection, alerting, or controlled reprocessing.
                .partitions(1)
                // Single replica is acceptable for local labs; production DLQs often deserve the same protection level as primary business topics.
                .replicas(1)
                .build();
    }

    @PostConstruct
    public void logTopicSummary() {
        log.info("Kafka topic summary -> messages: {} (3 partitions), notifications: {} (2 partitions), priority: {} (1 partition), dlq: {} (1 partition)",
                DEMO_MESSAGES_TOPIC,
                DEMO_NOTIFICATIONS_TOPIC,
                DEMO_PRIORITY_TOPIC,
                DEMO_DLQ_TOPIC);
        log.debug("Local development uses replication factor 1 for all demo topics; production environments usually increase this to 3.");
    }
}
