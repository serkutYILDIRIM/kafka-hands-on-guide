package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Error handling consumer example with retry and DLQ.
 *
 * <p>This listener demonstrates how to separate temporary failures from permanent ones so healthy traffic keeps moving and poison-pill records are isolated.</p>
 */
@Component
@Slf4j
public class ErrorHandlingConsumer {

    private static final String GROUP_ID = "error-handling-group";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 250L;

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    private final Map<String, AtomicInteger> retryCounters = new ConcurrentHashMap<>();

    /**
     * Pattern name: Error Handling Consumer with DLQ.
     * Error strategies:
     * 1. RETRY: Temporary failures such as network or database timeouts.
     * 2. DLQ: Permanent failures such as validation problems or malformed data.
     * 3. SKIP: Optional for non-critical errors when a business workflow can safely continue.
     * DLQ pattern: Failed messages are moved to a separate topic for investigation and controlled replay.
     * Retry policy: Maximum 3 attempts with exponential backoff.
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
            log.info("event=consume_start pattern=error-handling groupId={} memberId={} topic={} partition={} offset={} key={} messageId={} attemptsSoFar={}",
                GROUP_ID,
                memberId,
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                message.getMessageId(),
                currentAttempts(retryKey));
            log.info("event=message_details pattern=error-handling groupId={} memberId={} sourceId={} targetId={} amount={}",
                GROUP_ID,
                memberId,
                message.getSourceId(),
                message.getTargetId(),
                message.getAmount());

            processMessage(message);

            // Success path: process first, then acknowledge.
            // Keeping the ack after business success preserves at-least-once delivery for transient failures.
            ack.acknowledge();
            retryCounters.remove(retryKey);
            log.info("event=consume_success pattern=error-handling groupId={} memberId={} partition={} offset={} durationMs={} status=acknowledged",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime));
        } catch (RetryableProcessingException ex) {
            int attempts = retryCounters.computeIfAbsent(retryKey, key -> new AtomicInteger()).incrementAndGet();
            long backoffMs = exponentialBackoff(attempts);

            // Retryable errors are temporary infrastructure or dependency failures.
            // We intentionally do not acknowledge here so Kafka redelivers the record later.
            // After three attempts, we stop retrying and move the record to DLQ to avoid an endless poison-pill loop.
            log.error("event=retryable_failure pattern=error-handling groupId={} memberId={} partition={} offset={} durationMs={} attempt={} maxAttempts={} backoffMs={} error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime),
                attempts,
                MAX_RETRY_ATTEMPTS,
                backoffMs,
                ex.getMessage(),
                ex);

            if (attempts >= MAX_RETRY_ATTEMPTS) {
                sendToDlq(record, memberId, attempts, ex);
                ack.acknowledge();
                retryCounters.remove(retryKey);
                log.warn("event=retryable_to_dlq pattern=error-handling groupId={} memberId={} partition={} offset={} attempts={} action=dlq_then_ack",
                    GROUP_ID,
                    memberId,
                    record.partition(),
                    record.offset(),
                    attempts);
                return;
            }

            sleepBackoff(backoffMs);
        } catch (DeserializationException ex) {
            handlePermanentFailure(record, ack, retryKey, memberId, startTime, "deserialization", ex);
        } catch (NonRetryableProcessingException | IllegalArgumentException ex) {
            // Permanent errors should not be retried because they will keep failing on every redelivery.
            // The dead-letter queue pattern preserves the bad record for investigation, replay tooling, and operator alerts.
            // Monitoring the DLQ is critical; otherwise failures are merely displaced instead of operationally resolved.
            handlePermanentFailure(record, ack, retryKey, memberId, startTime, "non-retryable", ex);
        } catch (Exception ex) {
            handlePermanentFailure(record, ack, retryKey, memberId, startTime, "unexpected", ex);
        }
    }

    private void processMessage(DemoTransaction message) throws InterruptedException {
        String description = message.getDescription() == null ? "" : message.getDescription().toUpperCase();

        // Error classification matters:
        // - validation/domain issues are permanent and should go to the DLQ quickly.
        // - dependency timeouts are often transient and deserve bounded retry with backoff.
        if (description.contains("INVALID") || message.getAmount() == null || message.getAmount().signum() <= 0) {
            throw new NonRetryableProcessingException("Validation failed for DemoTransaction payload");
        }

        if (description.contains("RETRY") || description.contains("TIMEOUT")) {
            throw new RetryableProcessingException("Simulated downstream timeout while processing transaction");
        }

        Thread.sleep(60);
    }

    private void handlePermanentFailure(
            ConsumerRecord<String, DemoTransaction> record,
            Acknowledgment ack,
            String retryKey,
            String memberId,
            long startTime,
            String errorType,
            Exception ex) {

        log.error("event=permanent_failure pattern=error-handling groupId={} memberId={} partition={} offset={} durationMs={} errorType={} error={}",
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
        log.warn("event=permanent_to_dlq pattern=error-handling groupId={} memberId={} partition={} offset={} action=dlq_then_ack",
            GROUP_ID,
            memberId,
            record.partition(),
            record.offset());
    }

    private void sendToDlq(ConsumerRecord<String, DemoTransaction> record, String memberId, int attempts, Exception ex) {
        if (record.value() == null) {
            log.warn("event=dlq_skip pattern=error-handling groupId={} memberId={} partition={} offset={} attempts={} reason=null_payload error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                attempts,
                ex.getMessage());
            return;
        }

        kafkaTemplate.send(KafkaTopicConfig.DEMO_DLQ_TOPIC, record.key(), record.value());
        log.warn("event=dlq_send pattern=error-handling groupId={} memberId={} dlqTopic={} partition={} offset={} attempts={} messageId={} error={}",
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
            throw new IllegalArgumentException("DemoTransaction payload is null. In production, consider a raw-byte DLQ for malformed records that cannot be deserialized into typed objects.");
        }
        return record.value();
    }

    private long exponentialBackoff(int attempt) {
        return Math.min(BASE_BACKOFF_MS * (1L << Math.max(0, attempt - 1)), 2_000L);
    }

    private void sleepBackoff(long backoffMs) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("event=retry_backoff_interrupted pattern=error-handling groupId={} memberId={} backoffMs={} error={}",
                GROUP_ID,
                currentMemberId(),
                backoffMs,
                interruptedException.getMessage(),
                interruptedException);
        }
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

    private static class RetryableProcessingException extends RuntimeException {
        private RetryableProcessingException(String message) {
            super(message);
        }
    }

    private static class NonRetryableProcessingException extends RuntimeException {
        private NonRetryableProcessingException(String message) {
            super(message);
        }
    }
}
