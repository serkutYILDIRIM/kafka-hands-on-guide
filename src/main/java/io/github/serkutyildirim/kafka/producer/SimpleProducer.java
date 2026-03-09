package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the Fire-and-Forget producer pattern for {@link DemoTransaction} events.
 *
 * <p><b>When to use:</b> Metrics, logs, telemetry, and other non-critical data where raw throughput matters more than delivery confirmation.</p>
 * <p><b>Performance:</b> Typically the fastest option because the caller does not wait for broker acknowledgments.</p>
 * <p><b>Common pitfalls:</b> Broker-side failures can happen after the method returns, so this pattern should not be used for money movement or other critical workflows.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * simpleProducer.send(transaction);
 * }</pre>
 */
@Component
@Slf4j
public class SimpleProducer {

    private static final String TOPIC = "demo-messages";

    @Autowired
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    /**
     * Pattern name: Fire-and-Forget
     * Characteristics: Fastest, least reliable
     * Use cases: Metrics, logs, non-critical data
     * Performance: ~100k msgs/sec
     * Reliability: Message might be lost
     */
    public void send(DemoTransaction transaction) {
        // This is the simplest producer pattern: enqueue the send request and return immediately.
        // We intentionally do not attach a callback or wait for broker acknowledgment.
        // That makes it very fast, but later network/broker failures may go unnoticed by this caller.
        try {
            log.info("Sending message: {}", transaction.getMessageId());
            kafkaTemplate.send(TOPIC, transaction);
            // Retry strategy note: fire-and-forget typically relies only on producer-level retries from configuration.
        } catch (SerializationException ex) {
            log.error("Serialization failed for fire-and-forget messageId={}", transaction.getMessageId(), ex);
        } catch (RuntimeException ex) {
            log.error("Unable to dispatch fire-and-forget messageId={} to topic={}", transaction.getMessageId(), TOPIC, ex);
        }
    }
}
