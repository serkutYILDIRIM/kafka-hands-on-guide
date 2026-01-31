package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Error handling Kafka consumer implementation.
 * 
 * Demonstrates comprehensive error handling with:
 * - Try-catch error handling
 * - Retry mechanism with exponential backoff
 * - Dead Letter Queue (DLQ) pattern
 * - Error logging and monitoring
 * - Graceful degradation
 * 
 * TODO: Implement retry mechanism
 * TODO: Implement DLQ producer
 * TODO: Add error classification logic
 * TODO: Add metrics for error tracking
 * 
 * @author Serkut Yıldırım
 */
@Component
public class ErrorHandlingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ErrorHandlingConsumer.class);
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 1000;

    /**
     * Consume messages with comprehensive error handling
     * 
     * @param message The consumed message
     * @param acknowledgment The acknowledgment callback
     * @param partition The partition from which message was consumed
     * @param offset The offset of the message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = "error-handling-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWithErrorHandling(
            @Payload Object message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("[ErrorHandlingConsumer] Received message from partition {} at offset {}", partition, offset);
        
        boolean processed = false;
        int attemptCount = 0;
        
        while (!processed && attemptCount < MAX_RETRY_ATTEMPTS) {
            try {
                attemptCount++;
                logger.debug("[ErrorHandlingConsumer] Processing attempt {} for message at offset {}", 
                    attemptCount, offset);
                
                // TODO: Add actual message processing logic
                processMessage(message);
                
                processed = true;
                
                // Acknowledge after successful processing
                if (acknowledgment != null) {
                    acknowledgment.acknowledge();
                    logger.info("[ErrorHandlingConsumer] Message processed and acknowledged at offset {}", offset);
                }
                
            } catch (Exception e) {
                logger.error("[ErrorHandlingConsumer] Error processing message (attempt {}): {}", 
                    attemptCount, e.getMessage());
                
                if (attemptCount < MAX_RETRY_ATTEMPTS) {
                    // TODO: Implement exponential backoff
                    try {
                        long backoff = RETRY_BACKOFF_MS * attemptCount;
                        logger.info("[ErrorHandlingConsumer] Retrying after {} ms", backoff);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[ErrorHandlingConsumer] Retry interrupted");
                        break;
                    }
                } else {
                    // Max retries exceeded - send to DLQ
                    logger.error("[ErrorHandlingConsumer] Max retries exceeded for message at offset {}", offset);
                    sendToDLQ(message, e);
                    
                    // Acknowledge to skip this message
                    if (acknowledgment != null) {
                        acknowledgment.acknowledge();
                        logger.info("[ErrorHandlingConsumer] Message sent to DLQ and acknowledged");
                    }
                }
            }
        }
    }

    /**
     * Process a message (placeholder for business logic)
     * 
     * @param message The message to process
     * @throws Exception if processing fails
     */
    private void processMessage(Object message) throws Exception {
        // TODO: Implement actual business logic
        logger.debug("Processing message: {}", message);
    }

    /**
     * Send failed message to Dead Letter Queue
     * 
     * @param message The failed message
     * @param error The error that caused the failure
     */
    private void sendToDLQ(Object message, Exception error) {
        logger.warn("[ErrorHandlingConsumer] Sending message to DLQ: {}", message);
        // TODO: Implement DLQ producer
        // TODO: Add error metadata (original topic, partition, offset, error message)
        // TODO: Use KafkaTemplate to send to DLQ topic
    }

}
