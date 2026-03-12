package io.github.serkutyildirim.kafka.service;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.model.MessageStatus;
import io.github.serkutyildirim.kafka.producer.AsyncProducer;
import io.github.serkutyildirim.kafka.producer.PartitionedProducer;
import io.github.serkutyildirim.kafka.producer.ReliableProducer;
import io.github.serkutyildirim.kafka.producer.SimpleProducer;
import io.github.serkutyildirim.kafka.producer.TransactionalProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Central service layer that orchestrates all Kafka producer interactions for the demo application.
 *
 * <h2>Service layer design principles</h2>
 * <ul>
 *   <li><b>Abstraction:</b> Controllers and other callers deal only with business concepts
 *       ({@link DemoTransaction}, amount, currency). The complexity of choosing and configuring
 *       a producer pattern is hidden inside this class.</li>
 *   <li><b>Separation of concerns:</b> Validation, business logic, and Kafka interaction are
 *       kept separate. Producers focus on <em>how</em> to send; this service focuses on
 *       <em>what</em> to send and <em>whether</em> it is valid.</li>
 *   <li><b>Centralized Kafka logic:</b> Having one service class means that changes to
 *       topic names, producer selection, or retry strategy touch only one place rather than
 *       every controller method.</li>
 *   <li><b>Easy pattern switching:</b> Each public method delegates to a different producer
 *       implementation. Switching from fire-and-forget to reliable send requires changing
 *       only the called method, not the controller.</li>
 * </ul>
 *
 * <h2>ACID properties in distributed systems</h2>
 * <p>Traditional RDBMS ACID guarantees do not automatically extend across microservices and
 * message brokers. Kafka provides at-least-once or exactly-once delivery semantics at the
 * broker level, but end-to-end ACID must be achieved via patterns such as:</p>
 * <ul>
 *   <li><b>Outbox pattern:</b> Write to DB and Kafka inside the same local transaction using a
 *       dedicated outbox table and a CDC relay.</li>
 *   <li><b>Saga pattern:</b> Orchestrate a sequence of local transactions across services with
 *       compensating transactions to roll back on failure.</li>
 *   <li><b>Two-phase commit:</b> Generally avoided in Kafka architectures due to performance cost.</li>
 * </ul>
 *
 * <h2>Compensating transactions for rollbacks</h2>
 * <p>Because Kafka messages are immutable once written, "rollback" in an event-driven system
 * means publishing a <em>compensating event</em> that semantically reverses the original operation
 * (e.g., a REFUND event to cancel a PAYMENT event). The {@link #sendBatch} method demonstrates
 * Kafka's transactional producer as a partial mitigation at the broker level.</p>
 *
 * <h2>Idempotency considerations</h2>
 * <p>In at-least-once delivery, the same transaction may be produced more than once (network retry,
 * producer restart). Idempotency keys (the {@code messageId} / UUID on every
 * {@link DemoTransaction}) allow consumers and downstream services to detect and discard duplicates.
 * Duplicate detection at the producer side is handled by
 * {@link MessageValidationService#checkDuplicateTransaction(String)}.</p>
 *
 * <h2>TODO — Production enhancements</h2>
 * <ul>
 *   <li>Persist every transaction to a database (audit log / transaction history table).</li>
 *   <li>Track status transitions (CREATED → PROCESSING → COMPLETED/FAILED) in a state store.</li>
 *   <li>Integrate with a metrics library (Micrometer) to emit send-rate, latency, and error counters.</li>
 *   <li>Add distributed tracing (OpenTelemetry) so each Kafka message carries a trace context header.</li>
 *   <li>Implement a proper {@code getTransactionStatus} backed by a database or a Kafka Streams
 *       materialized view instead of the current mock.</li>
 * </ul>
 *
 * @author Serkut Yıldırım
 * @see SimpleProducer
 * @see ReliableProducer
 * @see AsyncProducer
 * @see TransactionalProducer
 * @see PartitionedProducer
 * @see MessageValidationService
 */
@Service
@Slf4j
public class KafkaService {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    /**
     * Fire-and-forget producer: fastest, least reliable.
     * Use for non-critical events where raw throughput matters more than delivery guarantees.
     */
    @Autowired
    private SimpleProducer simpleProducer;

    /**
     * Synchronous (blocking) producer: reliable, slower.
     * Use for critical transactions where the caller must know whether Kafka accepted the record.
     */
    @Autowired
    private ReliableProducer reliableProducer;

    /**
     * Asynchronous callback-based producer: fast and observable.
     * Balances throughput with failure visibility through non-blocking callbacks.
     */
    @Autowired
    private AsyncProducer asyncProducer;

    /**
     * Transactional producer: atomic batch, exactly-once semantics.
     * Use when a group of Kafka writes must all succeed or all be rolled back together.
     */
    @Autowired
    private TransactionalProducer transactionalProducer;

    /**
     * Key-based partitioned producer: guarantees ordering per key.
     * Use when all events for the same entity (e.g., one account) must be processed in order.
     */
    @Autowired
    private PartitionedProducer partitionedProducer;

    /**
     * Business-rule validation service.
     * Validation is deliberately called here (service layer) rather than inside producers
     * so that producers remain generic and reusable across different contexts.
     *
     * <p><b>Separation of concerns:</b> A producer's only job is to send bytes to Kafka.
     * Deciding whether those bytes represent a valid business event is the service layer's job.</p>
     */
    @Autowired
    private MessageValidationService validationService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link DemoTransaction} using the fire-and-forget (simple) producer pattern.
     *
     * <p>This is the fastest send path. The calling thread returns immediately after enqueuing
     * the record in the producer buffer. Broker acknowledgment is not waited for, so delivery
     * failures may go unobserved by the caller.</p>
     *
     * <p><b>Validation:</b> The transaction is validated before sending. If validation fails,
     * an {@link IllegalArgumentException} is thrown and nothing is sent to Kafka. This is the
     * "fail fast" principle — cheaper to reject early than to handle a poison message later.</p>
     *
     * <p><b>When to use:</b> Metrics, telemetry, activity logs, non-critical notifications.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * DemoTransaction tx = kafkaService.createTransaction("ACC-001", "ACC-002",
     *                         new BigDecimal("50.00"), "USD");
     * kafkaService.sendSimple(tx);
     * }</pre>
     *
     * @param transaction the transaction to send; must not be {@code null} and must pass validation
     * @throws IllegalArgumentException if the transaction fails business-rule validation
     */
    public void sendSimple(DemoTransaction transaction) {
        // Validation happens here (service layer) so producers stay free of business logic.
        // Idempotency consideration: if this call is retried by the caller after a timeout,
        // checkDuplicateTransaction will catch it once a real implementation is in place.
        validationService.validateTransaction(transaction);

        simpleProducer.send(transaction);

        log.info("Sent message using simple producer: {}", transaction.getMessageId());
    }

    /**
     * Sends a {@link DemoTransaction} using the synchronous reliable producer pattern and blocks
     * until the broker acknowledges the write.
     *
     * <p>This method returns only after Kafka confirms that the record has been written to the
     * leader partition. It is the safest single-message send but also the slowest because the
     * calling thread is blocked for the entire network round-trip.</p>
     *
     * <p><b>When to use:</b> Low-volume, high-criticality events where the caller must fail fast
     * if Kafka is unavailable (e.g., order placement, payment initiation).</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * SendResult<String, DemoTransaction> result =
     *         kafkaService.sendReliable(transaction);
     * long offset = result.getRecordMetadata().offset();
     * }</pre>
     *
     * @param transaction the transaction to send; must not be {@code null} and must pass validation
     * @return the {@link SendResult} containing partition and offset metadata from the broker
     * @throws IllegalArgumentException if the transaction fails business-rule validation
     * @throws ExecutionException       if the Kafka broker rejects the record or a timeout occurs
     * @throws InterruptedException     if the waiting thread is interrupted
     */
    public SendResult<String, DemoTransaction> sendReliable(DemoTransaction transaction)
            throws ExecutionException, InterruptedException {
        // Validate before blocking — no point waiting for a broker response for invalid data.
        validationService.validateTransaction(transaction);

        SendResult<String, DemoTransaction> result = reliableProducer.sendWithConfirmation(transaction);

        log.info("Reliable send confirmed for messageId={}: partition={} offset={}",
                transaction.getMessageId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        return result;
    }

    /**
     * Sends a {@link DemoTransaction} using the asynchronous callback producer pattern.
     *
     * <p>The calling thread returns immediately with a {@link CompletableFuture}. The caller
     * can attach callbacks to observe success or failure without blocking.</p>
     *
     * <p><b>When to use:</b> High-throughput flows where blocking per message would exhaust
     * request threads; also useful when the caller needs to react to success/failure asynchronously.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * kafkaService.sendAsync(transaction)
     *     .thenAccept(r -> log.info("offset={}", r.getRecordMetadata().offset()))
     *     .exceptionally(ex -> { log.error("Send failed", ex); return null; });
     * }</pre>
     *
     * @param transaction the transaction to send; must not be {@code null} and must pass validation
     * @return a {@link CompletableFuture} that completes with the {@link SendResult} on success
     *         or completes exceptionally on failure
     * @throws IllegalArgumentException if the transaction fails business-rule validation
     */
    public CompletableFuture<SendResult<String, DemoTransaction>> sendAsync(DemoTransaction transaction) {
        // Validate synchronously before dispatching the async send.
        // Even though the send itself is non-blocking, validation is cheap and must happen first.
        validationService.validateTransaction(transaction);

        return asyncProducer.sendAsync(transaction);
    }

    /**
     * Sends a batch of {@link DemoTransaction} objects transactionally (all-or-nothing semantics).
     *
     * <p>All messages in the list are published inside a single Kafka transaction. If any
     * individual send fails, the entire transaction is aborted and no messages become visible
     * to consumers configured with {@code isolation.level=read_committed}.</p>
     *
     * <p><b>ACID in Kafka:</b> The Kafka transactional producer guarantees atomicity at the
     * broker level — either all records in the transaction are committed or none are. However,
     * end-to-end ACID across a database write and a Kafka publish still requires the outbox
     * pattern or a saga.</p>
     *
     * <p><b>Compensating transactions:</b> If downstream processing fails after this batch is
     * committed, a compensating event (e.g., REVERSAL) must be published. Kafka messages are
     * immutable once written; rollback means publishing a new corrective event.</p>
     *
     * <p><b>When to use:</b> Multi-step financial workflows, bulk transfers, any case where a
     * partial write would leave the system in an inconsistent state.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * boolean ok = kafkaService.sendBatch(List.of(tx1, tx2, tx3));
     * if (!ok) { // handle batch failure }
     * }</pre>
     *
     * @param transactions the list of transactions to send atomically; must not be {@code null} or empty
     * @return {@code true} if the entire batch was committed successfully; {@code false} if the
     *         list is empty or if the transactional send fails
     * @throws IllegalArgumentException if any transaction in the list fails business-rule validation
     */
    public boolean sendBatch(List<DemoTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            log.warn("sendBatch called with null or empty list — skipping");
            return false;
        }

        // Validate every transaction before attempting the transactional send.
        // Fail fast: reject the whole batch if even one transaction is invalid,
        // rather than discovering the problem mid-transaction and triggering an unnecessary rollback.
        // TODO (production): consider validating in parallel with CompletableFuture for large batches.
        for (DemoTransaction transaction : transactions) {
            validationService.validateTransaction(transaction);
        }

        boolean success = transactionalProducer.sendTransactional(transactions);

        // TODO (production): persist the batch result to a database for audit logging.
        // TODO (production): emit a Micrometer counter for batch send success/failure.
        log.info("Batch of {} messages sent transactionally — success={}", transactions.size(), success);

        return success;
    }

    /**
     * Sends a {@link DemoTransaction} to a Kafka partition determined by the given routing key.
     *
     * <p>Kafka hashes the key to select a partition, guaranteeing that all records with the
     * same key land on the same partition. Within that partition, ordering is preserved.
     * This is essential for per-account or per-entity ordering requirements.</p>
     *
     * <p><b>Hot-key warning:</b> If one key generates far more traffic than others, its partition
     * becomes a bottleneck. Monitor partition lag and rebalance key distribution if needed.</p>
     *
     * <p><b>When to use:</b> Account-centric workflows, user-event streams, any domain where
     * ordering per entity matters more than even distribution.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * kafkaService.sendWithKey("ACC-001", transaction)
     *     .thenAccept(r -> log.info("partition={}", r.getRecordMetadata().partition()));
     * }</pre>
     *
     * @param key         the Kafka partition key (typically an account or entity ID);
     *                    must not be {@code null} or blank
     * @param transaction the transaction to send; must not be {@code null} and must pass validation
     * @return a {@link CompletableFuture} that completes with the {@link SendResult} on success
     *         or completes exceptionally on failure
     * @throws IllegalArgumentException if the key is blank or if the transaction fails validation
     */
    public CompletableFuture<SendResult<String, DemoTransaction>> sendWithKey(
            String key, DemoTransaction transaction) {

        // Validate the business payload first (separation of concerns).
        validationService.validateTransaction(transaction);

        // Key validation: a null/blank key would disable Kafka's key-based partitioning,
        // silently defeating the ordering guarantee this method is designed to provide.
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Partition key must not be null or blank. "
                            + "Provide a meaningful entity ID (e.g., account ID) to ensure ordering.");
        }

        log.info("Message sent with key: {}", key);

        return partitionedProducer.sendWithKey(key, transaction);
    }

    /**
     * Factory method that constructs a fully initialized {@link DemoTransaction} and validates it
     * before returning.
     *
     * <p>Using a factory method in the service layer rather than constructing DTOs in controllers
     * or tests ensures that every transaction has consistent metadata (UUID, timestamp, status)
     * and passes validation at the point of creation.</p>
     *
     * <p><b>Builder pattern benefits:</b> Fluent construction makes intent clear; optional fields
     * can be omitted without overloaded constructors; Lombok's {@code @SuperBuilder} propagates
     * inherited fields from {@link io.github.serkutyildirim.kafka.model.BaseMessage} automatically.</p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * DemoTransaction tx = kafkaService.createTransaction(
     *         "ACC-001", "ACC-002", new BigDecimal("250.00"), "EUR");
     * kafkaService.sendSimple(tx);
     * }</pre>
     *
     * @param sourceId  the source account identifier; must not be blank
     * @param targetId  the target account identifier; must not be blank
     * @param amount    the transfer amount; must be positive
     * @param currency  the ISO 4217 currency code (e.g., {@code "USD"}, {@code "EUR"})
     * @return a validated {@link DemoTransaction} ready to be sent to a Kafka topic
     * @throws IllegalArgumentException if the constructed transaction fails business-rule validation
     */
    public DemoTransaction createTransaction(
            String sourceId, String targetId, BigDecimal amount, String currency) {

        // Build the transaction using the Lombok-generated SuperBuilder.
        // BaseMessage auto-populates messageId (UUID) and timestamp (Instant.now()) via builder defaults.
        // TODO (production): pass a caller-supplied idempotency key as messageId to enable
        //                    safe retries from the HTTP layer without duplicate processing.
        DemoTransaction transaction = DemoTransaction.builder()
                .messageId(UUID.randomUUID())
                .timestamp(Instant.now())
                .sourceId(sourceId)
                .targetId(targetId)
                .amount(amount)
                .currency(currency)
                .status(MessageStatus.CREATED)
                .build();

        // Validate immediately after construction (fail fast).
        // This guarantees that callers always receive a usable object or a clear error.
        // TODO (production): also call validationService.checkDuplicateTransaction(transaction.getMessageId())
        //                    to prevent replaying the same logical operation.
        validationService.validateTransaction(transaction);

        log.debug("Created and validated transaction messageId={} sourceId={} targetId={} amount={} currency={}",
                transaction.getMessageId(), sourceId, targetId, amount, currency);

        return transaction;
    }

    /**
     * Returns the current processing status of a transaction by its identifier.
     *
     * <p><b>Current implementation:</b> This is a <em>mock stub</em> that always returns
     * {@code "COMPLETED"}. It demonstrates the API shape without requiring a database.</p>
     *
     * <p><b>TODO — Production integration:</b><br>
     * Replace this stub with one of the following approaches:
     * <ul>
     *   <li><b>Relational database:</b> Query a {@code transaction_status} table keyed on
     *       {@code transaction_id}; updated by consumers as they process each event.</li>
     *   <li><b>Kafka Streams state store:</b> Maintain a materialized view of the latest status
     *       per transaction using a KTable or GlobalKTable; query via interactive queries.</li>
     *   <li><b>CQRS read model:</b> Maintain a separate read-optimized projection updated by
     *       event handlers; serve status queries from that projection without touching the
     *       write-side Kafka producers.</li>
     * </ul>
     * </p>
     *
     * <p><b>Example usage:</b></p>
     * <pre>{@code
     * String status = kafkaService.getTransactionStatus("550e8400-e29b-41d4-a716-446655440000");
     * // Returns "COMPLETED" in the demo implementation
     * }</pre>
     *
     * @param transactionId the UUID string identifier of the transaction whose status is requested
     * @return the status string ({@code "COMPLETED"} in the mock; a {@link MessageStatus} name
     *         in a real implementation)
     */
    public String getTransactionStatus(String transactionId) {
        // TODO: Integrate with actual database to track real transaction status.
        //       Example: return transactionRepository.findById(transactionId)
        //                    .map(tx -> tx.getStatus().name())
        //                    .orElse("NOT_FOUND");
        //
        // TODO (production): add caching (e.g., @Cacheable) for frequently queried transaction IDs
        //                    to reduce database load on high-read endpoints.
        log.info("Status check for transaction: {}", transactionId);

        // Mock implementation: always returns COMPLETED so the demo API works end-to-end.
        return "COMPLETED";
    }
}
