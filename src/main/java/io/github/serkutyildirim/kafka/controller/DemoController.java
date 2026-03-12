package io.github.serkutyildirim.kafka.controller;

import io.github.serkutyildirim.kafka.model.DemoNotification;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.service.KafkaService;
import io.github.serkutyildirim.kafka.service.MessageValidationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for demonstrating Kafka producer operations.
 *
 * <p>Each endpoint maps to a specific producer pattern implemented in {@link KafkaService}:
 * <ul>
 *   <li>{@code POST /send-simple}      — fire-and-forget via {@link KafkaService#sendSimple}</li>
 *   <li>{@code POST /send-reliable}    — synchronous blocking via {@link KafkaService#sendReliable}</li>
 *   <li>{@code POST /send-async}       — async callback via {@link KafkaService#sendAsync}</li>
 *   <li>{@code POST /send-batch}       — transactional batch via {@link KafkaService#sendBatch}</li>
 *   <li>{@code POST /send-with-key}    — key-based partitioned via {@link KafkaService#sendWithKey}</li>
 *   <li>{@code GET  /status/{id}}      — mock status lookup via {@link KafkaService#getTransactionStatus}</li>
 *   <li>{@code POST /send-notification} — notification placeholder (async, not yet implemented)</li>
 * </ul>
 * </p>
 *
 * @author Serkut Yıldırım
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger logger = LoggerFactory.getLogger(DemoController.class);

    private final KafkaService kafkaService;
    private final MessageValidationService validationService;

    public DemoController(KafkaService kafkaService, MessageValidationService validationService) {
        this.kafkaService = kafkaService;
        this.validationService = validationService;
    }

    // -------------------------------------------------------------------------
    // Health
    // -------------------------------------------------------------------------

    /** Health check endpoint. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "kafka-hands-on-guide");
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Simple (fire-and-forget)
    // -------------------------------------------------------------------------

    /**
     * Send a transaction using the fire-and-forget (simple) producer.
     * Validation and sending are delegated to {@link KafkaService#sendSimple}.
     */
    @PostMapping("/send-simple")
    public ResponseEntity<Map<String, Object>> sendSimple(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via simple producer");
        Map<String, Object> response = new HashMap<>();
        try {
            kafkaService.sendSimple(transaction);
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "SimpleProducer");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for simple send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending message via simple producer: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Reliable (synchronous)
    // -------------------------------------------------------------------------

    /**
     * Send a transaction using the synchronous reliable producer and return broker metadata.
     * Blocks until Kafka acknowledges the write.
     */
    @PostMapping("/send-reliable")
    public ResponseEntity<Map<String, Object>> sendReliable(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via reliable producer");
        Map<String, Object> response = new HashMap<>();
        try {
            var result = kafkaService.sendReliable(transaction);
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "ReliableProducer");
            response.put("partition", result.getRecordMetadata().partition());
            response.put("offset", result.getRecordMetadata().offset());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for reliable send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending message via reliable producer: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Async
    // -------------------------------------------------------------------------

    /**
     * Send a transaction using the async producer. Returns immediately; delivery is confirmed
     * asynchronously via a callback in the producer.
     */
    @PostMapping("/send-async")
    public ResponseEntity<Map<String, Object>> sendAsync(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via async producer");
        Map<String, Object> response = new HashMap<>();
        try {
            kafkaService.sendAsync(transaction);
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "AsyncProducer");
            response.put("note", "Message is being sent asynchronously");
            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for async send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending message via async producer: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Transactional batch
    // -------------------------------------------------------------------------

    /**
     * Send a list of transactions atomically using the transactional producer.
     * Either all messages are committed or none are visible to consumers.
     */
    @PostMapping("/send-batch")
    public ResponseEntity<Map<String, Object>> sendBatch(@Valid @RequestBody List<DemoTransaction> transactions) {
        logger.info("Received request to send batch of {} transactions", transactions.size());
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = kafkaService.sendBatch(transactions);
            response.put("success", success);
            response.put("count", transactions.size());
            response.put("producer", "TransactionalProducer");
            return success ? ResponseEntity.ok(response)
                           : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for batch send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending batch: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Key-based (partitioned)
    // -------------------------------------------------------------------------

    /**
     * Send a transaction to a Kafka partition determined by the given routing key.
     * All messages with the same key are guaranteed to land on the same partition (ordering).
     *
     * @param key         routing / partition key (e.g., account ID)
     * @param transaction the transaction payload
     */
    @PostMapping("/send-with-key/{key}")
    public ResponseEntity<Map<String, Object>> sendWithKey(
            @PathVariable String key,
            @Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction with key={}", key);
        Map<String, Object> response = new HashMap<>();
        try {
            kafkaService.sendWithKey(key, transaction);
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("key", key);
            response.put("producer", "PartitionedProducer");
            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for keyed send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending message with key={}: {}", key, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Transaction status (mock)
    // -------------------------------------------------------------------------

    /**
     * Returns the processing status of a transaction (mock implementation — always COMPLETED).
     *
     * @param transactionId the UUID of the transaction
     */
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String transactionId) {
        logger.info("Received status check request for transactionId={}", transactionId);
        Map<String, Object> response = new HashMap<>();
        try {
            String status = kafkaService.getTransactionStatus(transactionId);
            response.put("transactionId", transactionId);
            response.put("status", status);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving status for transactionId={}: {}", transactionId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // Notification (placeholder)
    // -------------------------------------------------------------------------

    /**
     * Send a notification message (async, placeholder — not yet routed through a dedicated producer).
     */
    @PostMapping("/send-notification")
    public ResponseEntity<Map<String, Object>> sendNotification(@Valid @RequestBody DemoNotification notification) {
        logger.info("Received request to send notification messageId={}", notification.getMessageId());
        Map<String, Object> response = new HashMap<>();
        try {
            // Validation is handled inside KafkaService; notification-specific validation
            // would be added to MessageValidationService in a follow-up task.
            // TODO: route notification through a dedicated NotificationProducer.
            logger.info("Notification async flow is not implemented yet for messageId={}", notification.getMessageId());
            response.put("success", true);
            response.put("messageId", notification.getMessageId());
            response.put("note", "Notification producer not yet implemented — logged only");
            return ResponseEntity.accepted().body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("Validation failed for notification send: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("Error sending notification: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
