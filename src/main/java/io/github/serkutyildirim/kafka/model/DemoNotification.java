package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Notification message representing a lightweight outbound communication event.
 * Compared with {@link DemoTransaction}, this model is intentionally simpler because it does not carry monetary data,
 * currency rules, or transfer-specific business semantics.
 *
 * <p><b>Why is this structure simpler than DemoTransaction?</b></p>
 * <ul>
 *   <li>Notifications mostly need recipient, content, channel, and urgency information.</li>
 *   <li>There is no financial precision concern like {@link java.math.BigDecimal} handling.</li>
 *   <li>The validation rules are simpler, which makes the payload easier for generic notification consumers to process.</li>
 * </ul>
 *
 * <p><b>When to use separate message types instead of one unified type?</b></p>
 * <ul>
 *   <li>Use separate types when domains have different validation rules, lifecycles, SLAs, or consumers.</li>
 *   <li>Use a unified type only when payload structure and processing semantics are nearly identical.</li>
 *   <li>In Kafka, separate types improve type safety, observability, and evolution of independent schemas.</li>
 * </ul>
 *
 * <p><b>Kafka serialization considerations:</b></p>
 * <ul>
 *   <li>Enums serialize as readable strings, which keeps topic payloads easy to inspect.</li>
 *   <li>Priority and channel fields are useful for topic routing, consumer filtering, and alerting strategies.</li>
 *   <li>Validation matters in distributed systems because invalid notifications can be replicated, retried, and dead-lettered many times.</li>
 * </ul>
 *
 * <p><b>Example JSON representation:</b></p>
 * <pre>{@code
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
 * }</pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Jacksonized
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class DemoNotification extends BaseMessage {

    private static final String MESSAGE_TYPE = "DEMO_NOTIFICATION";

    {
        initializeMessageType(MESSAGE_TYPE);
    }

    /**
     * Recipient identifier used for routing and per-user ordering.
     */
    @NotBlank(message = "Recipient ID cannot be blank")
    @Size(max = 64, message = "Recipient ID must be at most 64 characters")
    private final String recipientId;

    /**
     * Message content to deliver.
     * Size limits help keep payloads practical for Kafka transport and downstream delivery channels.
     */
    @NotBlank(message = "Content cannot be blank")
    @Size(max = 500, message = "Content must be at most 500 characters")
    private final String content;

    /**
     * Delivery channel enum.
     */
    @NotNull(message = "Notification type cannot be null")
    private final NotificationType notificationType;

    /**
     * Processing priority used for routing and resource allocation.
     */
    @NotNull(message = "Priority cannot be null")
    private final Priority priority;
}
