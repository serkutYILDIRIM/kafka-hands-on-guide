package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Async Kafka producer implementation.
 * 
 * Demonstrates asynchronous message sending with:
 * - Non-blocking send operations
 * - Callback handling for success/failure
 * - Better performance for high-throughput scenarios
 * - Proper error handling with callbacks
 * 
 * TODO: Implement async send with callbacks
 * TODO: Add success callback handling
 * TODO: Add failure callback handling
 * TODO: Add metrics tracking for async operations
 * 
 * @author Serkut Yıldırım
 */
@Component
public class AsyncProducer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AsyncProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send a message asynchronously with callback handling
     * 
     * @param key The message key for partitioning
     * @param message The message to send
     */
    public void sendMessageAsync(String key, Object message) {
        logger.info("Sending message with AsyncProducer");
        
        // TODO: Implement async send with CompletableFuture
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(KafkaTopicConfig.DEMO_MESSAGES_TOPIC, key, message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // TODO: Handle success case
                logger.info("Message sent successfully to partition {} with offset {}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                // TODO: Handle failure case
                logger.error("Failed to send message: {}", ex.getMessage());
            }
        });
    }

    /**
     * Send a message to a specific topic asynchronously
     * 
     * @param topic The target topic
     * @param key The message key
     * @param message The message to send
     */
    public void sendToTopicAsync(String topic, String key, Object message) {
        logger.info("Sending message to topic {} asynchronously", topic);
        // TODO: Implement async send to custom topic with callbacks
    }

}
