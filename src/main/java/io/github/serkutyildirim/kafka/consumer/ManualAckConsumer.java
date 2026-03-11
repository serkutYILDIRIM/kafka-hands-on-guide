package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual acknowledgment consumer example.
 *
 * <p>This pattern keeps offset advancement under application control: receive the record, execute business logic, and acknowledge only after success.</p>
 *
 * <p><b>Why teams choose this:</b> it is the most approachable way to implement at-least-once delivery when message loss is unacceptable but full Kafka transactions are unnecessary.</p>
 */
@Component
@Slf4j
public class ManualAckConsumer {

    private static final String GROUP_ID = "manual-ack-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final KafkaTemplate<String, DemoTransaction> kafkaTemplate;
    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    public ManualAckConsumer(KafkaTemplate<String, DemoTransaction> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Pattern name: Manual Acknowledgment Consumer.
     * Characteristics: Fine-grained offset control with explicit post-processing acknowledgment.
     * Delivery guarantee: At-least-once, so the same message can be processed more than once during failures or restarts.
     * Use cases: Critical data flows such as payments, orders, and workflows where message loss is unacceptable.
     * Pattern: Receive → Process → Acknowledge.
     * Idempotency requirement: Processing must be idempotent because an unacknowledged record will be redelivered.
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "manualAckListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record, Acknowledgment ack) {
        long startTime = System.nanoTime();
        String memberId = currentMemberId();
        String retryKey = retryKey(record);

        try {
            DemoTransaction message = requirePayload(record);
            log.info("Processing message: {}", message);
            log.info("event=consume_start pattern=manual-ack groupId={} memberId={} topic={} partition={} offset={} key={} messageId={} attemptsSoFar={}",
                GROUP_ID,
                memberId,
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                message.getMessageId(),
                currentAttempts(retryKey));
            log.info("event=message_details pattern=manual-ack groupId={} memberId={} sourceId={} targetId={} amount={}",
                GROUP_ID,
                memberId,
                message.getSourceId(),
                message.getTargetId(),
                message.getAmount());

            processBusinessLogic(message);

            // Acknowledge only after the business side-effect succeeds.
            // If we do not acknowledge, Kafka keeps the committed offset behind the current offset and redelivers later.
            // That is why idempotent writes and deduplication are so important in manual-ack consumers.
            ack.acknowledge();
            retryCounters.remove(retryKey);

            log.info("event=consume_success pattern=manual-ack groupId={} memberId={} partition={} offset={} durationMs={} status=acknowledged",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime));
        } catch (DeserializationException ex) {
            handleNonRetryableFailure(record, ack, retryKey, memberId, startTime, "deserialization", ex);
        } catch (Exception ex) {
            int attempts = retryCounters.computeIfAbsent(retryKey, key -> new AtomicInteger()).incrementAndGet();

            log.error("event=consume_failure pattern=manual-ack groupId={} memberId={} partition={} offset={} durationMs={} attempt={} maxAttempts={} errorType=business error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime),
                attempts,
                MAX_RETRY_ATTEMPTS,
                ex.getMessage(),
                ex);

            // Retry vs DLQ decision:
            // - below the threshold, do not acknowledge so the broker redelivers the same record.
            // - at or above the threshold, route to DLQ and acknowledge the original so one poison pill does not stall the partition forever.
            if (attempts >= MAX_RETRY_ATTEMPTS) {
                sendToDlq(record, memberId, attempts, ex);
                ack.acknowledge();
                retryCounters.remove(retryKey);
                log.warn("event=manual_ack_dlq groupId={} memberId={} partition={} offset={} attempts={} action=dlq_then_ack",
                    GROUP_ID,
                    memberId,
                    record.partition(),
                    record.offset(),
                    attempts);
            }
        }
    }

    private void processBusinessLogic(DemoTransaction message) throws InterruptedException {
        if (message.getDescription() != null && message.getDescription().toUpperCase().contains("FAIL_MANUAL")) {
            throw new IllegalStateException("Simulated business failure for manual acknowledgment demo");
        }

        Thread.sleep(75);
    }

    private void handleNonRetryableFailure(
            ConsumerRecord<String, DemoTransaction> record,
            Acknowledgment ack,
            String retryKey,
            String memberId,
            long startTime,
            String errorType,
            Exception ex) {

        log.error("event=consume_failure pattern=manual-ack groupId={} memberId={} partition={} offset={} durationMs={} errorType={} error={}",
            GROUP_ID,
            memberId,
            record.partition(),
            record.offset(),
            elapsedMillis(startTime),
            errorType,
            ex.getMessage(),
            ex);

        sendToDlq(record, memberId, MAX_RETRY_ATTEMPTS, ex);
        ack.acknowledge();
        retryCounters.remove(retryKey);
    }

    private void sendToDlq(ConsumerRecord<String, DemoTransaction> record, String memberId, int attempts, Exception ex) {
        if (record.value() == null) {
            log.warn("event=dlq_skip pattern=manual-ack groupId={} memberId={} partition={} offset={} attempts={} reason=null_payload error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                attempts,
                ex.getMessage());
            return;
        }

        kafkaTemplate.send(KafkaTopicConfig.DEMO_DLQ_TOPIC, record.key(), record.value());
        log.warn("event=dlq_send pattern=manual-ack groupId={} memberId={} dlqTopic={} partition={} offset={} attempts={} messageId={} error={}",
            GROUP_ID,
            memberId,
            KafkaTopicConfig.DEMO_DLQ_TOPIC,
            record.partition(),
            record.offset(),
            attempts,
            record.value().getMessageId(),
            ex.getMessage());
    }

    private DemoTransaction requirePayload(ConsumerRecord<String, DemoTransaction> record) {
        if (record == null || record.value() == null) {
            throw new IllegalArgumentException("DemoTransaction payload is null. With manual ack, poison pills should be isolated quickly so they do not block healthy traffic.");
        }
        return record.value();
    }

    private String retryKey(ConsumerRecord<String, DemoTransaction> record) {
        return record.topic() + '-' + record.partition() + '-' + record.offset();
    }

    private int currentAttempts(String retryKey) {
        return retryCounters.getOrDefault(retryKey, new AtomicInteger(0)).get();
    }

    private String currentMemberId() {
        return Thread.currentThread().getName();
    }

    private long elapsedMillis(long startTime) {
        return Math.max(1L, (System.nanoTime() - startTime) / 1_000_000L);
    }
}
