package io.github.serkutyildirim.kafka.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central Kafka producer configuration for the learning project.
 *
 * <p><b>Producer configuration overview:</b></p>
 * <ul>
 *   <li>The standard producer is the default choice for most demo sends because it balances reliability and throughput well.</li>
 *   <li>The transactional producer adds stronger delivery guarantees, but it is more expensive and should only be used when the workflow really needs it.</li>
 * </ul>
 *
 * <p><b>Idempotence and why it matters:</b></p>
 * <ul>
 *   <li>Idempotence prevents duplicate writes caused by retries after transient network or broker issues.</li>
 *   <li>It is one of the simplest reliability upgrades you can enable, and it is required for Kafka transactions.</li>
 * </ul>
 *
 * <p><b>Acknowledgment strategies:</b></p>
 * <ul>
 *   <li>{@code acks=all} waits for all in-sync replicas and offers the strongest durability among Kafka's built-in ack modes.</li>
 *   <li>It is slightly slower than {@code 1} or {@code 0}, but it is the safest educational default when discussing reliable producers.</li>
 * </ul>
 *
 * <p><b>Batching and compression:</b></p>
 * <ul>
 *   <li>A small linger value lets Kafka batch nearby records together for better network efficiency.</li>
 *   <li>Compression reduces bandwidth and broker disk usage, but it adds some CPU cost on producer and broker sides.</li>
 * </ul>
 *
 * <p><b>When to use each producer type:</b></p>
 * <ul>
 *   <li>Use the standard producer for most demo events, logs, notifications, and fire-and-forget learning scenarios.</li>
 *   <li>Use the transactional producer when a group of Kafka writes must commit or roll back together, especially in exactly-once pipelines.</li>
 * </ul>
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @Value("${app.kafka.transaction-id-prefix:kafka-demo-tx-}")
    private String transactionIdPrefix;

    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configs = standardProducerConfigs();
        log.info("Creating standard Kafka producer factory for bootstrap servers {}", bootstrapServers);
        return new DefaultKafkaProducerFactory<>(configs);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate() {
        log.info("Creating standard KafkaTemplate bean");
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, Object> transactionalProducerFactory() {
        Map<String, Object> configs = transactionalProducerConfigs();
        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(configs);
        producerFactory.setTransactionIdPrefix(transactionIdPrefix);
        log.info("Creating transactional Kafka producer factory with transactionIdPrefix={}", transactionIdPrefix);
        return producerFactory;
    }

    @Bean(name = "transactionalKafkaTemplate")
    @Qualifier("transactionalKafkaTemplate")
    public KafkaTemplate<String, Object> transactionalKafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(transactionalProducerFactory());
        // Exactly-once semantics depend on stable transactional IDs so Kafka can fence zombie producers.
        // Spring uses this prefix to generate concrete transactional IDs per producer instance.
        // Transactions add coordination overhead, so expect roughly 20-30% lower throughput than non-transactional sends.
        // Use this template only when multiple Kafka writes must commit atomically; avoid paying the cost for simple events.
        template.setTransactionIdPrefix(transactionIdPrefix);
        log.info("Creating transactional KafkaTemplate bean");
        return template;
    }

    private Map<String, Object> standardProducerConfigs() {
        Map<String, Object> configs = new LinkedHashMap<>();

        // Kafka bootstrap servers tell the producer where the cluster entry point is.
        // We inject it from configuration so local, test, and production environments can differ without code changes.
        // Hard-coding broker addresses is a common mistake that makes deployments brittle.
        configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // StringSerializer converts human-readable keys into bytes.
        // String keys are a good default because they are easy to log and often double as partition keys.
        // Keep key formats stable because changing them can change partition distribution and ordering behavior.
        configs.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // JsonSerializer converts Java objects into JSON so the learning project can send rich message payloads.
        // JSON is easy to inspect during learning, though it is larger and slower than compact binary formats such as Avro or Protobuf.
        // This trade-off is worth it here because readability matters more than wire efficiency.
        configs.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // acks=all means the leader waits for every in-sync replica before acknowledging the write.
        // We choose the safest durability mode to demonstrate reliable producer behavior.
        // The trade-off is slightly higher latency than acks=1 or acks=0.
        configs.put(ProducerConfig.ACKS_CONFIG, "all");

        // Retries let the producer recover from transient broker/network failures automatically.
        // Three retries is enough to show the concept without hiding persistent failures for too long.
        // Too many retries can increase duplicate risk when idempotence is off and can also extend end-to-end latency.
        configs.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Idempotence prevents duplicate records caused by retrying the same send request.
        // It materially improves reliability and is one of the best default settings for business events.
        // It slightly constrains some producer settings, but the safety gain usually outweighs the cost.
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // linger.ms lets the producer wait briefly so more records can be batched together.
        // 10 ms is a small delay that often improves throughput significantly in busy applications.
        // The trade-off is a tiny increase in publish latency for low-volume traffic.
        configs.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        // snappy compression reduces network traffic and broker disk usage while staying relatively CPU-efficient.
        // It is a good middle ground for demo workloads because it is widely supported and fast.
        // Compression saves I/O but adds CPU work during compression and decompression.
        configs.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Max in-flight requests controls how many unacknowledged requests can exist per connection.
        // Keeping it at 5 preserves ordering guarantees together with idempotence while still allowing pipelining.
        // Setting this too high can complicate ordering guarantees when retries happen.
        configs.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        return configs;
    }

    private Map<String, Object> transactionalProducerConfigs() {
        Map<String, Object> configs = new LinkedHashMap<>(standardProducerConfigs());

        // A transactional.id uniquely identifies the transactional producer instance to Kafka.
        // We derive it from a stable prefix plus a UUID so multiple application instances do not fence each other accidentally.
        // Reusing the same transactional ID across different live instances is a common production mistake.
        configs.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionIdPrefix + UUID.randomUUID());

        // Transactions require idempotence so Kafka can safely de-duplicate retried writes inside a transaction boundary.
        // We set it explicitly again here so the requirement is visible when reading the transactional config in isolation.
        // Disabling this would break transactional producer semantics.
        configs.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return configs;
    }

    @PostConstruct
    public void logProducerConfigurationSummary() {
        log.info("Kafka producer summary -> bootstrapServers={}, acks=all, retries=3, idempotence=true, lingerMs=10, compression=snappy, maxInFlight=5", bootstrapServers);
        log.debug("Transactional producer summary -> transactionIdPrefix={}, exactly-once capable with higher latency and lower throughput than the standard producer", transactionIdPrefix);
    }
}
