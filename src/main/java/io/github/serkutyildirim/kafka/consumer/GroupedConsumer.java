package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Grouped Kafka consumer implementation.
 * 
 * Demonstrates consumer group concepts with:
 * - Multiple consumers in the same group (load balancing)
 * - Multiple consumers in different groups (pub-sub pattern)
 * - Partition rebalancing
 * - Consumer group coordination
 * 
 * TODO: Implement multiple consumer instances simulation
 * TODO: Add partition assignment logging
 * TODO: Add rebalance listener
 * TODO: Document consumer group behavior
 * 
 * @author Serkut Yıldırım
 */
@Component
public class GroupedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(GroupedConsumer.class);

    /**
     * Consumer instance 1 in group-a
     * When running multiple application instances, partitions will be distributed
     * 
     * @param message The consumed message
     * @param partition The partition from which message was consumed
     * @param offset The offset of the message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
        groupId = "grouped-consumer-group-a",
        containerFactory = "kafkaListenerContainerFactory",
        id = "consumer-a-1"
    )
    public void consumeGroupA1(
            @Payload Object message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("[GroupA-Consumer1] Received message from partition {} at offset {}", partition, offset);
        // TODO: Add processing logic
        logger.debug("[GroupA-Consumer1] Processing: {}", message);
    }

    /**
     * Consumer instance 2 in group-a (same group as above)
     * Will receive different partitions than consumer-a-1
     * 
     * @param message The consumed message
     * @param partition The partition from which message was consumed
     * @param offset The offset of the message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
        groupId = "grouped-consumer-group-a",
        containerFactory = "kafkaListenerContainerFactory",
        id = "consumer-a-2"
    )
    public void consumeGroupA2(
            @Payload Object message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("[GroupA-Consumer2] Received message from partition {} at offset {}", partition, offset);
        // TODO: Add processing logic
        logger.debug("[GroupA-Consumer2] Processing: {}", message);
    }

    /**
     * Consumer in group-b (different group)
     * Will receive ALL messages (different offset tracking)
     * Demonstrates pub-sub pattern when multiple groups listen to same topic
     * 
     * @param message The consumed message
     * @param partition The partition from which message was consumed
     * @param offset The offset of the message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_TRANSACTIONS_TOPIC,
        groupId = "grouped-consumer-group-b",
        containerFactory = "kafkaListenerContainerFactory",
        id = "consumer-b-1"
    )
    public void consumeGroupB1(
            @Payload Object message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("[GroupB-Consumer1] Received message from partition {} at offset {}", partition, offset);
        // TODO: Add different processing logic for group B
        logger.debug("[GroupB-Consumer1] Processing: {}", message);
    }

    // TODO: Add ConsumerRebalanceListener to track partition assignment changes
    // TODO: Add method to log current partition assignments
    // TODO: Document how to run multiple instances to see load balancing

}
