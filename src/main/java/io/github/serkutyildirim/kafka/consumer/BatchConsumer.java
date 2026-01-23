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

import java.util.List;

/**
 * Batch Kafka consumer implementation.
 * 
 * Demonstrates batch message consumption with:
 * - Processing multiple messages at once
 * - Improved throughput for high-volume scenarios
 * - Batch acknowledgment
 * - Efficient database operations (batch insert/update)
 * 
 * TODO: Implement batch processing logic
 * TODO: Add batch size configuration
 * TODO: Add batch validation
 * TODO: Implement partial batch failure handling
 * 
 * @author Serkut Yıldırım
 */
@Component
public class BatchConsumer {

    private static final Logger logger = LoggerFactory.getLogger(BatchConsumer.class);

    /**
     * Consume messages in batches from notification topic
     * Processes multiple messages at once for better performance
     * 
     * @param messages List of consumed messages
     * @param acknowledgment The acknowledgment callback
     * @param partitions List of partitions
     * @param offsets List of offsets
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_NOTIFICATIONS_TOPIC,
        groupId = "batch-consumer-group",
        containerFactory = "kafkaListenerContainerFactory",
        batch = "true"
    )
    public void consumeBatch(
            @Payload List<Object> messages,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets) {
        
        logger.info("[BatchConsumer] Received batch of {} messages", messages.size());
        
        try {
            // TODO: Add batch processing logic
            for (int i = 0; i < messages.size(); i++) {
                Object message = messages.get(i);
                int partition = partitions.get(i);
                long offset = offsets.get(i);
                
                logger.debug("[BatchConsumer] Processing message {} from partition {} at offset {}", 
                    i + 1, partition, offset);
                
                // Process individual message
                // processMessage(message);
            }
            
            // TODO: Perform batch operation (e.g., bulk database insert)
            // bulkInsertToDatabase(messages);
            
            // Acknowledge entire batch after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                logger.info("[BatchConsumer] Batch acknowledged successfully");
            }
            
        } catch (Exception e) {
            logger.error("[BatchConsumer] Error processing batch: {}", e.getMessage());
            // TODO: Implement batch error handling
            // Option 1: Don't acknowledge (entire batch will be reprocessed)
            // Option 2: Process messages individually to identify failing ones
            // Option 3: Send entire batch to DLQ
        }
    }

    /**
     * Process messages in smaller sub-batches
     * Useful when full batch size is too large to process at once
     * 
     * @param messages List of messages to process
     * @param batchSize Size of sub-batches
     */
    private void processInSubBatches(List<Object> messages, int batchSize) {
        // TODO: Implement sub-batch processing logic
        logger.debug("Processing {} messages in sub-batches of {}", messages.size(), batchSize);
    }

}
