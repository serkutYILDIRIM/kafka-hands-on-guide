package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Transaction message representing a financial transfer event.
 * It extends {@link BaseMessage} so the payload carries both business data and Kafka-friendly metadata.
 *
 * <p><b>Why BigDecimal for money?</b></p>
 * <ul>
 *   <li>{@link BigDecimal} avoids floating-point precision errors that are unacceptable in financial domains.</li>
 *   <li>That makes totals, fees, reconciliation, and audits deterministic across producers and consumers.</li>
 *   <li>In event-driven systems, an imprecise amount can fan out into multiple downstream inconsistencies, so exact arithmetic matters.</li>
 * </ul>
 *
 * <p><b>Why track status on the event?</b></p>
 * <ul>
 *   <li>{@link MessageStatus} helps consumers model processing progress explicitly.</li>
 *   <li>It supports retries, dead-letter routing, observability, audit trails, and idempotent workflows.</li>
 *   <li>Publishing status-aware events makes failure handling easier to understand across distributed services.</li>
 * </ul>
 *
 * <p><b>Why validate fields at the message boundary?</b></p>
 * <ul>
 *   <li>Kafka topics are shared contracts, so invalid data can quickly affect multiple consumers.</li>
 *   <li>Early validation prevents malformed events from spreading through a distributed system.</li>
 *   <li>That reduces poison messages, replay noise, debugging cost, and downstream data corruption.</li>
 * </ul>
 *
 * <p><b>Kafka serialization considerations:</b></p>
 * <ul>
 *   <li>The builder keeps creation concise while still auto-populating shared metadata.</li>
 *   <li>Enum values serialize to stable strings, which is easy for non-Java consumers to read.</li>
 *   <li>Validation annotations document the event contract close to the payload itself.</li>
 * </ul>
 *
 * <p><b>Example JSON representation:</b></p>
 * <pre>{@code
 * {
 *   "type": "DEMO_TRANSACTION",
 *   "messageId": "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp": "2025-01-24T10:30:00Z",
 *   "messageType": "DEMO_TRANSACTION",
 *   "sourceId": "ACC-001",
 *   "targetId": "ACC-002",
 *   "amount": 100.50,
 *   "currency": "USD",
 *   "status": "CREATED",
 *   "description": "Payment for order #12345"
 * }
 * }</pre>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@Jacksonized
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class DemoTransaction extends BaseMessage {

    private static final String MESSAGE_TYPE = "DEMO_TRANSACTION";

    {
        initializeMessageType(MESSAGE_TYPE);
    }

    /**
     * Source account identifier.
     * Not blank and size-limited so invalid routing keys do not enter Kafka topics.
     */
    @NotBlank(message = "Source ID cannot be blank")
    @Size(max = 64, message = "Source ID must be at most 64 characters")
    private final String sourceId;

    /**
     * Target account identifier.
     * Validation is important in distributed systems because bad identifiers become expensive to repair after fan-out.
     */
    @NotBlank(message = "Target ID cannot be blank")
    @Size(max = 64, message = "Target ID must be at most 64 characters")
    private final String targetId;

    /**
     * Positive transaction amount.
     * A non-positive value would represent an invalid financial event for this example domain.
     */
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private final BigDecimal amount;

    /**
     * ISO 4217 currency code such as USD, EUR, or TRY.
     * Structural validation catches malformed currency values before they spread to consumers.
     */
    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g., USD, EUR)")
    private final String currency;

    /**
     * Processing status for the event lifecycle.
     */
    @NotNull(message = "Status cannot be null")
    private final MessageStatus status;

    /**
     * Optional human-readable description for operators and consumers.
     */
    @Size(max = 255, message = "Description must be at most 255 characters")
    private final String description;
}
