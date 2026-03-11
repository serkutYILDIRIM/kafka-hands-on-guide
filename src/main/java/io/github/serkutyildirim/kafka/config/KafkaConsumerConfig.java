package io.github.serkutyildirim.kafka.config;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central Kafka consumer configuration for the learning project.
 *
 * <p><b>Consumer groups and partition assignment:</b></p>
 * <ul>
 *   <li>Consumers in the same group share topic partitions so each record is processed once per group.</li>
 *   <li>If a topic has 3 partitions, only 3 consumers in that group can actively process records at the same time.</li>
 * </ul>
 *
 * <p><b>Rebalancing process:</b></p>
 * <ul>
 *   <li>When consumers join, leave, or time out, Kafka reassigns partitions across the group.</li>
 *   <li>Rebalances are normal, but too many can pause consumption and hurt latency, so session and heartbeat settings matter.</li>
 * </ul>
 *
 * <p><b>Offset management strategies:</b></p>
 * <ul>
 *   <li>Auto-commit is simple and good for demos, but it can acknowledge progress before business processing is truly durable.</li>
 *   <li>Manual acknowledgment gives more control and supports at-least-once delivery, but the consumer code becomes more complex.</li>
 *   <li>Batch consumption improves throughput, though retries and partial failures become harder to reason about.</li>
 * </ul>
 *
 * <p><b>ErrorHandlingDeserializer wrapper pattern:</b></p>
 * <ul>
 *   <li>The wrapper prevents deserialization failures from crashing the entire listener thread immediately.</li>
 *   <li>It captures deserialization problems as structured errors so listeners and error handlers can route bad records to a DLQ.</li>
 * </ul>
 *
 * <p><b>When to use each consumer type:</b></p>
 * <ul>
 *   <li>Use the standard consumer when simplicity matters most.</li>
 *   <li>Use manual acknowledgment when a record must only advance its offset after downstream work succeeds.</li>
 *   <li>Use batch consumers for high-volume pipelines where throughput matters more than per-message latency.</li>
 * </ul>
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @Bean
    @Primary
    public ConsumerFactory<String, DemoTransaction> consumerFactory() {
        Map<String, Object> configs = baseConsumerConfigs("demo-consumer-group", true);
        log.info("Creating standard consumer factory for group demo-consumer-group");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    @Primary
    public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Concurrency 3 means Spring can run up to 3 consumer threads for this listener factory.
        // That matches the 3-partition demo-messages topic nicely and demonstrates parallel consumption.
        // More threads than partitions would not increase throughput for a single group.
        factory.setConcurrency(3);
        log.info("Creating standard listener container factory with concurrency=3 and auto-commit enabled");
        return factory;
    }

    @Bean
    public ConsumerFactory<String, DemoTransaction> manualAckConsumerFactory() {
        Map<String, Object> configs = baseConsumerConfigs("manual-ack-group", false);
        log.info("Creating manual-ack consumer factory for group manual-ack-group");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean(name = "manualAckListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> manualAckListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(manualAckConsumerFactory());
        // Manual offset control lets the listener commit only after business processing really succeeds.
        // That gives an at-least-once delivery guarantee because failed records are re-read until acknowledged.
        // The trade-off is that consumers must be idempotent, otherwise retries can apply the same effect twice.
        factory.getContainerProperties().setAckMode(AckMode.MANUAL);
        // Concurrency 3 lines up with topics that have up to 3 partitions and shows parallel manual-ack processing.
        factory.setConcurrency(3);
        log.info("Creating manual-ack listener container factory with AckMode.MANUAL and concurrency=3");
        return factory;
    }

    @Bean
    public ConsumerFactory<String, DemoTransaction> batchConsumerFactory() {
        Map<String, Object> configs = baseConsumerConfigs("batch-consumer-group", false);
        // max.poll.records limits how many records one poll returns.
        // 100 is a modest batch size that improves throughput without creating very large in-memory batches.
        // Larger batches reduce overhead but also increase per-batch failure impact and end-to-end latency.
        configs.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        log.info("Creating batch consumer factory for group batch-consumer-group with maxPollRecords=100 and autoCommit=false");
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean(name = "batchListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> batchListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DemoTransaction> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        // Batch listeners reduce listener invocation overhead and can improve throughput for high-volume topics.
        // The main trade-off is higher latency because records may wait to accumulate into a batch.
        // Batch failures are also harder because one bad record can affect how the entire batch is retried.
        // AckMode.BATCH commits offsets only after the listener method returns successfully,
        // which gives this demo the requested all-or-nothing retry behavior without manual acknowledgments.
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(AckMode.BATCH);
        // Concurrency 2 is intentional here: fewer concurrent batch workers can reduce resource spikes while still improving throughput.
        factory.setConcurrency(2);
        log.info("Creating batch listener container factory with batch mode enabled, AckMode.BATCH, autoCommit=false and concurrency=2");
        return factory;
    }

    private Map<String, Object> baseConsumerConfigs(String groupId, boolean enableAutoCommit) {
        Map<String, Object> configs = new LinkedHashMap<>();

        // Bootstrap servers tell consumers where to find the Kafka cluster.
        // Keeping this externalized avoids code changes between local development and production.
        // Misconfigured broker addresses are one of the most common reasons consumers fail at startup.
        configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // The consumer group ID controls partition assignment and offset tracking.
        // Using a dedicated group per example makes demos easier to understand because each example has isolated offsets.
        // Accidentally reusing a production group in local development can lead to very confusing read positions.
        configs.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // StringDeserializer turns record keys back into readable Java strings.
        // String keys are easy to debug and commonly used for partitioning or correlation IDs.
        configs.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // ErrorHandlingDeserializer wraps the real value deserializer to catch malformed payload problems gracefully.
        // Without this wrapper, bad JSON can fail the poll loop more abruptly and be harder to route to a DLQ.
        // This is a great safety net in shared Kafka topics where producers may evolve independently.
        configs.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // The delegate deserializer does the actual JSON-to-object conversion after the wrapper intercepts failures.
        // JsonDeserializer keeps payloads readable for a learning project, though schema-based formats can be stricter in production.
        configs.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Trusted packages control which Java types can be deserialized from Kafka payload metadata.
        // We use * here for convenience in a sandbox project, but production code should trust only the application's packages.
        // Leaving this too open in production is a security anti-pattern.
        configs.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        // The default value type is used when headers or type metadata are missing.
        // DemoTransaction is a sensible fallback because the guide's primary message examples are transaction-shaped events.
        // If you mix many schemas on one topic, be careful: an incorrect default type can hide contract mismatches.
        configs.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DemoTransaction.class);

        // earliest means a brand-new consumer group starts from the oldest available record.
        // This is ideal for demos and tests because learners can replay the full topic history.
        // In production, latest may be preferable for some real-time workloads that do not need historical replay.
        configs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Auto-commit periodically stores offsets in the background.
        // It is simple and fine for basic examples, but it can mark messages consumed before downstream side effects are fully durable.
        // Manual acknowledgment is safer when you need tighter control over failure handling.
        configs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);

        // Session timeout controls how long the broker waits before declaring the consumer dead.
        // 10 seconds is short enough for fast demos but long enough to tolerate brief pauses.
        // Too small a value can trigger noisy rebalances during GC pauses or local debugging.
        configs.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);

        // Heartbeat interval tells the consumer how often to signal liveness to the group coordinator.
        // 3 seconds works well with a 10-second session timeout and keeps failure detection reasonably quick.
        // A common rule is to keep heartbeats comfortably lower than the session timeout.
        configs.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        return configs;
    }

    @PostConstruct
    public void logConsumerConfigurationSummary() {
        log.info("Kafka consumer summary -> bootstrapServers={}, defaultGroup=demo-consumer-group, autoOffsetReset=earliest, sessionTimeoutMs=10000, heartbeatIntervalMs=3000", bootstrapServers);
        log.debug("Additional consumer modes -> manualAckGroup=manual-ack-group (autoCommit=false, AckMode.MANUAL), batchGroup=batch-consumer-group (autoCommit=false, AckMode.BATCH, maxPollRecords=100, concurrency=2)");
    }
}
