package io.github.serkutyildirim.kafka.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all Kafka message payloads in the guide.
 * It centralizes shared metadata and enables polymorphic JSON serialization for heterogeneous event streams.
 *
 * <p><b>Why UUID for message IDs?</b></p>
 * <ul>
 *   <li>{@link UUID} values can be generated independently by every producer without a shared sequence or database.</li>
 *   <li>This fits distributed Kafka deployments where many services publish concurrently across machines and regions.</li>
 *   <li>Globally unique identifiers also simplify tracing, deduplication, replay analysis, and correlation across topics.</li>
 * </ul>
 *
 * <p><b>Why Instant instead of LocalDateTime?</b></p>
 * <ul>
 *   <li>{@link Instant} represents an absolute UTC moment, so it is timezone-agnostic.</li>
 *   <li>That avoids ambiguous timestamps when producers and consumers run in different locales or daylight-saving rules.</li>
 *   <li>It also maps naturally to Kafka event timelines, observability tooling, and cross-service audit logs.</li>
 * </ul>
 *
 * <p><b>Why polymorphic serialization in Kafka?</b></p>
 * <ul>
 *   <li>The JSON {@code type} discriminator lets one topic carry multiple event shapes safely.</li>
 *   <li>Consumers can deserialize directly to the correct subclass without external routing metadata.</li>
 *   <li>This improves extensibility, makes schema evolution easier, and keeps producer/consumer contracts explicit.</li>
 * </ul>
 *
 * <p><b>Kafka serialization considerations:</b></p>
 * <ul>
 *   <li>The logical {@code messageType} field mirrors the Jackson {@code type} discriminator and helps with logging and routing.</li>
 *   <li>Auto-generated metadata reduces producer mistakes and keeps events consistent across services.</li>
 *   <li>Builder defaults are useful in distributed systems because metadata is present even when producers only supply business fields.</li>
 * </ul>
 *
 * <p><b>Usage example:</b></p>
 * <pre>{@code
 * DemoTransaction transaction = DemoTransaction.builder()
 *     .sourceId("ACC-001")
 *     .targetId("ACC-002")
 *     .amount(new BigDecimal("100.50"))
 *     .currency("USD")
 *     .status(MessageStatus.CREATED)
 *     .description("Payment for order #12345")
 *     .build();
 *
 * // Auto-populated metadata:
 * // - messageId   -> random UUID
 * // - timestamp   -> current UTC instant
 * // - messageType -> "DEMO_TRANSACTION"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DemoTransaction.class, name = "DEMO_TRANSACTION"),
    @JsonSubTypes.Type(value = DemoNotification.class, name = "DEMO_NOTIFICATION")
})
public abstract class BaseMessage {

    /**
     * Unique message identifier used for traceability, correlation, and deduplication.
     */
    @Builder.Default
    private final UUID messageId = UUID.randomUUID();

    /**
     * UTC creation time for the message.
     */
    @Builder.Default
    private final Instant timestamp = Instant.now();

    /**
     * Logical message type set by subclasses.
     * We avoid exposing a public setter so message identity stays stable after creation.
     */
    @Setter(AccessLevel.NONE)
    private String messageType;

    /**
     * Initializes the logical message type from a subclass-specific constant.
     */
    @SuppressWarnings("unused")
    protected void initializeMessageType(String messageType) {
        this.messageType = messageType;
    }
}
