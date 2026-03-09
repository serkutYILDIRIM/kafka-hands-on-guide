package io.github.serkutyildirim.kafka.service;

import io.github.serkutyildirim.kafka.model.DemoNotification;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.producer.AsyncProducer;
import io.github.serkutyildirim.kafka.producer.PartitionedProducer;
import io.github.serkutyildirim.kafka.producer.ReliableProducer;
import io.github.serkutyildirim.kafka.producer.SimpleProducer;
import io.github.serkutyildirim.kafka.producer.TransactionalProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Kafka service class that orchestrates producer operations.
 *
 * Provides a high-level API for sending messages using different producer patterns.
 * Acts as a facade for the various producer implementations.
 */
@Service
public class KafkaService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaService.class);

    private final SimpleProducer simpleProducer;
    private final ReliableProducer reliableProducer;
    private final AsyncProducer asyncProducer;
    private final TransactionalProducer transactionalProducer;
    private final PartitionedProducer partitionedProducer;

    public KafkaService(SimpleProducer simpleProducer,
                        ReliableProducer reliableProducer,
                        AsyncProducer asyncProducer,
                        TransactionalProducer transactionalProducer,
                        PartitionedProducer partitionedProducer) {
        this.simpleProducer = simpleProducer;
        this.reliableProducer = reliableProducer;
        this.asyncProducer = asyncProducer;
        this.transactionalProducer = transactionalProducer;
        this.partitionedProducer = partitionedProducer;
    }

    /**
     * Send a transaction message using simple producer
     *
     * @param transaction The transaction to send
     */
    public void sendTransactionSimple(DemoTransaction transaction) {
        logger.info("Sending transaction via simple producer");
        simpleProducer.send(transaction);
    }

    /**
     * Send a transaction message using reliable producer
     *
     * @param transaction The transaction to send
     * @return true if sent successfully, false otherwise
     */
    public boolean sendTransactionReliable(DemoTransaction transaction) {
        logger.info("Sending transaction via reliable producer");
        try {
            reliableProducer.sendWithConfirmation(transaction);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.error("Reliable send interrupted for messageId={}", transaction.getMessageId(), ex);
            return false;
        } catch (ExecutionException ex) {
            logger.error("Reliable send failed for messageId={}", transaction.getMessageId(), ex);
            return false;
        }
    }

    /**
     * Send a transaction message using async producer
     *
     * @param transaction The transaction to send
     */
    public void sendTransactionAsync(DemoTransaction transaction) {
        logger.info("Sending transaction via async producer");
        asyncProducer.sendAsync(transaction);
    }

    /**
     * Send a notification message using async producer
     *
     * @param notification The notification to send
     */
    public void sendNotificationAsync(DemoNotification notification) {
        logger.info("Notification async flow is not implemented yet for messageId={}", notification.getMessageId());
    }

    /**
     * Send multiple messages in a transaction
     *
     * @param transactions Array of transactions to send
     * @return true if all messages sent successfully, false otherwise
     */
    public boolean sendMultipleInTransaction(DemoTransaction... transactions) {
        int count = transactions == null ? 0 : transactions.length;
        logger.info("Sending {} transactions in a single transaction", count);
        if (count == 0) {
            return false;
        }
        return transactionalProducer.sendTransactional(Arrays.asList(transactions));
    }

    /**
     * Send a message to a specific partition
     *
     * @param transaction The transaction to send
     * @param partition The target partition
     */
    public void sendToPartition(DemoTransaction transaction, int partition) {
        logger.info("Routing transaction using source account {} as Kafka key; requested partition {} is informational in key-based mode",
                transaction.getSourceId(), partition);
        // We use the source account ID as the key because it keeps one account's history on the same partition.
        // Kafka guarantees ordering within that partition, which is what account-centric processing usually needs.
        partitionedProducer.sendWithKey(transaction.getSourceId(), transaction);
    }

    /**
     * Demonstrate all producer patterns with a sample transaction
     *
     * @param transaction The sample transaction
     */
    public void demonstrateAllPatterns(DemoTransaction transaction) {
        logger.info("Demonstrating all producer patterns for messageId={}", transaction.getMessageId());

        simpleProducer.send(transaction);
        sendTransactionReliable(transaction);
        asyncProducer.sendAsync(transaction);
        partitionedProducer.sendWithKey(transaction.getSourceId(), transaction);
        transactionalProducer.sendTransactional(List.of(transaction));
    }
}
