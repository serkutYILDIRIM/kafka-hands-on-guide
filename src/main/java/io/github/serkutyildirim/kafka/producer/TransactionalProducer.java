package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Demonstrates the Transactional Send producer pattern for {@link DemoTransaction} events.
 *
 * <p><b>When to use:</b> Financial workflows and multi-step operations where a group of Kafka writes must commit or roll back together.</p>
 * <p><b>Performance:</b> The slowest producer style here because Kafka coordinates transaction state and fencing.</p>
 * <p><b>Common pitfalls:</b> Consumers must use {@code isolation.level=read_committed}; otherwise they may still read aborted records.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * boolean committed = transactionalProducer.sendTransactional(List.of(transaction1, transaction2));
 * }</pre>
 */
@Component
@Slf4j
public class TransactionalProducer {

    private static final String TOPIC = "demo-messages";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    @Autowired
    @Qualifier("transactionalKafkaTemplate")
    private KafkaTemplate<String, DemoTransaction> transactionalKafkaTemplate;

    /**
     * Pattern name: Transactional Send (Exactly-Once Semantics)
     * Characteristics: Atomic, all-or-nothing, slowest
     * Use cases: Financial transactions, multi-step operations
     * Performance: 50-70% of non-transactional throughput
     * Guarantee: Either all messages sent or none
     */
    public boolean sendTransactional(List<DemoTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            log.warn("Transactional send skipped because no messages were provided");
            return false;
        }

        try {
            log.info("Beginning Kafka transaction for {} messages", transactions.size());

            Boolean committed = transactionalKafkaTemplate.executeInTransaction(operations -> {
                // Transaction lifecycle: begin -> send each record -> commit if all succeed.
                // If any send fails, we throw an exception so Spring rolls the Kafka transaction back.
                for (DemoTransaction transaction : transactions) {
                    String accountKey = transaction.getSourceId();

                    // We use the source account ID as the Kafka key so all events for the same account land on the same partition.
                    // Kafka guarantees ordering within a partition, which is crucial for account history and balance workflows.
                    // Exactly-once semantics here means the whole batch is committed atomically from the producer perspective.
                    try {
                        operations.send(TOPIC, accountKey, transaction)
                                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    } catch (SerializationException ex) {
                        log.error("Serialization failed inside transaction for messageId={}", transaction.getMessageId(), ex);
                        throw ex;
                    } catch (TimeoutException ex) {
                        log.error("Transactional send timed out for messageId={}", transaction.getMessageId(), ex);
                        throw new IllegalStateException("Timed out during transactional send", ex);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        log.error("Transactional send interrupted for messageId={}", transaction.getMessageId(), ex);
                        throw new IllegalStateException("Interrupted during transactional send", ex);
                    } catch (ExecutionException ex) {
                        log.error("Broker rejected transactional messageId={}", transaction.getMessageId(), ex);
                        throw new IllegalStateException("Transactional send failed", ex);
                    }
                }
                return Boolean.TRUE;
            });

            // Commit happens when the callback exits successfully; otherwise Kafka aborts the transaction.
            // Transactions provide atomicity, but they add coordination overhead and reduce throughput.
            // Consumers must set isolation.level=read_committed to avoid seeing aborted transactional records.
            log.info("Kafka transaction committed for {} messages", transactions.size());
            // Retry strategy note: retries should be cautious here; re-running a failed business transaction may require idempotent business keys and compensation logic.
            return Boolean.TRUE.equals(committed);
        } catch (SerializationException ex) {
            log.error("Kafka transaction aborted due to serialization error. firstMessageId={}", transactions.get(0).getMessageId(), ex);
            return false;
        } catch (Exception ex) {
            log.error("Kafka transaction rolled back for firstMessageId={}", transactions.get(0).getMessageId(), ex);
            return false;
        }
    }
}
