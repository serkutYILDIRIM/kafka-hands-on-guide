package io.github.serkutyildirim.kafka.model;

/**
 * Enum representing the delivery channel for notifications.
 * Defines the medium through which a notification will be sent to the recipient.
 *
 * <p>Each notification type may have different:
 * <ul>
 *   <li>Delivery latency (PUSH is fastest, EMAIL is slowest)</li>
 *   <li>Cost implications (SMS typically has per-message charges)</li>
 *   <li>User preferences (some users opt out of certain channels)</li>
 *   <li>Content formatting requirements (SMS has character limits)</li>
 * </ul>
 *
 * <p><b>Usage in Kafka Routing:</b></p>
 * <p>This enum can be used to:
 * <ul>
 *   <li>Route notifications to different Kafka topics per channel (e.g., notifications.email, notifications.sms)</li>
 *   <li>Apply channel-specific consumer logic and formatting</li>
 *   <li>Implement priority-based processing (e.g., PUSH notifications processed before EMAIL)</li>
 *   <li>Track delivery metrics per channel type</li>
 * </ul>
 *
 * @see DemoNotification
 * @author Serkut Yıldırım
 */
public enum NotificationType {

    /**
     * Email notification delivery.
     * Characteristics:
     * - Slower delivery (seconds to minutes)
     * - Supports rich HTML content
     * - Higher character/size limits
     * - Typically lower cost
     * - Requires valid email address
     * Use cases: Detailed transaction reports, account statements, newsletters
     */
    EMAIL,

    /**
     * SMS (Short Message Service) text message notification.
     * Characteristics:
     * - Fast delivery (seconds)
     * - Plain text only, limited formatting
     * - Character limit (typically 160 characters per SMS)
     * - Higher cost per message
     * - Requires valid phone number
     * - Higher open rate than email
     * Use cases: OTP codes, critical alerts, payment confirmations
     */
    SMS,

    /**
     * Mobile push notification.
     * Characteristics:
     * - Fastest delivery (near real-time)
     * - Limited content (title + short message)
     * - Requires user to have app installed
     * - Requires notification permissions
     * - Lower cost (typically free after infrastructure)
     * - Can include actions/deep links
     * Use cases: Real-time updates, app engagement, time-sensitive alerts
     */
    PUSH
}
