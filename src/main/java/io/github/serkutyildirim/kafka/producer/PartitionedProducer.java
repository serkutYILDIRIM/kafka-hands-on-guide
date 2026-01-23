package io.github.serkutyildirim.kafka.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Partitioned Kafka producer implementation.
 * 
 * Demonstrates partition-aware message sending with:
 * - Custom partition key selection
 * - Explicit partition targeting
 * - Partition ordering guarantees
 * - Load balancing across partitions
 * 
 * TODO: Implement send with partition key
 * TODO: Implement send to specific partition
 * TODO: Add custom partitioner logic
 * TODO: Add partition selection strategy documentation
 * 
 * @author Serkut Yıldırım
 */
@Component
public class PartitionedProducer {

    private static final Logger logger = LoggerFactory.getLogger(PartitionedProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PartitionedProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send a message with a partition key
     * Messages with the same key will go to the same partition (ordering guaranteed)
     * 
     * @param topic The target topic
     * @param partitionKey The key to determine partition
     * @param message The message to send
     */
    public void sendWithPartitionKey(String topic, String partitionKey, Object message) {
        logger.info("Sending message with partition key: {}", partitionKey);
        // TODO: Implement send with partition key
        // TODO: Log which partition the message is sent to
        kafkaTemplate.send(topic, partitionKey, message);
    }

    /**
     * Send a message to a specific partition
     * 
     * @param topic The target topic
     * @param partition The target partition number
     * @param key The message key
     * @param message The message to send
     */
    public void sendToPartition(String topic, int partition, String key, Object message) {
        logger.info("Sending message to topic {} partition {}", topic, partition);
        // TODO: Implement send to specific partition
        // TODO: Add partition validation
        kafkaTemplate.send(topic, partition, key, message);
    }

    /**
     * Send a message with custom partition selection logic
     * 
     * @param topic The target topic
     * @param message The message to send
     * @param totalPartitions Total number of partitions in the topic
     */
    public void sendWithCustomPartitioning(String topic, Object message, int totalPartitions) {
        logger.info("Sending message with custom partitioning logic");
        // TODO: Implement custom partition selection
        // TODO: Example: round-robin, hash-based, or business logic based
    }

}
