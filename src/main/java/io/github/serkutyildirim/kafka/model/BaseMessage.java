package io.github.serkutyildirim.kafka.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all Kafka messages in the system.
 * Provides common fields and polymorphic serialization support for event-driven architectures.
 *
 * <p><b>Design Decisions:</b></p>
 * <ul>
 *   <li><b>UUID for Message IDs:</b> Ensures global uniqueness across distributed systems without
 *       coordination. UUIDs are collision-resistant and can be generated independently by any service,
 *       making them ideal for Kafka's distributed nature where multiple producers may generate
 *       messages simultaneously.</li>
 *   <li><b>Instant over LocalDateTime:</b> {@link Instant} represents a point in time in UTC,
 *       making it timezone-agnostic. This is critical in distributed systems where services may run
 *       in different timezones. {@link Instant} also maps directly to Unix timestamps, simplifying
 *       integration with Kafka's timestamp metadata.</li>
 *   <li><b>Polymorphic Serialization:</b> Using Jackson's {@code @JsonTypeInfo} and {@code @JsonSubTypes}
 *       enables storing the message type directly in the JSON payload. This allows consumers to
 *       deserialize messages to the correct subclass without external type information, supporting
 *       schema evolution and multiple message types on the same topic.</li>
 * </ul>
 *
 * <p><b>Kafka Serialization Considerations:</b></p>
 * <ul>
 *   <li>The {@code type} field in JSON identifies the concrete message class</li>
 *   <li>Supports schema evolution - new fields can be added to subclasses without breaking existing consumers</li>
 *   <li>Compatible with Kafka's default JsonSerializer/JsonDeserializer</li>
 *   <li>All timestamps are in UTC to avoid timezone conversion issues across services</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Creating a transaction message
 * DemoTransaction transaction = DemoTransaction.builder()
 *     .sourceId("ACC-001")
 *     .targetId("ACC-002")
 *     .amount(new BigDecimal("100.50"))
 *     .currency("USD")
 *     .status(MessageStatus.CREATED)
 *     .description("Payment for order #12345")
 *     .build();
 *
 * // The message will automatically have:
 * // - messageId: auto-generated UUID (e.g., "550e8400-e29b-41d4-a716-446655440000")
 * // - timestamp: current Instant (e.g., "2025-01-24T10:30:00Z")
 * // - messageType: "DEMO_TRANSACTION"
 *
 * // JSON representation:
 * // {
 * //   "type": "DEMO_TRANSACTION",
 * //   "messageId": "550e8400-e29b-41d4-a716-446655440000",
 * //   "timestamp": "2025-01-24T10:30:00Z",
 * //   "messageType": "DEMO_TRANSACTION",
 * //   "sourceId": "ACC-001",
 * //   "targetId": "ACC-002",
 * //   "amount": 100.50,
 * //   "currency": "USD",
 * //   "status": "CREATED",
 * //   "description": "Payment for order #12345"
 * // }
 * }</pre>
 *
 * @see DemoTransaction
 * @see DemoNotification
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = DemoTransaction.class, name = "DEMO_TRANSACTION"),
    @JsonSubTypes.Type(value = DemoNotification.class, name = "DEMO_NOTIFICATION")
})
public abstract class BaseMessage {

    /**
     * Unique identifier for this message.
     * Generated automatically using UUID version 4 (random).
     * Ensures global uniqueness across all services and Kafka partitions.
     */
    @Builder.Default
    private UUID messageId = UUID.randomUUID();

    /**
     * Creation timestamp in UTC.
     * Uses {@link Instant} for timezone-agnostic time representation.
     * Set automatically to the current time when the message is created.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Type identifier for this message.
     * Set by subclasses to identify the concrete message type.
     * Used for routing, filtering, and consumer logic.
     */
    private String messageType;
}
