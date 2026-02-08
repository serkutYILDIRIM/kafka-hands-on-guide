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
import java.util.Map;

/**
 * REST controller for demonstrating Kafka operations.
 * 
 * Provides HTTP endpoints to:
 * - Send messages using different producer patterns
 * - Trigger various Kafka scenarios
 * - Test different message types
 * - Monitor Kafka operations
 * 
 * TODO: Implement all REST endpoints
 * TODO: Add proper error responses
 * TODO: Add API documentation
 * TODO: Add request validation
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

    /**
     * Health check endpoint
     */

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "kafka-hands-on-guide");
        return ResponseEntity.ok(response);
    }

    /**
     * Send a transaction using simple producer
     * Example class
     */
    @PostMapping("/send-simple")
    public ResponseEntity<Map<String, Object>> sendSimple(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via simple producer");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!validationService.validateTransaction(transaction)) {
                response.put("success", false);
                response.put("error", "Transaction validation failed");
                return ResponseEntity.badRequest().body(response);
            }
            
            kafkaService.sendTransactionSimple(transaction);
            
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "SimpleProducer");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send a transaction using reliable producer
     */
    @PostMapping("/send-reliable")
    public ResponseEntity<Map<String, Object>> sendReliable(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via reliable producer");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!validationService.validateTransaction(transaction)) {
                response.put("success", false);
                response.put("error", "Transaction validation failed");
                return ResponseEntity.badRequest().body(response);
            }
            
            boolean sent = kafkaService.sendTransactionReliable(transaction);
            
            response.put("success", sent);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "ReliableProducer");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send a transaction using async producer
     */
    @PostMapping("/send-async")
    public ResponseEntity<Map<String, Object>> sendAsync(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to send transaction via async producer");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!validationService.validateTransaction(transaction)) {
                response.put("success", false);
                response.put("error", "Transaction validation failed");
                return ResponseEntity.badRequest().body(response);
            }
            
            kafkaService.sendTransactionAsync(transaction);
            
            response.put("success", true);
            response.put("messageId", transaction.getMessageId());
            response.put("producer", "AsyncProducer");
            response.put("note", "Message is being sent asynchronously");
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Send a notification message
     */
    @PostMapping("/send-notification")
    public ResponseEntity<Map<String, Object>> sendNotification(@Valid @RequestBody DemoNotification notification) {
        logger.info("Received request to send notification");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (!validationService.validateNotification(notification)) {
                response.put("success", false);
                response.put("error", "Notification validation failed");
                return ResponseEntity.badRequest().body(response);
            }
            
            kafkaService.sendNotificationAsync(notification);
            
            response.put("success", true);
            response.put("messageId", notification.getMessageId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error sending notification: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Demonstrate all producer patterns
     */
    @PostMapping("/demonstrate-all")
    public ResponseEntity<Map<String, Object>> demonstrateAll(@Valid @RequestBody DemoTransaction transaction) {
        logger.info("Received request to demonstrate all producer patterns");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            kafkaService.demonstrateAllPatterns(transaction);
            
            response.put("success", true);
            response.put("message", "Demonstrated all producer patterns");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error demonstrating patterns: {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // TODO: Add endpoint for transactional send
    // TODO: Add endpoint for partitioned send
    // TODO: Add endpoint for batch send
    // TODO: Add endpoint to get Kafka metrics

}
