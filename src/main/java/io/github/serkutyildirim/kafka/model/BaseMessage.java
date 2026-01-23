package io.github.serkutyildirim.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base message class for all Kafka messages in the application.
 * 
 * Provides common fields that all messages should have:
 * - Unique message ID
 * - Timestamp
 * - Message status
 * - Source information
 * 
 * TODO: Extend this class for specific message types
 * 
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the message
     */
    private String messageId;

    /**
     * Timestamp when the message was created
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Current status of the message
     */
    private MessageStatus status;

    /**
     * Source system or component that created the message
     */
    private String source;

    /**
     * Constructor that initializes default values
     */
    protected BaseMessage(String source) {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.status = MessageStatus.PENDING;
        this.source = source;
    }

}
