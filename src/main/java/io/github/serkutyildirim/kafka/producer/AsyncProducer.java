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
 * Demonstrates the Asynchronous with Callback producer pattern for {@link DemoTransaction} events.
 *
 * <p><b>When to use:</b> High-throughput message publishing where the caller should stay responsive but failures still need to be observed.</p>
 * <p><b>Performance:</b> Much faster than synchronous send because the calling thread does not block on broker acknowledgment.</p>
 * <p><b>Common pitfalls:</b> Teams sometimes forget to observe the returned future, which hides delivery failures and operational signals.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * asyncProducer.sendAsync(transaction)
 *     .thenAccept(result -> System.out.println(result.getRecordMetadata().offset()));
 * }</pre>
 */
@Component
@Slf4j
public class AsyncProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    /**
     * Pattern name: Asynchronous with Callback
     * Characteristics: Fast, non-blocking, reliable with callbacks
     * Use cases: High-throughput with error handling
     * Performance: ~50k msgs/sec
     * Latency: <1ms (non-blocking)
     */
    public CompletableFuture<SendResult<String, DemoTransaction>> sendAsync(DemoTransaction transaction) {
        // Async send returns immediately, so request threads stay free for more work.
        // Success and failure are handled in callbacks, which is the key difference from fire-and-forget.
        // Retry strategy note: let Kafka producer retries handle transient issues, then observe failures in the callback for alerting or compensation.
        try {
            CompletableFuture<SendResult<String, DemoTransaction>> future = kafkaTemplate.send(TOPIC, transaction);

            future.thenAccept(result -> log.info(
                    "Async message {} sent to partition {} at offset {}",
                    transaction.getMessageId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
            )).exceptionally(ex -> {
                logAsyncFailure(transaction, ex);
                return null;
            });

            return future;
        } catch (SerializationException ex) {
            log.error("Serialization failed for async messageId={}", transaction.getMessageId(), ex);
            return CompletableFuture.failedFuture(ex);
        } catch (RuntimeException ex) {
            logAsyncFailure(transaction, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    private void logAsyncFailure(DemoTransaction transaction, Throwable throwable) {
        Throwable rootCause = unwrap(throwable);
        if (rootCause instanceof TimeoutException) {
            log.error("Async send timed out for messageId={}", transaction.getMessageId(), rootCause);
            return;
        }
        if (rootCause instanceof SerializationException) {
            log.error("Async serialization failed for messageId={}", transaction.getMessageId(), rootCause);
            return;
        }
        log.error("Async send failed for messageId={}", transaction.getMessageId(), rootCause);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
