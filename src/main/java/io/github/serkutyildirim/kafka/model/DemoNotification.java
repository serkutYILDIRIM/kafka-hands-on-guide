package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Notification message representing a user notification in the system.
 * Extends {@link BaseMessage} to inherit common message fields and polymorphic serialization.
 *
 * <p><b>Simpler Structure Compared to DemoTransaction:</b></p>
 * <p>Unlike {@link DemoTransaction}, this class has a simpler structure because:
 * <ul>
 *   <li>No financial calculations requiring {@link java.math.BigDecimal} precision</li>
 *   <li>No complex validation rules (e.g., currency codes, positive amounts)</li>
 *   <li>More focused domain - just delivering a message to a recipient</li>
 *   <li>Status tracking inherited from {@link BaseMessage} handles lifecycle management</li>
 * </ul>
 *
 * <p><b>When to Use Separate Message Types vs Single Unified Type:</b></p>
 * <p><b>Use Separate Types (like DemoTransaction and DemoNotification) when:</b></p>
 * <ul>
 *   <li>Different validation requirements (transactions need strict financial validation)</li>
 *   <li>Different consumer logic (transaction processors vs notification senders)</li>
 *   <li>Different SLAs and priorities (transactions may need lower latency)</li>
 *   <li>Different retention policies (transactions may need longer retention for auditing)</li>
 *   <li>Different schema evolution patterns (notifications may change more frequently)</li>
 *   <li>Clearer domain separation and type safety in code</li>
 * </ul>
 *
 * <p><b>Use Single Unified Type when:</b></p>
 * <ul>
 *   <li>Messages share most fields and business logic</li>
 *   <li>Same consumer processes all message types</li>
 *   <li>Similar SLAs and processing requirements</li>
 *   <li>Fewer message types simplifies topic management</li>
 * </ul>
 *
 * <p>In this example, we use separate types because notifications and transactions have
 * different processing requirements, SLAs, and consumer implementations.</p>
 *
 * <p><b>Example JSON Representation:</b></p>
 * <pre>
 * {
 *   "type": "DEMO_NOTIFICATION",
 *   "messageId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
 *   "timestamp": "2025-01-24T10:30:00Z",
 *   "messageType": "DEMO_NOTIFICATION",
 *   "recipientId": "user-123",
 *   "content": "Your payment of $100.50 was processed successfully",
 *   "notificationType": "PUSH",
 *   "priority": "HIGH"
 * }
 * </pre>
 *
 * <p><b>Kafka Usage Patterns:</b></p>
 * <ul>
 *   <li><b>Topic Routing:</b> Route by priority to different topics (notifications.high, notifications.standard)
 *       or by notificationType (notifications.email, notifications.sms, notifications.push)</li>
 *   <li><b>Partition Key:</b> Use {@code recipientId} to ensure all notifications to the same
 *       user are processed in order</li>
 *   <li><b>Batching:</b> Low priority notifications can be batched for efficiency</li>
 *   <li><b>Filtering:</b> Consumers can filter by notificationType to handle specific channels</li>
 * </ul>
 *
 * @see BaseMessage
 * @see NotificationType
 * @see Priority
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class DemoNotification extends BaseMessage {

    /**
     * Message type identifier for notification messages.
     * Set to constant value to identify this message as a notification.
     */
    private final String messageType = "DEMO_NOTIFICATION";

    /**
     * Recipient identifier.
     * Identifies the user or system that should receive this notification.
     * Must not be blank. Used as Kafka partition key to ensure ordering per recipient.
     */
    @NotBlank(message = "Recipient ID cannot be blank")
    private final String recipientId;

    /**
     * Notification message content.
     * The actual message to be delivered to the recipient.
     * Must not be blank.
     *
     * <p>Content format and length constraints may vary by {@link NotificationType}:
     * <ul>
     *   <li>SMS: Limited to ~160 characters</li>
     *   <li>PUSH: Limited to ~100-200 characters depending on platform</li>
     *   <li>EMAIL: Can support longer, HTML-formatted content</li>
     * </ul>
     */
    @NotBlank(message = "Content cannot be blank")
    private final String content;

    /**
     * Notification delivery channel type.
     * Determines how the notification will be delivered to the recipient.
     * Must not be null.
     *
     * <p>Different types have different characteristics:
     * <ul>
     *   <li>{@link NotificationType#EMAIL}: Slower, supports rich content</li>
     *   <li>{@link NotificationType#SMS}: Fast, plain text, character limited</li>
     *   <li>{@link NotificationType#PUSH}: Fastest, requires app installation</li>
     * </ul>
     *
     * @see NotificationType for detailed comparison
     */
    @NotNull(message = "Notification type cannot be null")
    private final NotificationType notificationType;

    /**
     * Processing priority for this notification.
     * Determines routing and processing speed.
     * Must not be null.
     *
     * <p>Priority affects:
     * <ul>
     *   <li>Topic routing (high priority may go to dedicated topic)</li>
     *   <li>Consumer resources (high priority gets more/faster consumers)</li>
     *   <li>Processing order (high priority processed before low)</li>
     *   <li>Batching strategy (low priority may be batched for efficiency)</li>
     * </ul>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@link Priority#HIGH}: Security alerts, OTP codes, payment failures</li>
     *   <li>{@link Priority#MEDIUM}: Order confirmations, transaction receipts</li>
     *   <li>{@link Priority#LOW}: Marketing emails, newsletters, digests</li>
     * </ul>
     *
     * @see Priority for routing strategy details
     */
    @NotNull(message = "Priority cannot be null")
    private final Priority priority;
}
