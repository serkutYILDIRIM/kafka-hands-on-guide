package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

/**
 * Consumer groups enable parallel processing because Kafka assigns each partition to exactly ONE consumer inside the same group.
 * Multiple groups can still consume the same topic independently, so one group can handle business processing while another powers analytics or audit trails.
 * Rebalancing happens when consumers join, leave, crash, deploy, or stop heartbeating; during that window Kafka pauses, redistributes partitions, and then consumption resumes.
 * Partition assignment strategies decide which member gets which partition after each rebalance, so the observed load split depends on partition count and active group members.
 * Testing tip: run multiple application instances against the same topic to observe partition distribution, current vs committed offsets, and group rebalancing behavior in practice.
 */
@Component
@Slf4j
public class GroupedConsumer {

    private static final String GROUP_ID = "grouped-consumer-1";

    /**
     * Pattern name: Grouped Consumer Example.
     * Characteristics: Same processing style as the simple consumer, but with a different group ID and therefore independent offsets and partition ownership.
     * Delivery guarantee: Matches the underlying auto-commit style, which is simple but less recovery-safe than manual acknowledgment.
     * Use cases: Demonstrating consumer group mechanics, partition sharing, and rebalance behavior.
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

            log.info("Consumed message: {} from partition: {} at offset: {}", message, record.partition(), record.offset());
            log.info("event=consume_start pattern=grouped groupId={} memberId={} topic={} partition={} offset={} key={} messageId={}",
                GROUP_ID,
                memberId,
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                message.getMessageId());
            log.info("event=message_details pattern=grouped groupId={} memberId={} sourceId={} targetId={} amount={}",
                GROUP_ID,
                memberId,
                message.getSourceId(),
                message.getTargetId(),
                message.getAmount());

            // Partition assignment strategies decide which consumer gets which partition during a rebalance.
            // In a single group, each partition is owned by only one active member, which is why partition count limits parallelism.
            // With auto-commit, the committed offset may advance independently from the record currently being processed.
            Thread.sleep(100);

            // Exercise:
            // 1) Run the app in two terminals with different ports.
            // 2) Send 10 messages to demo-messages.
            // 3) Observe how messages split between consumers in the same group.
            // 4) Stop one instance.
            // 5) Observe rebalancing: the remaining consumer takes over all partitions.
            log.info("event=consume_success pattern=grouped groupId={} memberId={} partition={} offset={} durationMs={} processingResult=processed",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime));
        } catch (DeserializationException ex) {
            log.error("event=consume_failure pattern=grouped groupId={} memberId={} partition={} offset={} durationMs={} errorType=deserialization error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime),
                ex.getMessage(),
                ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("event=consume_failure pattern=grouped groupId={} memberId={} partition={} offset={} durationMs={} errorType=interrupted error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime),
                ex.getMessage(),
                ex);
        } catch (Exception ex) {
            log.error("event=consume_failure pattern=grouped groupId={} memberId={} partition={} offset={} durationMs={} errorType=business error={}",
                GROUP_ID,
                memberId,
                record.partition(),
                record.offset(),
                elapsedMillis(startTime),
                ex.getMessage(),
                ex);
        }
    }

    private DemoTransaction requirePayload(ConsumerRecord<String, DemoTransaction> record) {
        if (record == null || record.value() == null) {
            throw new IllegalArgumentException("Grouped consumer received a null payload. Compare current position with committed offsets when debugging repeated poison-pill scenarios.");
        }
        return record.value();
    }

    private String currentMemberId() {
        return Thread.currentThread().getName();
    }

    private long elapsedMillis(long startTime) {
        return Math.max(1L, (System.nanoTime() - startTime) / 1_000_000L);
    }
}
