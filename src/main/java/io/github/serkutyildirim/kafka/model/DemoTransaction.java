package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Transaction message representing a financial transaction in the system.
 * Extends {@link BaseMessage} to inherit common message fields and polymorphic serialization.
 *
 * <p><b>Design Decisions:</b></p>
 * <ul>
 *   <li><b>BigDecimal for Money:</b> Using {@link BigDecimal} instead of {@code double} or {@code float}
 *       prevents floating-point precision errors that are critical in financial calculations.
 *       For example: {@code 0.1 + 0.2 = 0.30000000000000004} in floating-point, but
 *       {@code BigDecimal("0.1").add(BigDecimal("0.2")) = 0.3} exactly.
 *       BigDecimal ensures precision for currency calculations, regulatory compliance, and audit trails.</li>
 *
 *   <li><b>Status Tracking:</b> The {@link MessageStatus} field enables tracking transaction lifecycle
 *       in event-driven systems. This is crucial for:
 *       <ul>
 *         <li>Idempotency - detecting duplicate processing attempts</li>
 *         <li>Error recovery - identifying failed transactions for retry</li>
 *         <li>Audit trails - maintaining complete transaction history</li>
 *         <li>Monitoring - alerting on stuck or failed transactions</li>
 *       </ul></li>
 *
 *   <li><b>Field Validation:</b> Bean Validation annotations ensure data integrity before messages
 *       enter the Kafka pipeline. Catching validation errors early prevents:
 *       <ul>
 *         <li>Invalid messages polluting topics</li>
 *         <li>Consumer crashes due to malformed data</li>
 *         <li>Silent data corruption in downstream systems</li>
 *         <li>Difficult debugging across distributed services</li>
 *       </ul>
 *       In distributed systems, validation at message boundaries is critical for system resilience.</li>
 * </ul>
 *
 * <p><b>Example JSON Representation:</b></p>
 * <pre>
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
 * </pre>
 *
 * <p><b>Kafka Usage Patterns:</b></p>
 * <ul>
 *   <li><b>Partition Key:</b> Use {@code sourceId} as partition key to ensure all transactions
 *       from the same source account are processed in order on the same partition</li>
 *   <li><b>Idempotent Producer:</b> Use {@code messageId} (UUID) to detect duplicates and
 *       enable exactly-once semantics</li>
 *   <li><b>Status Updates:</b> Consumers should update {@code status} field and produce
 *       status change events to audit topic</li>
 * </ul>
 *
 * @see BaseMessage
 * @see MessageStatus
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class DemoTransaction extends BaseMessage {

    /**
     * Message type identifier for transaction messages.
     * Set to constant value to identify this message as a transaction.
     */
    private final String messageType = "DEMO_TRANSACTION";

    /**
     * Source account identifier.
     * Represents the account from which funds are transferred.
     * Must not be blank. Used as Kafka partition key to ensure ordering.
     */
    @NotBlank(message = "Source ID cannot be blank")
    private final String sourceId;

    /**
     * Target account identifier.
     * Represents the account to which funds are transferred.
     * Must not be blank.
     */
    @NotBlank(message = "Target ID cannot be blank")
    private final String targetId;

    /**
     * Transaction amount.
     * Uses {@link BigDecimal} to avoid floating-point precision errors in financial calculations.
     * Must be positive and not null.
     *
     * <p><b>Why BigDecimal:</b></p>
     * <pre>
     * // Floating-point error example:
     * double result = 0.1 + 0.2;  // 0.30000000000000004 (WRONG!)
     *
     * // BigDecimal precision:
     * BigDecimal result = new BigDecimal("0.1").add(new BigDecimal("0.2"));  // 0.3 (CORRECT!)
     * </pre>
     */
    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private final BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., USD, EUR, GBP, TRY).
     * Must be exactly 3 uppercase letters following ISO 4217 standard.
     * Ensures consistent currency representation across the system.
     *
     * <p>Validation ensures format correctness. Currency existence should be validated
     * against a currency reference service in business logic.</p>
     */
    @NotBlank(message = "Currency cannot be blank")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g., USD, EUR)")
    private final String currency;

    /**
     * Processing status of the transaction.
     * Tracks the message lifecycle through the Kafka pipeline.
     * Must not be null. See {@link MessageStatus} for state transition diagram.
     *
     * <p><b>Status Tracking Importance in Event-Driven Systems:</b></p>
     * <ul>
     *   <li>Enables idempotent processing by checking current status before processing</li>
     *   <li>Supports retry logic by identifying FAILED transactions</li>
     *   <li>Facilitates monitoring and alerting on stuck transactions</li>
     *   <li>Creates audit trail for compliance and debugging</li>
     * </ul>
     */
    @NotNull(message = "Status cannot be null")
    private final MessageStatus status;

    /**
     * Optional human-readable description of the transaction.
     * Can contain additional context like order number, invoice reference, etc.
     * Not validated as it's optional and free-form.
     */
    private final String description;
}
