package io.github.serkutyildirim.kafka.model;

/**
 * Enum representing the processing priority of a message.
 * Used to implement priority-based routing and processing strategies in Kafka.
 *
 * <p><b>Priority-Based Routing Strategies:</b></p>
 *
 * <p><b>1. Multi-Topic Strategy (Recommended):</b></p>
 * <pre>
 * Route messages to different topics based on priority:
 * - notifications.high   → Dedicated consumers, higher throughput
 * - notifications.medium → Standard consumers
 * - notifications.low    → Batch processors, lower priority
 *
 * Pros: True priority isolation, HIGH messages never blocked by LOW
 * Cons: More topics to manage, more complex configuration
 * </pre>
 *
 * <p><b>2. Consumer-Side Filtering Strategy:</b></p>
 * <pre>
 * All priorities on same topic, consumers filter and prioritize:
 * - Consumer polls messages
 * - Sorts by priority before processing
 * - Processes HIGH → MEDIUM → LOW
 *
 * Pros: Simpler topic management
 * Cons: LOW priority messages still consume partition space, potential head-of-line blocking
 * </pre>
 *
 * <p><b>3. Hybrid Strategy:</b></p>
 * <pre>
 * Separate topic for HIGH priority only:
 * - notifications.high → Dedicated fast-track consumers
 * - notifications.standard → Combined MEDIUM + LOW with consumer-side prioritization
 *
 * Pros: Balance between isolation and simplicity
 * Cons: Two processing pipelines to maintain
 * </pre>
 *
 * <p><b>Implementation Considerations:</b></p>
 * <ul>
 *   <li><b>Consumer Resources:</b> HIGH priority topics may need more consumer instances
 *       to ensure low latency</li>
 *   <li><b>Partition Count:</b> HIGH priority topics may benefit from more partitions
 *       for better parallelism</li>
 *   <li><b>Monitoring:</b> Track processing latency by priority to detect priority inversion</li>
 *   <li><b>Backpressure:</b> Implement circuit breakers to prevent LOW priority messages
 *       from overwhelming system during high load</li>
 * </ul>
 *
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * // Critical security alert
 * DemoNotification alert = DemoNotification.builder()
 *     .recipientId("user-123")
 *     .content("Suspicious login detected from new device")
 *     .notificationType(NotificationType.PUSH)
 *     .priority(Priority.HIGH)  // Ensures immediate processing
 *     .build();
 *
 * // Promotional email
 * DemoNotification promo = DemoNotification.builder()
 *     .recipientId("user-456")
 *     .content("Check out our weekly deals!")
 *     .notificationType(NotificationType.EMAIL)
 *     .priority(Priority.LOW)  // Can be batched and delayed
 *     .build();
 * }</pre>
 *
 * @see DemoNotification
 * @author Serkut Yıldırım
 */
public enum Priority {

    /**
     * Low priority - non-urgent messages that can tolerate delays.
     * Processing characteristics:
     * - Can be batched for efficiency
     * - May be processed during off-peak hours
     * - Acceptable latency: minutes to hours
     * - May be throttled during high system load
     *
     * Typical use cases:
     * - Marketing emails and newsletters
     * - Daily/weekly digest notifications
     * - Non-critical system updates
     * - Informational announcements
     */
    LOW,

    /**
     * Medium priority - standard business messages requiring timely processing.
     * Processing characteristics:
     * - Processed in near real-time (seconds to minutes)
     * - Standard consumer resources allocated
     * - Acceptable latency: seconds to minutes
     * - Balanced throughput and latency
     *
     * Typical use cases:
     * - Order confirmations
     * - Account activity notifications
     * - Standard transaction alerts
     * - General customer communications
     */
    MEDIUM,

    /**
     * High priority - critical messages requiring immediate processing.
     * Processing characteristics:
     * - Processed immediately with dedicated resources
     * - May bypass normal queuing mechanisms
     * - Target latency: sub-second to seconds
     * - Higher resource allocation (more consumers, faster instances)
     *
     * Typical use cases:
     * - Security alerts (fraud detection, suspicious activity)
     * - Payment failures requiring immediate action
     * - System critical notifications
     * - Time-sensitive OTP codes
     * - Real-time transaction confirmations
     */
    HIGH
}
