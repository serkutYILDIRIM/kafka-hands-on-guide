package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Simple Kafka consumer implementation.
 * 
 * Demonstrates the most basic way to consume messages from Kafka:
 * - Auto-commit offset mode
 * - Single message processing
 * - Basic error handling
 * - Suitable for simple use cases
 * 
 * TODO: Add message processing logic
 * TODO: Add error handling
 * TODO: Add message validation
 * 
 * @author Serkut Yıldırım
 */
@Component
public class SimpleConsumer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleConsumer.class);

    /**
     * Consume messages from demo-messages topic
     * Auto-commit is disabled in application.yml, but this consumer demonstrates
     * the simplest consumption pattern
     * 
     * @param message The consumed message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = "simple-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMessage(DemoTransaction message) {
        logger.info("[SimpleConsumer] Received message: ID={}, Source={}, Target={}, Amount={}",
            message.getMessageId(),
            message.getSourceId(),
            message.getTargetId(),
            message.getAmount());
        
        // TODO: Add business logic to process the message
        // TODO: Add validation
        // TODO: Add error handling
        
        logger.debug("[SimpleConsumer] Message processed successfully: {}", message.getMessageId());
    }

    /**
     * Consume notification messages
     * 
     * @param message The consumed notification
     */
    // TODO: Add listener for demo-notifications topic
    // TODO: Use different consumer group

}
