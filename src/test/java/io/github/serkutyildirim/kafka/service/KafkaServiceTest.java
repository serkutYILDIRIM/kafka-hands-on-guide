package io.github.serkutyildirim.kafka.service;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.model.MessageStatus;
import io.github.serkutyildirim.kafka.producer.AsyncProducer;
import io.github.serkutyildirim.kafka.producer.PartitionedProducer;
import io.github.serkutyildirim.kafka.producer.ReliableProducer;
import io.github.serkutyildirim.kafka.producer.SimpleProducer;
import io.github.serkutyildirim.kafka.producer.TransactionalProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaService}.
 *
 * <p>Follows the same Mockito + {@code ReflectionTestUtils} style used in
 * {@code ProducerPatternsTest} and {@code ConsumerPatternsTest}: no Spring context is loaded,
 * all collaborators are mocked, and each test verifies a single behaviour.</p>
 *
 * <p><b>What is tested:</b></p>
 * <ul>
 *   <li>{@link KafkaService#sendSimple} — delegates to {@link SimpleProducer} after validation</li>
 *   <li>{@link KafkaService#sendSimple} — rejects invalid transaction before calling producer</li>
 *   <li>{@link KafkaService#sendReliable} — returns broker {@link SendResult} on success</li>
 *   <li>{@link KafkaService#sendAsync} — returns {@link CompletableFuture} from {@link AsyncProducer}</li>
 *   <li>{@link KafkaService#sendBatch} — returns {@code true} on successful transactional commit</li>
 *   <li>{@link KafkaService#sendBatch} — returns {@code false} for empty list</li>
 *   <li>{@link KafkaService#sendBatch} — rejects invalid transaction inside batch</li>
 *   <li>{@link KafkaService#sendWithKey} — delegates to {@link PartitionedProducer} with given key</li>
 *   <li>{@link KafkaService#sendWithKey} — rejects blank key</li>
 *   <li>{@link KafkaService#createTransaction} — returns a validated {@link DemoTransaction}</li>
 *   <li>{@link KafkaService#createTransaction} — rejects invalid arguments</li>
 *   <li>{@link KafkaService#getTransactionStatus} — returns mock status string</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KafkaServiceTest {

    @Mock
    private SimpleProducer simpleProducer;
    @Mock
    private ReliableProducer reliableProducer;
    @Mock
    private AsyncProducer asyncProducer;
    @Mock
    private TransactionalProducer transactionalProducer;
    @Mock
    private PartitionedProducer partitionedProducer;

    private KafkaService kafkaService;

    @BeforeEach
    void setUp() {
        // Real validation service — no mocking needed, it has no external dependencies.
        MessageValidationService validationService = new MessageValidationService();

        kafkaService = new KafkaService();
        ReflectionTestUtils.setField(kafkaService, "simpleProducer", simpleProducer);
        ReflectionTestUtils.setField(kafkaService, "reliableProducer", reliableProducer);
        ReflectionTestUtils.setField(kafkaService, "asyncProducer", asyncProducer);
        ReflectionTestUtils.setField(kafkaService, "transactionalProducer", transactionalProducer);
        ReflectionTestUtils.setField(kafkaService, "partitionedProducer", partitionedProducer);
        ReflectionTestUtils.setField(kafkaService, "validationService", validationService);
    }

    // -------------------------------------------------------------------------
    // sendSimple
    // -------------------------------------------------------------------------

    @Test
    void sendSimpleShouldDelegateToSimpleProducer() {
        DemoTransaction transaction = validTransaction();

        assertDoesNotThrow(() -> kafkaService.sendSimple(transaction));

        verify(simpleProducer).send(transaction);
    }

    @Test
    void sendSimpleShouldRejectInvalidTransactionBeforeCallingProducer() {
        DemoTransaction invalid = invalidTransaction();

        assertThrows(IllegalArgumentException.class, () -> kafkaService.sendSimple(invalid));

        verify(simpleProducer, never()).send(any());
    }

    // -------------------------------------------------------------------------
    // sendReliable
    // -------------------------------------------------------------------------

    @Test
    void sendReliableShouldReturnBrokerSendResult() throws Exception {
        @SuppressWarnings("unchecked")
        SendResult<String, DemoTransaction> sendResult = org.mockito.Mockito.mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("demo-messages", 0), 0, 10, 0L, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(reliableProducer.sendWithConfirmation(any())).thenReturn(sendResult);

        SendResult<String, DemoTransaction> actual = kafkaService.sendReliable(validTransaction());

        assertSame(sendResult, actual);
        assertEquals(0, actual.getRecordMetadata().partition());
        assertEquals(10, actual.getRecordMetadata().offset());
    }

    @Test
    void sendReliableShouldRejectInvalidTransactionBeforeCallingProducer() throws Exception {
        try {
            kafkaService.sendReliable(invalidTransaction());
        } catch (IllegalArgumentException | ExecutionException | InterruptedException expected) {
            // expected — invalid transaction must not reach the producer
        }
        verify(reliableProducer, never()).sendWithConfirmation(any());
    }

    // -------------------------------------------------------------------------
    // sendAsync
    // -------------------------------------------------------------------------

    @Test
    void sendAsyncShouldReturnCompletableFutureFromAsyncProducer() throws ExecutionException, InterruptedException {
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, DemoTransaction>> future =
                CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
        when(asyncProducer.sendAsync(any())).thenReturn(future);

        CompletableFuture<SendResult<String, DemoTransaction>> result = kafkaService.sendAsync(validTransaction());

        assertFalse(result.isCompletedExceptionally());
        assertNotNull(result.get());
    }

    @Test
    void sendAsyncShouldRejectInvalidTransactionBeforeCallingProducer() {
        assertThrows(IllegalArgumentException.class,
                () -> kafkaService.sendAsync(invalidTransaction()));
        verify(asyncProducer, never()).sendAsync(any());
    }

    // -------------------------------------------------------------------------
    // sendBatch
    // -------------------------------------------------------------------------

    @Test
    void sendBatchShouldReturnTrueWhenTransactionalProducerCommits() {
        when(transactionalProducer.sendTransactional(any())).thenReturn(true);

        boolean result = kafkaService.sendBatch(List.of(validTransaction(), validTransaction()));

        assertTrue(result);
        verify(transactionalProducer).sendTransactional(any());
    }

    @Test
    void sendBatchShouldReturnFalseForEmptyList() {
        boolean result = kafkaService.sendBatch(List.of());

        assertFalse(result);
        verify(transactionalProducer, never()).sendTransactional(any());
    }

    @Test
    void sendBatchShouldReturnFalseForNullList() {
        boolean result = kafkaService.sendBatch(null);

        assertFalse(result);
        verify(transactionalProducer, never()).sendTransactional(any());
    }

    @Test
    void sendBatchShouldRejectBatchContainingInvalidTransaction() {
        List<DemoTransaction> mixedBatch = List.of(validTransaction(), invalidTransaction());

        assertThrows(IllegalArgumentException.class, () -> kafkaService.sendBatch(mixedBatch));
        verify(transactionalProducer, never()).sendTransactional(any());
    }

    // -------------------------------------------------------------------------
    // sendWithKey
    // -------------------------------------------------------------------------

    @Test
    void sendWithKeyShouldDelegateToPartitionedProducerWithGivenKey() {
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, DemoTransaction>> future =
                CompletableFuture.completedFuture(org.mockito.Mockito.mock(SendResult.class));
        when(partitionedProducer.sendWithKey(anyString(), any())).thenReturn(future);

        DemoTransaction transaction = validTransaction();
        kafkaService.sendWithKey("ACC-001", transaction);

        verify(partitionedProducer).sendWithKey(eq("ACC-001"), eq(transaction));
    }

    @Test
    void sendWithKeyShouldRejectBlankKey() {
        assertThrows(IllegalArgumentException.class,
                () -> kafkaService.sendWithKey("  ", validTransaction()));
        verify(partitionedProducer, never()).sendWithKey(anyString(), any());
    }

    @Test
    void sendWithKeyShouldRejectNullKey() {
        assertThrows(IllegalArgumentException.class,
                () -> kafkaService.sendWithKey(null, validTransaction()));
        verify(partitionedProducer, never()).sendWithKey(anyString(), any());
    }

    @Test
    void sendWithKeyShouldRejectInvalidTransactionBeforeCallingProducer() {
        assertThrows(IllegalArgumentException.class,
                () -> kafkaService.sendWithKey("ACC-001", invalidTransaction()));
        verify(partitionedProducer, never()).sendWithKey(anyString(), any());
    }

    // -------------------------------------------------------------------------
    // createTransaction
    // -------------------------------------------------------------------------

    @Test
    void createTransactionShouldReturnFullyInitializedTransaction() {
        DemoTransaction tx = kafkaService.createTransaction(
                "ACC-001", "ACC-002", new BigDecimal("250.00"), "EUR");

        assertNotNull(tx.getMessageId());
        assertNotNull(tx.getTimestamp());
        assertEquals("ACC-001", tx.getSourceId());
        assertEquals("ACC-002", tx.getTargetId());
        assertEquals(new BigDecimal("250.00"), tx.getAmount());
        assertEquals("EUR", tx.getCurrency());
        assertEquals(MessageStatus.CREATED, tx.getStatus());
    }

    @Test
    void createTransactionShouldRejectUnsupportedCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
                kafkaService.createTransaction("ACC-001", "ACC-002", new BigDecimal("10.00"), "XYZ"));
    }

    @Test
    void createTransactionShouldRejectNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                kafkaService.createTransaction("ACC-001", "ACC-002", new BigDecimal("-1.00"), "USD"));
    }

    // -------------------------------------------------------------------------
    // getTransactionStatus
    // -------------------------------------------------------------------------

    @Test
    void getTransactionStatusShouldReturnCompletedForAnyId() {
        String status = kafkaService.getTransactionStatus("any-uuid-here");

        assertEquals("COMPLETED", status);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private DemoTransaction validTransaction() {
        return DemoTransaction.builder()
                .sourceId("ACC-001")
                .targetId("ACC-002")
                .amount(new BigDecimal("100.50"))
                .currency("USD")
                .status(MessageStatus.CREATED)
                .description("KafkaService test")
                .build();
    }

    /**
     * Transaction with an unsupported currency — guaranteed to fail
     * {@link MessageValidationService#validateTransaction} so tests can verify
     * that invalid payloads are rejected before reaching any producer.
     */
    private DemoTransaction invalidTransaction() {
        return DemoTransaction.builder()
                .sourceId("ACC-001")
                .targetId("ACC-002")
                .amount(new BigDecimal("100.50"))
                .currency("XYZ")        // not in whitelist
                .status(MessageStatus.CREATED)
                .build();
    }
}




