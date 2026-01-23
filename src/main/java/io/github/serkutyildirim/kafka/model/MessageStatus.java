package io.github.serkutyildirim.kafka.model;

/**
 * Enum representing the status of a message.
 * 
 * Used to track message lifecycle and processing state.
 * 
 * @author Serkut Yıldırım
 */
public enum MessageStatus {
    /**
     * Message has been created but not yet sent
     */
    PENDING,
    
    /**
     * Message has been sent to Kafka
     */
    SENT,
    
    /**
     * Message has been successfully delivered and acknowledged
     */
    DELIVERED,
    
    /**
     * Message is currently being processed by a consumer
     */
    PROCESSING,
    
    /**
     * Message has been successfully processed
     */
    COMPLETED,
    
    /**
     * Message processing failed
     */
    FAILED,
    
    /**
     * Message has been sent to dead letter queue
     */
    DLQ
}
