package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * Demonstrates the Key-Based Partitioning producer pattern for {@link DemoTransaction} events.
 *
 * <p><b>When to use:</b> User events, account transactions, and any workflow where all events for the same entity must stay ordered.</p>
 * <p><b>Performance:</b> Similar to other async sends, but key choice strongly affects partition balance.</p>
 * <p><b>Common pitfalls:</b> A hot key can overload one partition and reduce total throughput even when the topic has many partitions.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * partitionedProducer.sendWithKey(transaction.getSourceId(), transaction);
 * }</pre>
 */
@Component
@Slf4j
public class PartitionedProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    /**
     * Pattern name: Key-Based Partitioning
     * Characteristics: Ensures message ordering per key
     * Use cases: User events, account transactions
     * Key insight: Same key always goes to same partition
     * Partition formula: hash(key) % partition_count
     */
    public CompletableFuture<SendResult<String, DemoTransaction>> sendWithKey(String key, DemoTransaction transaction) {
        // Keys matter because Kafka guarantees ordering only within a single partition.
        // We usually use the account ID (here the source account ID) as the key so all events for that account stay together.
        // Kafka's default partitioner uses a hash of the key to pick the partition, which creates partition affinity.
        // If one key is dramatically hotter than others, that single partition can become a bottleneck.
        // Use null keys only when per-entity ordering does not matter and even distribution is more important.
        // Retry strategy note: async retries remain useful here, but repeated failures for one hot key may indicate a skew problem rather than transient instability.
        try {
            CompletableFuture<SendResult<String, DemoTransaction>> future = kafkaTemplate.send(TOPIC, key, transaction);

            future.thenAccept(result -> log.info(
                    "Message with key {} sent to partition {}",
                    key,
                    result.getRecordMetadata().partition()
            )).exceptionally(ex -> {
                logPartitionFailure(transaction, key, ex);
                return null;
            });

            return future;
        } catch (SerializationException ex) {
            log.error("Serialization failed for keyed messageId={} key={}", transaction.getMessageId(), key, ex);
            return CompletableFuture.failedFuture(ex);
        } catch (RuntimeException ex) {
            logPartitionFailure(transaction, key, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void logPartitionFailure(DemoTransaction transaction, String key, Throwable throwable) {
        Throwable rootCause = unwrap(throwable);
        if (rootCause instanceof TimeoutException) {
            log.error("Partitioned send timed out for messageId={} key={}", transaction.getMessageId(), key, rootCause);
            return;
        }
        if (rootCause instanceof SerializationException) {
            log.error("Partitioned serialization failed for messageId={} key={}", transaction.getMessageId(), key, rootCause);
            return;
        }
        log.error("Partitioned send failed for messageId={} key={}", transaction.getMessageId(), key, rootCause);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
