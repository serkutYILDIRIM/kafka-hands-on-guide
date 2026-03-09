package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Demonstrates the Synchronous Send producer pattern for {@link DemoTransaction} events.
 *
 * <p><b>When to use:</b> Critical transactions and low-volume business events where the caller must know whether Kafka accepted the record.</p>
 * <p><b>Performance:</b> Slower than async patterns because the caller blocks until Kafka responds.</p>
 * <p><b>Common pitfalls:</b> Blocking per message reduces throughput sharply and can tie up request threads under load.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * SendResult<String, DemoTransaction> result = reliableProducer.sendWithConfirmation(transaction);
 * }</pre>
 */
@Component
@Slf4j
public class ReliableProducer {

    private static final String TOPIC = "demo-messages";
    private static final long SEND_TIMEOUT_SECONDS = 10;

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    /**
     * Pattern name: Synchronous Send
     * Characteristics: Reliable, blocking, slower
     * Use cases: Critical transactions, low-volume important messages
     * Performance: ~5k msgs/sec (single thread)
     * Latency: +10-50ms per message
     */
    public SendResult<String, DemoTransaction> sendWithConfirmation(DemoTransaction transaction)
            throws ExecutionException, InterruptedException {
        // Synchronous send waits for Kafka to acknowledge the write before returning.
        // Use it when the caller must fail fast if the broker does not accept the record.
        // Retry strategy note: producer-level retries help transient failures, but blocking callers still pay the latency cost.
        try {
            SendResult<String, DemoTransaction> result = kafkaTemplate
                    .send(TOPIC, transaction)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info(
                    "Message sent to partition {} at offset {}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            );
            return result;
        } catch (SerializationException ex) {
            log.error("Serialization failed for synchronous messageId={}", transaction.getMessageId(), ex);
            throw ex;
        } catch (TimeoutException ex) {
            log.error("Timed out while sending synchronous messageId={}", transaction.getMessageId(), ex);
            throw new ExecutionException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Synchronous send interrupted for messageId={}", transaction.getMessageId(), ex);
            throw ex;
        } catch (ExecutionException ex) {
            log.error("Kafka rejected synchronous messageId={}", transaction.getMessageId(), ex);
            throw ex;
        }
    }
}
