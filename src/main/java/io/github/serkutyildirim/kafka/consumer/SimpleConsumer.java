package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

/**
 * Simple Kafka consumer implementation.
 *
 * Demonstrates the most basic way to consume messages from Kafka:
 * - Auto-commit offset mode
 * - Single message processing
 * - Basic error handling
 * - Suitable for simple use cases
 *
 * TODO: Add message processing logic
 * TODO: Add error handling
 * TODO: Add message validation
 *
 * @author Serkut Yıldırım
 */
@Component
@Slf4j
public class SimpleConsumer {

    private static final String GROUP_ID = "simple-consumer-group";

    /**
     * Consume messages from demo-messages topic
     * Auto-commit is disabled in application.yml, but this consumer demonstrates
     * the simplest consumption pattern
     * 
     * @param message The consumed message
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, DemoTransaction> record) {
        long startTime = System.nanoTime();
        String memberId = currentMemberId();

        try {
            DemoTransaction message = requirePayload(record);

            log.info("event=consume_start pattern=simple groupId={} memberId={} topic={} partition={} offset={} key={} messageId={}",
                GROUP_ID,
                memberId,
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                message.getMessageId());
            log.info("event=message_details pattern=simple groupId={} memberId={} sourceId={} targetId={} amount={}",
                GROUP_ID,
                memberId,
                message.getSourceId(),
                message.getTargetId(),
                message.getAmount());

            // Auto-commit keeps the code minimal because the container commits offsets in the background.
            // The trade-off is recovery precision: a crash after poll/commit but before durable processing can lose the record.
            Thread.sleep(100);

            long durationMs = elapsedMillis(startTime);
            log.info("event=consume_success pattern=simple groupId={} memberId={} partition={} offset={} durationMs={} processingResult=processed",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                durationMs);
        } catch (DeserializationException ex) {
            log.error("event=consume_failure pattern=simple groupId={} memberId={} partition={} offset={} errorType=deserialization error={} ",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                ex.getMessage(),
                ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("event=consume_failure pattern=simple groupId={} memberId={} partition={} offset={} errorType=interrupted error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                ex.getMessage(),
                ex);
        } catch (Exception ex) {
            log.error("event=consume_failure pattern=simple groupId={} memberId={} partition={} offset={} errorType=business error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                ex.getMessage(),
                ex);
        }
    }

    private DemoTransaction requirePayload(ConsumerRecord<String, DemoTransaction> record) {
        if (record == null || record.value() == null) {
            throw new IllegalArgumentException("DemoTransaction payload is null. This often means deserialization failed before the listener could process a valid object.");
        }
        return record.value();
    }

    private String currentMemberId() {
        // The exact low-level member ID is managed by the Kafka client and is not directly passed in this minimal listener signature.
        // For teaching logs we surface the listener thread, which still helps correlate partition assignment and rebalance activity.
        return Thread.currentThread().getName();
    }

    private long elapsedMillis(long startTime) {
        return Math.max(1L, (System.nanoTime() - startTime) / 1_000_000L);
    }
}
