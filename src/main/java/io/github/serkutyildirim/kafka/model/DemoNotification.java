package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Demo notification message representing a notification to be sent.
 * 
 * This class is used to demonstrate:
 * - Async producer patterns
 * - Batch consumer patterns
 * - Simple message processing
 * 
 * TODO: Add support for different notification channels (EMAIL, SMS, PUSH)
 * TODO: Implement notification priority levels
 * 
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DemoNotification extends BaseMessage {

    private static final long serialVersionUID = 1L;

    /**
     * Recipient user ID
     */
    @NotBlank(message = "Recipient ID cannot be blank")
    private String recipientId;

    /**
     * Recipient email address
     */
    @Email(message = "Invalid email format")
    private String recipientEmail;

    /**
     * Notification title/subject
     */
    @NotBlank(message = "Title cannot be blank")
    private String title;

    /**
     * Notification body/content
     */
    @NotBlank(message = "Content cannot be blank")
    private String content;

    /**
     * Notification type (e.g., INFO, WARNING, ERROR, SUCCESS)
     */
    private String notificationType;

    /**
     * Notification channel (e.g., EMAIL, SMS, PUSH)
     */
    private String channel;

    /**
     * Constructor for creating a new notification
     */
    public DemoNotification(String recipientId, String recipientEmail, String title, String content) {
        super("NOTIFICATION_SERVICE");
        this.recipientId = recipientId;
        this.recipientEmail = recipientEmail;
        this.title = title;
        this.content = content;
        this.notificationType = "INFO";
        this.channel = "EMAIL";
    }

}
