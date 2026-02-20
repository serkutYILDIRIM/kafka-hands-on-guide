package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
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
     * @param records List of ConsumerRecord containing messages with metadata
     * @param acknowledgment The acknowledgment callback
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_NOTIFICATIONS_TOPIC,
        groupId = "batch-consumer-group",
        containerFactory = "kafkaListenerContainerFactory",
        batch = "true"
    )
    public void consumeBatch(
            List<ConsumerRecord<String, Object>> records,
            Acknowledgment acknowledgment) {

        logger.info("[BatchConsumer] Received batch of {} messages", records.size());

        try {
            // TODO: Add batch processing logic
            for (int i = 0; i < records.size(); i++) {
                ConsumerRecord<String, Object> record = records.get(i);
                Object message = record.value();
                int partition = record.partition();
                long offset = record.offset();

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
