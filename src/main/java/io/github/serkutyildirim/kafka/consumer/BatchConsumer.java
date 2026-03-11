package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Batch Kafka consumer implementation.
 *
 * <p>Batch consumption improves throughput by amortizing listener invocation cost, serialization overhead, and downstream I/O across multiple records.</p>
 */
@Component
@Slf4j
public class BatchConsumer {

    private static final String GROUP_ID = "batch-consumer-group";
    private static final int SUB_BATCH_SIZE = 25;

    /**
     * Pattern name: Batch Consumer.
     * Characteristics: Processes multiple messages at once.
     * Performance: Higher throughput and better resource utilization because one callback can handle many records.
     * Use cases: Bulk inserts, aggregation, analytics, compaction-style workloads, and warehouse ingestion.
     * Trade-off: Higher latency, trickier partial-failure handling, and more careful batch-size tuning.
     */
    @KafkaListener(
        topics = KafkaTopicConfig.DEMO_MESSAGES_TOPIC,
        groupId = GROUP_ID,
        containerFactory = "batchListenerContainerFactory"
    )
    public void consumeBatch(List<ConsumerRecord<String, DemoTransaction>> records) {
        long startTime = System.nanoTime();
        String memberId = currentMemberId();

        log.info("Received batch of {} messages", records.size());
        log.info("event=batch_receive pattern=batch groupId={} memberId={} batchSize={}", GROUP_ID, memberId, records.size());

        try {
            List<DemoTransaction> messages = new ArrayList<>(records.size());

            for (ConsumerRecord<String, DemoTransaction> record : records) {
                DemoTransaction message = requirePayload(record);
                messages.add(message);

                log.info("event=batch_record pattern=batch groupId={} memberId={} partition={} offset={} key={} messageId={} sourceId={} targetId={} amount={}",
                    GROUP_ID,
                    memberId,
                    record.partition(),
                    record.offset(),
                    record.key(),
                    message.getMessageId(),
                    message.getSourceId(),
                    message.getTargetId(),
                    message.getAmount());
            }

            // Batch processing reduces per-record overhead such as network calls and database roundtrips.
            // In real systems this is where you'd replace N single-row writes with one bulk insert/update.
            // Tune the batch size carefully: too small wastes throughput, too large increases memory pressure and retry blast radius.
            processInSubBatches(messages, SUB_BATCH_SIZE);

            long durationMs = elapsedMillis(startTime);
            double throughput = calculateThroughput(records.size(), durationMs);
            log.info("event=batch_success pattern=batch groupId={} memberId={} batchSize={} durationMs={} throughputMessagesPerSecond={} status=processed",
                GROUP_ID,
                memberId,
                records.size(),
                durationMs,
                String.format(java.util.Locale.US, "%.2f", throughput));
        } catch (DeserializationException ex) {
            log.error("event=batch_failure pattern=batch groupId={} memberId={} batchSize={} durationMs={} errorType=deserialization error={}",
                GROUP_ID,
                memberId,
                records.size(),
                elapsedMillis(startTime),
                ex.getMessage(),
                ex);
            throw ex;
        } catch (RuntimeException ex) {
            // Partial failures are the hard part of batch listeners.
            // This example intentionally uses all-or-nothing semantics: one bad record fails the entire batch so Kafka retries the full batch.
            // That is simple to reason about, but the trade-off is duplicate work when only one record in the batch is bad.
            log.error("event=batch_failure pattern=batch groupId={} memberId={} batchSize={} durationMs={} errorType=business error={}",
                GROUP_ID,
                memberId,
                records.size(),
                elapsedMillis(startTime),
                ex.getMessage(),
                ex);
            throw ex;
        }
    }

    private void processInSubBatches(List<DemoTransaction> messages, int batchSize) {
        for (int fromIndex = 0; fromIndex < messages.size(); fromIndex += batchSize) {
            int toIndex = Math.min(fromIndex + batchSize, messages.size());
            List<DemoTransaction> subBatch = messages.subList(fromIndex, toIndex);

            if (subBatch.stream().anyMatch(this::shouldFailBatch)) {
                throw new IllegalStateException("Simulated batch failure to demonstrate whole-batch retry semantics");
            }

            // Sub-batches are a practical compromise when the broker poll size is larger than what the database can handle efficiently.
            try {
                Thread.sleep(Math.min(40L * subBatch.size(), 200L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Batch processing interrupted", ex);
            }
        }
    }

    private boolean shouldFailBatch(DemoTransaction transaction) {
        return transaction.getDescription() != null && transaction.getDescription().toUpperCase().contains("FAIL_BATCH");
    }

    private DemoTransaction requirePayload(ConsumerRecord<String, DemoTransaction> record) {
        if (record == null || record.value() == null) {
            throw new IllegalArgumentException("Batch payload is null. Null values usually indicate malformed input or deserialization problems that should fail the full batch.");
        }
        return record.value();
    }

    private double calculateThroughput(int messageCount, long durationMs) {
        return messageCount * 1000.0 / Math.max(durationMs, 1L);
    }

    private String currentMemberId() {
        return Thread.currentThread().getName();
    }

    private long elapsedMillis(long startTime) {
        return Math.max(1L, (System.nanoTime() - startTime) / 1_000_000L);
    }
}
