package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Simple Kafka producer implementation.
 * 
 * Demonstrates the most basic way to send messages to Kafka:
 * - Fire-and-forget pattern
 * - No callback handling
 * - Suitable for non-critical messages
 * 
 * TODO: Implement message sending logic
 * TODO: Add basic error logging
 * TODO: Add method to send with custom partition key
 * 
 * @author Serkut Yıldırım
 */
@Component
public class SimpleProducer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SimpleProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send a message to Kafka without waiting for response (fire-and-forget)
     * 
     * @param message The message to send
     */
    public void sendMessage(DemoTransaction message) {
        logger.info("Sending message with SimpleProducer: {}", message.getMessageId());
        // TODO: Implement kafka send logic
        kafkaTemplate.send(KafkaTopicConfig.DEMO_MESSAGES_TOPIC, message.getSourceId(), message);
    }

    /**
     * Send a message to a specific topic
     * 
     * @param topic The target topic
     * @param message The message to send
     */
    public void sendToTopic(String topic, Object message) {
        logger.info("Sending message to topic {}", topic);
        // TODO: Implement send to custom topic logic
        kafkaTemplate.send(topic, message);
    }

}
