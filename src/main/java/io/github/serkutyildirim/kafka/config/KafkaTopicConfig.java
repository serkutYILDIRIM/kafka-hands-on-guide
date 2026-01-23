package io.github.serkutyildirim.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic configuration class.
 * 
 * Defines and creates Kafka topics programmatically on application startup.
 * Topics are created with specific partition counts and replication factors.
 * 
 * TODO: Implement the following topics:
 * - demo-messages: General purpose demo topic (3 partitions, RF=1)
 * - demo-transactions: Transactional messages topic (5 partitions, RF=1)
 * - demo-notifications: Notification messages topic (3 partitions, RF=1)
 * - demo-dlq: Dead Letter Queue for error handling (1 partition, RF=1)
 * 
 * @author Serkut Yıldırım
 */
@Configuration
public class KafkaTopicConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaTopicConfig.class);

    public static final String DEMO_MESSAGES_TOPIC = "demo-messages";
    public static final String DEMO_TRANSACTIONS_TOPIC = "demo-transactions";
    public static final String DEMO_NOTIFICATIONS_TOPIC = "demo-notifications";
    public static final String DEMO_DLQ_TOPIC = "demo-dlq";

    @Bean
    public NewTopic demoMessagesTopic() {
        logger.info("Creating topic: {}", DEMO_MESSAGES_TOPIC);
        return TopicBuilder.name(DEMO_MESSAGES_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoTransactionsTopic() {
        logger.info("Creating topic: {}", DEMO_TRANSACTIONS_TOPIC);
        return TopicBuilder.name(DEMO_TRANSACTIONS_TOPIC)
                .partitions(5)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoNotificationsTopic() {
        logger.info("Creating topic: {}", DEMO_NOTIFICATIONS_TOPIC);
        return TopicBuilder.name(DEMO_NOTIFICATIONS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoDlqTopic() {
        logger.info("Creating topic: {}", DEMO_DLQ_TOPIC);
        return TopicBuilder.name(DEMO_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

}
