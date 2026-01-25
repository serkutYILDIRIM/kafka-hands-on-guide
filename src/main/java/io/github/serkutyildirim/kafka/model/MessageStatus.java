package io.github.serkutyildirim.kafka.model;

/**
 * Enum representing the processing status of a message in the Kafka ecosystem.
 * Tracks message lifecycle from creation through completion or failure.
 *
 * <p><b>State Transition Diagram:</b></p>
 * <pre>
 * Success Path:
 *   CREATED → PROCESSING → COMPLETED
 *
 * Retry Path (Transient Failures):
 *   CREATED → PROCESSING → FAILED → RETRY → PROCESSING → COMPLETED
 *                           ↑                    ↓
 *                           └────────────────────┘
 *                           (can retry multiple times)
 *
 * Dead Letter Path (Permanent Failures):
 *   CREATED → PROCESSING → FAILED → DEAD_LETTER
 *                           ↓
 *                    (after max retries exceeded
 *                     or non-retryable error)
 * </pre>
 *
 * <p><b>Integration with Kafka Error Handling:</b></p>
 * <ul>
 *   <li><b>CREATED:</b> Initial state when message is produced to Kafka. Used for tracking
 *       message origin and creation context.</li>
 *   <li><b>PROCESSING:</b> Consumer has pulled the message and is actively processing it.
 *       Important for monitoring in-flight messages and detecting stuck processing.</li>
 *   <li><b>COMPLETED:</b> Processing succeeded. Message can be archived or marked for cleanup.</li>
 *   <li><b>FAILED:</b> Processing failed due to transient or permanent error. Triggers
 *       retry logic or dead letter routing based on error type and retry count.</li>
 *   <li><b>RETRY:</b> Message is queued for retry after a transient failure (e.g., network timeout,
 *       temporary service unavailability). Typically combined with exponential backoff.</li>
 *   <li><b>DEAD_LETTER:</b> Message exhausted all retry attempts or encountered a non-retryable
 *       error (e.g., schema validation failure, malformed data). Sent to dead letter topic
 *       for manual investigation and recovery.</li>
 * </ul>
 *
 * <p><b>Best Practices:</b></p>
 * <ul>
 *   <li>Store status changes in a database or state store for audit trail</li>
 *   <li>Emit metrics for each status transition to monitor pipeline health</li>
 *   <li>Implement timeout detection for messages stuck in PROCESSING state</li>
 *   <li>Configure alerts for high FAILED or DEAD_LETTER rates</li>
 * </ul>
 *
 * @author Serkut Yıldırım
 */
public enum MessageStatus {

    /**
     * Message has been created and sent to Kafka.
     * This is the initial state when a producer creates a new message.
     * At this point, the message is in the Kafka topic but not yet consumed.
     */
    CREATED,

    /**
     * Message is currently being processed by a consumer.
     * Consumer has received the message and is executing business logic.
     * Transition from CREATED happens when consumer polls the message.
     * Transition from RETRY happens when retrying after a previous failure.
     */
    PROCESSING,

    /**
     * Message processing completed successfully.
     * All business logic executed without errors.
     * Message can be committed and archived.
     * This is a terminal success state.
     */
    COMPLETED,

    /**
     * Message processing failed.
     * Could be due to:
     * - Transient errors (network timeout, service unavailable, database deadlock)
     * - Permanent errors (validation failure, business rule violation, malformed data)
     * Next state depends on error type and retry configuration:
     * - Transient errors → RETRY (if retry attempts remain)
     * - Permanent errors or retry exhaustion → DEAD_LETTER
     */
    FAILED,

    /**
     * Message is queued for retry after a transient failure.
     * Indicates that the failure was deemed recoverable and worth retrying.
     * Typically involves:
     * - Exponential backoff delay before next processing attempt
     * - Retry counter increment to prevent infinite loops
     * - Logging of retry reason for debugging
     * Transitions to PROCESSING when retry is attempted.
     * Transitions to DEAD_LETTER if max retries exceeded.
     */
    RETRY,

    /**
     * Message sent to dead letter queue after exhausting all recovery options.
     * Occurs when:
     * - Maximum retry attempts exceeded (e.g., 3 retries with exponential backoff)
     * - Non-retryable error detected (e.g., schema validation failure, invalid format)
     * - Message poisoning detected (repeatedly crashes consumer)
     * Requires manual investigation and intervention.
     * Dead letter messages should be:
     * - Logged with full context (original message, error details, retry history)
     * - Monitored via alerts for operations team
     * - Periodically reviewed and potentially reprocessed after fixes
     * This is a terminal failure state.
     */
    DEAD_LETTER
}
