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
 * Manual acknowledgment Kafka consumer implementation.
 * 
 * Demonstrates manual offset management with:
 * - Manual acknowledgment mode
 * - Explicit offset commit control
 * - Better error handling capabilities
 * - At-least-once delivery guarantee
 * 
 * TODO: Implement manual acknowledgment logic
 * TODO: Add conditional acknowledgment based on processing result
 * TODO: Add error handling with manual offset management
 * TODO: Implement retry logic before acknowledgment
 * 
 * @author Serkut Yıldırım
 */
@Component
public class ManualAckConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ManualAckConsumer.class);

    /**
     * Consume messages with manual acknowledgment
     * Only acknowledges (commits offset) after successful processing
     * 
     * @param message The consumed message
     * @param acknowledgment The acknowledgment callback
     * @param partition The partition from which message was consumed
     * @param offset The offset of the message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
        groupId = "manual-ack-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWithManualAck(
            @Payload Object message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("[ManualAckConsumer] Received message from partition {} at offset {}", partition, offset);
        
        try {
            // TODO: Add business logic to process the message
            logger.debug("[ManualAckConsumer] Processing message: {}", message);
            
            // Simulate processing
            // processMessage(message);
            
            // TODO: Only acknowledge after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                logger.info("[ManualAckConsumer] Message acknowledged at offset {}", offset);
            }
            
        } catch (Exception e) {
            logger.error("[ManualAckConsumer] Error processing message at offset {}: {}", offset, e.getMessage());
            // TODO: Implement error handling strategy
            // Option 1: Don't acknowledge (message will be reprocessed)
            // Option 2: Send to DLQ and acknowledge
            // Option 3: Retry with backoff
        }
    }

    /**
     * Process a message (placeholder for business logic)
     * 
     * @param message The message to process
     */
    private void processMessage(Object message) {
        // TODO: Implement actual business logic
        logger.debug("Processing message: {}", message);
    }

}
