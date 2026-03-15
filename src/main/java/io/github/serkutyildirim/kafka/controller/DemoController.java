package io.github.serkutyildirim.kafka.controller;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.service.KafkaService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.SendResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * REST controller that demonstrates and tests all Kafka producer patterns implemented in this project.
 *
 * <h2>Controller purpose</h2>
 * <p>This controller exposes a set of HTTP endpoints that map directly to the different Kafka
 * producer strategies available in {@link KafkaService}. Each endpoint is designed to be a
 * self-contained, runnable experiment for understanding producer behaviour, trade-offs, and
 * failure modes.</p>
 *
 * <h2>Endpoint organisation by pattern type</h2>
 * <table border="1">
 *   <tr><th>Endpoint</th><th>Pattern</th><th>Use case</th></tr>
 *   <tr><td>POST /send-simple</td><td>Fire-and-forget</td><td>Non-critical messages / telemetry</td></tr>
 *   <tr><td>POST /send-reliable</td><td>Synchronous / blocking</td><td>Critical transactions</td></tr>
 *   <tr><td>POST /send-async</td><td>Async callback</td><td>High-throughput scenarios</td></tr>
 *   <tr><td>POST /send-batch</td><td>Transactional (exactly-once)</td><td>Atomic multi-message operations</td></tr>
 *   <tr><td>POST /send-with-key</td><td>Key-based partitioning</td><td>Ordered per-entity streams</td></tr>
 *   <tr><td>GET  /transaction/{id}/status</td><td>Status query (mock)</td><td>Monitoring / polling</td></tr>
 *   <tr><td>GET  /health</td><td>Connectivity check</td><td>K8s readiness / liveness probes</td></tr>
 *   <tr><td>POST /generate-test-data</td><td>Bulk test data generation</td><td>Observing consumer behaviour</td></tr>
 * </table>
 *
 * <h2>HTTP status code choices</h2>
 * <ul>
 *   <li><b>201 Created</b> — used when a Kafka record has been durably produced (send-simple,
 *       send-reliable, send-batch, send-with-key, generate-test-data). Signals that a new
 *       resource (the Kafka event) was created as a result of the request.</li>
 *   <li><b>202 Accepted</b> — used for send-async, where the HTTP response is returned before
 *       Kafka delivery is confirmed. The message is "accepted for processing".</li>
 *   <li><b>200 OK</b> — used for read-only queries (health, status lookup).</li>
 *   <li><b>400 Bad Request</b> — used for validation failures (constraint violations, empty key).</li>
 *   <li><b>500 Internal Server Error</b> — used when Kafka itself reports a failure.</li>
 *   <li><b>503 Service Unavailable</b> — used when the health check detects Kafka is unreachable.</li>
 * </ul>
 *
 * <h2>Example curl commands</h2>
 * <pre>{@code
 * # 1. Send simple message (fire-and-forget):
 * curl -X POST http://localhost:8090/api/demo/send-simple \
 *   -H "Content-Type: application/json" \
 *   -d '{"sourceId":"ACC001","targetId":"ACC002","amount":100.50,"currency":"USD"}'
 *
 * # 2. Send with broker confirmation:
 * curl -X POST http://localhost:8090/api/demo/send-reliable \
 *   -H "Content-Type: application/json" \
 *   -d '{"sourceId":"ACC003","targetId":"ACC004","amount":250.00,"currency":"EUR"}'
 *
 * # 3. Send asynchronously (202 Accepted):
 * curl -X POST http://localhost:8090/api/demo/send-async \
 *   -H "Content-Type: application/json" \
 *   -d '{"sourceId":"ACC001","targetId":"ACC002","amount":75.00,"currency":"GBP"}'
 *
 * # 4. Send batch (transactional):
 * curl -X POST http://localhost:8090/api/demo/send-batch \
 *   -H "Content-Type: application/json" \
 *   -d '[{"sourceId":"ACC005","targetId":"ACC006","amount":50.00,"currency":"USD"},
 *        {"sourceId":"ACC007","targetId":"ACC008","amount":75.00,"currency":"GBP"}]'
 *
 * # 5. Send with key (partition routing):
 * curl -X POST "http://localhost:8090/api/demo/send-with-key?key=USER001" \
 *   -H "Content-Type: application/json" \
 *   -d '{"sourceId":"USER001","targetId":"ACC009","amount":500.00,"currency":"USD"}'
 *
 * # 6. Check transaction status:
 * curl http://localhost:8090/api/demo/transaction/550e8400-e29b-41d4-a716-446655440000/status
 *
 * # 7. Generate test data:
 * curl -X POST "http://localhost:8090/api/demo/generate-test-data?count=20"
 *
 * # 8. Check health:
 * curl http://localhost:8090/api/demo/health
 * }</pre>
 *
 * <h2>Integration with Postman</h2>
 * <p>Import the collection by creating a new request to {@code http://localhost:8090/api/demo/*}
 * and adding the {@code Content-Type: application/json} header. All request bodies follow the
 * {@link DemoTransaction} schema shown in the curl examples above.</p>
 *
 * @author Serkut Yıldırım
 * @see KafkaService
 */
@RestController
@RequestMapping("/api/demo")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
@Slf4j
public class DemoController {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    /**
     * Central service layer that orchestrates producer selection and business validation.
     * All Kafka interactions are delegated to this service; the controller remains thin.
     */
    @Autowired
    private KafkaService kafkaService;

    /**
     * Kafka bootstrap servers, injected from application configuration.
     * Used exclusively by the health endpoint's {@link AdminClient} to verify connectivity.
     */
    @Value("${spring.kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    // =========================================================================
    // Endpoint 1 — POST /send-simple
    // =========================================================================

    /**
     * Tests the <b>fire-and-forget</b> producer pattern.
     *
     * <p><b>Use case:</b> Non-critical messages such as audit logs, click-stream events, or
     * telemetry data where occasional message loss is acceptable in exchange for maximum
     * throughput.</p>
     *
     * <p><b>Behaviour:</b> The method returns immediately after the record is enqueued in the
     * producer buffer. No broker acknowledgement is awaited; delivery failures are not reported
     * to the caller.</p>
     *
     * <p><b>HTTP 201 Created</b> is returned on the happy path because a Kafka event record
     * (a new resource) was created as a result of the request.</p>
     *
     * <p><b>Error strategy:</b> {@link IllegalArgumentException} (validation failure) → 400;
     * any other unexpected exception → 500.</p>
     *
     * @param transaction the transaction payload; validated with Bean Validation before sending
     * @return 201 with {@code messageId}, 400 on validation failure, 500 on unexpected error
     */
    @PostMapping("/send-simple")
    public ResponseEntity<Map<String, Object>> sendSimple(
            @RequestBody @Valid DemoTransaction transaction) {

        log.info("POST /send-simple — messageId={}", transaction.getMessageId());
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // Fire-and-forget: enqueues the record and returns without waiting for broker ACK.
            kafkaService.sendSimple(transaction);

            response.put("messageId", transaction.getMessageId());
            response.put("status", "SENT");
            response.put("pattern", "fire-and-forget");
            // 201: a Kafka event record was created
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("POST /send-simple failed — {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 2 — POST /send-reliable
    // =========================================================================

    /**
     * Tests the <b>synchronous producer with broker confirmation</b> pattern.
     *
     * <p><b>Use case:</b> Critical transactions (payments, order placements) where the caller
     * must know immediately whether the broker accepted the record. Suitable for low-volume,
     * high-importance writes.</p>
     *
     * <p><b>Behaviour:</b> Blocks the HTTP request thread until the Kafka broker acknowledges
     * the write. Returns the exact partition and offset assigned by the broker, which can be
     * used for idempotency checks or audit trails.</p>
     *
     * <p><b>HTTP 201 Created</b> — the Kafka event was durably written; partition and offset
     * serve as its "coordinates" in the topic.</p>
     *
     * <p><b>Error strategy:</b> {@link ExecutionException} or {@link InterruptedException}
     * from the blocking send → 500 with full error details including exception type.</p>
     *
     * @param transaction the transaction payload; validated with Bean Validation before sending
     * @return 201 with {@code messageId}, {@code partition}, and {@code offset};
     *         500 on broker or thread-interruption error
     */
    @PostMapping("/send-reliable")
    public ResponseEntity<Map<String, Object>> sendReliable(
            @RequestBody @Valid DemoTransaction transaction) {

        log.info("POST /send-reliable — messageId={}", transaction.getMessageId());
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // Synchronous (blocking) send: waits for broker ACK before returning.
            // The SendResult carries RecordMetadata with partition + offset.
            SendResult<String, DemoTransaction> result = kafkaService.sendReliable(transaction);

            response.put("messageId", transaction.getMessageId());
            response.put("partition", result.getRecordMetadata().partition());
            response.put("offset", result.getRecordMetadata().offset());
            response.put("pattern", "synchronous-reliable");
            // 201: record is durably stored on the broker
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (ExecutionException e) {
            // Broker rejected the record or a timeout occurred during the blocking wait.
            log.error("POST /send-reliable — ExecutionException: {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            response.put("exceptionType", "ExecutionException");
            response.put("cause", e.getCause() != null ? e.getCause().getMessage() : "unknown");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);

        } catch (InterruptedException e) {
            // The waiting thread was interrupted (e.g., application shutdown).
            log.warn("POST /send-reliable — InterruptedException", e);
            Thread.currentThread().interrupt(); // restore interrupt flag
            response.put("error", e.getMessage());
            response.put("exceptionType", "InterruptedException");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 3 — POST /send-async
    // =========================================================================

    /**
     * Tests the <b>asynchronous callback-based producer</b> pattern.
     *
     * <p><b>Use case:</b> High-throughput scenarios where blocking per message would exhaust
     * HTTP request threads. The caller gets an instant response; success or failure is handled
     * in a non-blocking callback within the producer.</p>
     *
     * <p><b>Behaviour:</b> Returns <em>immediately</em> without waiting for the
     * {@link CompletableFuture} to complete. The broker confirmation (or failure) is processed
     * asynchronously by the callback registered inside {@link io.github.serkutyildirim.kafka.producer.AsyncProducer}.</p>
     *
     * <p><b>HTTP 202 Accepted</b> is the correct code here: the request was received and
     * enqueued for processing, but the final outcome (delivery confirmation) is not yet known.</p>
     *
     * <p><b>Error strategy:</b> Validation failures caught before the async dispatch → 500 is
     * returned. Post-dispatch failures are handled entirely in the producer callback and are
     * observable only through logs/metrics.</p>
     *
     * @param transaction the transaction payload; validated with Bean Validation before sending
     * @return 202 Accepted with a "Processing asynchronously" message; 500 on pre-dispatch error
     */
    @PostMapping("/send-async")
    public ResponseEntity<Map<String, Object>> sendAsync(
            @RequestBody @Valid DemoTransaction transaction) {

        log.info("POST /send-async — messageId={}", transaction.getMessageId());
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // Dispatch the async send. The CompletableFuture is intentionally NOT awaited here.
            // Success/failure is observed through the callback registered in AsyncProducer.
            kafkaService.sendAsync(transaction);

            // 202 Accepted: request has been accepted for processing, outcome not yet determined.
            response.put("message", "Processing asynchronously");
            response.put("messageId", transaction.getMessageId());
            response.put("pattern", "async-callback");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

        } catch (Exception e) {
            log.error("POST /send-async failed before dispatch — {}", e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 4 — POST /send-batch
    // =========================================================================

    /**
     * Tests the <b>transactional producer with exactly-once semantics</b> pattern.
     *
     * <p><b>Use case:</b> Multi-step financial operations or any workflow where either all
     * messages must be written or none should be visible to consumers. Atomicity is guaranteed
     * at the Kafka broker level.</p>
     *
     * <p><b>Behaviour:</b> All transactions in the list are published inside a single Kafka
     * transaction. If any send fails, the entire transaction is aborted and consumers configured
     * with {@code isolation.level=read_committed} will not see any of the messages.</p>
     *
     * <p><b>HTTP 201 Created</b> — all records were durably committed as one atomic unit.
     * HTTP 500 — the batch failed and was rolled back entirely.</p>
     *
     * <p><b>Throughput note:</b> Transactional sends are slower than simple sends because of
     * the two-phase commit overhead. Use this pattern only when atomicity is a hard requirement.</p>
     *
     * @param transactions the list of transactions to send atomically; must not be empty
     * @return 201 with {@code batchSize} and {@code success=true};
     *         500 with {@code success=false} if the transaction was rolled back
     */
    @PostMapping("/send-batch")
    public ResponseEntity<Map<String, Object>> sendBatch(
            @RequestBody @Valid List<DemoTransaction> transactions) {

        log.info("POST /send-batch — batchSize={}", transactions.size());
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // Transactional send: all-or-nothing. Returns true if the batch was committed.
            boolean success = kafkaService.sendBatch(transactions);

            response.put("batchSize", transactions.size());
            response.put("success", success);
            response.put("pattern", "transactional-exactly-once");

            if (success) {
                // 201: new Kafka event records were atomically created
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                response.put("error", "Transactional batch was rolled back — no messages were committed");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }

        } catch (Exception e) {
            log.error("POST /send-batch failed — {}", e.getMessage(), e);
            response.put("batchSize", transactions.size());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 5 — POST /send-with-key
    // =========================================================================

    /**
     * Tests <b>partition routing with a message key</b>.
     *
     * <p><b>Use case:</b> Maintaining strict message ordering per entity (e.g., per account,
     * per user). Kafka hashes the key to select a partition; all records sharing the same key
     * are written to the same partition, preserving FIFO order for that key.</p>
     *
     * <p><b>Behaviour:</b> The key is validated before dispatch. An empty or blank key would
     * silently disable key-based partitioning, so an {@link IllegalArgumentException} is
     * thrown proactively to surface that mistake early.</p>
     *
     * <p><b>HTTP 201 Created</b> — record enqueued with the specified routing key.<br>
     * HTTP 400 Bad Request — key was null or empty.</p>
     *
     * <p><b>Example:</b> Use {@code accountId} as key so that all events for a single account
     * arrive at a single partition, enabling stateful per-account stream processing.</p>
     *
     * @param key         the Kafka partition routing key (e.g., account ID or user ID);
     *                    must not be null or blank
     * @param transaction the transaction payload; validated with Bean Validation before sending
     * @return 201 with {@code messageId} and {@code key};
     *         400 if the key is empty; 500 on unexpected error
     */
    @PostMapping("/send-with-key")
    public ResponseEntity<Map<String, Object>> sendWithKey(
            @RequestParam String key,
            @RequestBody @Valid DemoTransaction transaction) {

        log.info("POST /send-with-key — key={} messageId={}", key, transaction.getMessageId());
        Map<String, Object> response = new LinkedHashMap<>();

        // Validate key eagerly so the error message is clear and actionable.
        if (key == null || key.isBlank()) {
            response.put("error", "Request parameter 'key' must not be null or blank. "
                    + "Provide a meaningful entity ID (e.g., accountId) to enable partition routing.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Key-based send: Kafka hashes the key to select a partition.
            // The CompletableFuture is dispatched but not awaited — caller gets 201 immediately.
            kafkaService.sendWithKey(key, transaction);

            response.put("messageId", transaction.getMessageId());
            response.put("key", key);
            response.put("pattern", "key-based-partitioning");
            // 201: new Kafka event record created with explicit routing key
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("POST /send-with-key failed — key={} error={}", key, e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 6 — GET /transaction/{transactionId}/status
    // =========================================================================

    /**
     * Checks the processing status of a transaction (mock implementation).
     *
     * <p><b>Current behaviour:</b> Always returns {@code "COMPLETED"} for demonstration
     * purposes. The endpoint is wired up and callable; it just delegates to a mock stub
     * in {@link KafkaService#getTransactionStatus(String)}.</p>
     *
     * <p><b>TODO:</b> Integrate with a real status-tracking store:</p>
     * <ul>
     *   <li>Relational DB: query a {@code transaction_status} table updated by event consumers.</li>
     *   <li>Kafka Streams: materialise a KTable of the latest status per transaction ID.</li>
     *   <li>CQRS read model: serve queries from a dedicated read-side projection.</li>
     * </ul>
     *
     * @param transactionId the UUID string of the transaction to look up
     * @return 200 with {@code transactionId} and {@code status}; 500 on unexpected error
     */
    @GetMapping("/transaction/{transactionId}/status")
    public ResponseEntity<Map<String, Object>> getTransactionStatus(
            @PathVariable String transactionId) {

        log.info("GET /transaction/{}/status", transactionId);
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            // TODO: replace with real persistence query or Kafka Streams interactive query.
            String status = kafkaService.getTransactionStatus(transactionId);

            response.put("transactionId", transactionId);
            response.put("status", status);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("GET /transaction/{}/status failed — {}", transactionId, e.getMessage(), e);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Endpoint 7 — GET /health
    // =========================================================================

    /**
     * Health check endpoint for monitoring Kafka connectivity.
     *
     * <p><b>Kubernetes readiness / liveness probes:</b> Point the probe at this endpoint.
     * A 200 response means the application can reach Kafka; a 503 means Kafka is unavailable
     * and the pod should not receive traffic (readiness) or should be restarted (liveness).</p>
     *
     * <p><b>Implementation:</b> An {@link AdminClient} is created per-call (intentionally, to
     * test live connectivity rather than relying on a cached connection) to list available topics.
     * The result includes:</p>
     * <ul>
     *   <li>{@code kafka_status} — {@code "UP"} or {@code "DOWN"}</li>
     *   <li>{@code topics} — names of all topics visible to the admin client</li>
     *   <li>{@code timestamp} — ISO-8601 timestamp of the check</li>
     * </ul>
     *
     * @return 200 with health details if Kafka is reachable; 503 if unreachable
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("GET /health — checking Kafka connectivity");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());

        // Build a short-lived AdminClient purely for the connectivity test.
        // A 3-second request timeout prevents the health probe from hanging.
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");

        try (AdminClient adminClient = AdminClient.create(adminProps)) {
            // ListTopics with a short timeout so the health check returns quickly.
            Set<String> topicNames = adminClient
                    .listTopics(new ListTopicsOptions().timeoutMs(3000))
                    .names()
                    .get();

            response.put("kafka_status", "UP");
            response.put("topics", topicNames.stream().sorted().collect(Collectors.toList()));
            // 200: Kafka is healthy and reachable
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.warn("GET /health — Kafka unreachable: {}", e.getMessage());
            response.put("kafka_status", "DOWN");
            response.put("error", e.getMessage());
            response.put("topics", Collections.emptyList());
            // 503: service is running but cannot reach Kafka
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    // =========================================================================
    // Endpoint 8 — POST /generate-test-data
    // =========================================================================

    /**
     * Generates a configurable number of sample {@link DemoTransaction} objects and sends them
     * through a mix of producer patterns to exercise the full system.
     *
     * <p><b>Use case:</b> Quickly populate a Kafka topic with realistic-looking transaction data
     * to observe consumer group behaviour, partition assignment, lag accumulation, or Kafka UI
     * dashboards without manually crafting individual curl commands.</p>
     *
     * <p><b>Sending strategy:</b></p>
     * <ul>
     *   <li>First two transactions (if {@code count >= 2}) are sent with {@code sendReliable}
     *       as a warm-up to confirm Kafka is reachable.</li>
     *   <li>Remaining transactions are sent with {@code sendSimple} for speed.</li>
     *   <li>Every 5th transaction (after the first two) is sent with {@code sendWithKey}
     *       using the {@code sourceId} as the routing key to demonstrate partition routing.</li>
     * </ul>
     *
     * <p><b>HTTP 201 Created</b> — transactions generated and dispatched successfully.</p>
     *
     * @param count number of transactions to generate; defaults to 10
     * @return 201 with {@code generatedCount}; 500 on unexpected error
     */
    @PostMapping("/generate-test-data")
    public ResponseEntity<Map<String, Object>> generateTestData(
            @RequestParam(defaultValue = "10") int count) {

        log.info("POST /generate-test-data — count={}", count);
        Map<String, Object> response = new LinkedHashMap<>();

        // Guard against unreasonably large batch requests.
        if (count <= 0 || count > 1000) {
            response.put("error", "Parameter 'count' must be between 1 and 1000 (inclusive).");
            return ResponseEntity.badRequest().body(response);
        }

        // Currency pool for realistic-looking test data.
        String[] currencies = {"USD", "EUR", "GBP"};
        Random random = new Random();
        int sent = 0;

        try {
            for (int i = 0; i < count; i++) {
                // Build a realistic transaction with randomised but plausible field values.
                String sourceId = "ACC" + String.format("%03d", random.nextInt(900) + 100);
                String targetId = "ACC" + String.format("%03d", random.nextInt(900) + 100);
                BigDecimal amount = BigDecimal.valueOf(10 + random.nextDouble() * 990)
                        .setScale(2, RoundingMode.HALF_UP);
                String currency = currencies[random.nextInt(currencies.length)];

                DemoTransaction transaction = kafkaService.createTransaction(
                        sourceId, targetId, amount, currency);

                if (i < 2) {
                    // Send first two reliably as a connectivity warm-up.
                    try {
                        kafkaService.sendReliable(transaction);
                    } catch (ExecutionException | InterruptedException ex) {
                        log.warn("generate-test-data: reliable send #{} failed — {}", i, ex.getMessage());
                        if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    }
                } else if (i % 5 == 0) {
                    // Every 5th message: key-based routing using sourceId for ordered delivery.
                    kafkaService.sendWithKey(sourceId, transaction);
                } else {
                    // Remaining messages: fire-and-forget for maximum throughput.
                    kafkaService.sendSimple(transaction);
                }
                sent++;
            }

            response.put("generatedCount", sent);
            response.put("requestedCount", count);
            // 201: test event records were created in Kafka
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("POST /generate-test-data failed after {} sends — {}", sent, e.getMessage(), e);
            response.put("generatedCount", sent);
            response.put("requestedCount", count);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =========================================================================
    // Global exception handlers
    // =========================================================================

    /**
     * Handles {@link IllegalArgumentException} thrown anywhere in this controller.
     *
     * <p>Business validation failures (e.g., empty partition key, negative amount) propagate
     * as {@code IllegalArgumentException}. Returning 400 rather than 500 signals to the
     * client that the error is in the <em>request</em>, not in the server.</p>
     *
     * @param ex the caught exception
     * @return 400 Bad Request with the exception message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException handled: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", "IllegalArgumentException");
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Catch-all handler for any {@link Exception} not handled by a more specific handler.
     *
     * <p>Returning 500 here prevents unhandled exceptions from leaking stack traces to the
     * client while still logging the full detail server-side.</p>
     *
     * @param ex the caught exception
     * @return 500 Internal Server Error with the exception message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("type", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // =========================================================================
    // Validation error handler
    // =========================================================================

    /**
     * Handles Bean Validation failures triggered by {@code @Valid} on request bodies.
     *
     * <p>Spring MVC throws {@link MethodArgumentNotValidException} when a {@code @Valid}
     * annotated request body fails constraint validation. This handler extracts the per-field
     * errors and returns them as a structured map, making it easy for API clients to show
     * field-level feedback to end users.</p>
     *
     * <p><b>Response structure example:</b></p>
     * <pre>{@code
     * {
     *   "validationErrors": {
     *     "amount": "must be greater than 0",
     *     "sourceId": "must not be blank"
     *   }
     * }
     * }</pre>
     *
     * @param ex the validation exception containing all field errors
     * @return 400 Bad Request with a {@code validationErrors} map of fieldName → message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field errors into a fieldName → errorMessage map.
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        // If the same field has multiple errors, concatenate them.
                        (msg1, msg2) -> msg1 + "; " + msg2
                ));

        log.warn("Validation failed: {}", fieldErrors);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("validationErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }
}
