package io.github.serkutyildirim.kafka.producer;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.model.MessageStatus;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProducerPatternsTest {

    @Test
    void simpleProducerShouldDispatchFireAndForgetSend() {
        KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), any(DemoTransaction.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        SimpleProducer producer = new SimpleProducer();
        ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);

        DemoTransaction transaction = sampleTransaction();
        assertDoesNotThrow(() -> producer.send(transaction));

        verify(kafkaTemplate).send(eq("demo-messages"), eq(transaction));
    }

    @Test
    void reliableProducerShouldReturnBrokerConfirmation() throws Exception {
        KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        SendResult<String, DemoTransaction> sendResult = mock(SendResult.class);
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("demo-messages", 1), 0, 42, 0L, 0, 0);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        CompletableFuture<SendResult<String, DemoTransaction>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), any(DemoTransaction.class))).thenReturn(future);

        ReliableProducer producer = new ReliableProducer();
        ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);

        SendResult<String, DemoTransaction> actual = producer.sendWithConfirmation(sampleTransaction());

        assertSame(sendResult, actual);
    }

    @Test
    void asyncProducerShouldReturnFailedFutureWhenSerializationFailsImmediately() {
        KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), any(DemoTransaction.class)))
                .thenThrow(new SerializationException("boom"));

        AsyncProducer producer = new AsyncProducer();
        ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);

        CompletableFuture<SendResult<String, DemoTransaction>> future = producer.sendAsync(sampleTransaction());

        assertTrue(future.isCompletedExceptionally());
        assertThrows(ExecutionException.class, future::get);
    }

    @Test
    void partitionedProducerShouldSendUsingProvidedKey() {
        KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any(DemoTransaction.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        PartitionedProducer producer = new PartitionedProducer();
        ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);

        DemoTransaction transaction = sampleTransaction();
        producer.sendWithKey("ACC-001", transaction);

        verify(kafkaTemplate).send("demo-messages", "ACC-001", transaction);
    }

    @Test
    void transactionalProducerShouldCommitWhenAllMessagesAreQueued() {
        KafkaTemplate<String, DemoTransaction> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        KafkaOperations<String, DemoTransaction> operations = mock(KafkaOperations.class);

        when(operations.send(anyString(), anyString(), any(DemoTransaction.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(kafkaTemplate.executeInTransaction(any())).thenAnswer(invocation -> {
            KafkaOperations.OperationsCallback<String, DemoTransaction, Boolean> callback = invocation.getArgument(0);
            return callback.doInOperations(operations);
        });

        TransactionalProducer producer = new TransactionalProducer();
        ReflectionTestUtils.setField(producer, "transactionalKafkaTemplate", kafkaTemplate);

        boolean committed = producer.sendTransactional(List.of(sampleTransaction(), sampleTransaction()));

        assertTrue(committed);
        verify(kafkaTemplate).executeInTransaction(any());
        verify(operations, times(2)).send(eq("demo-messages"), anyString(), any(DemoTransaction.class));
    }

    @Test
    void transactionalProducerShouldRejectEmptyBatch() {
        TransactionalProducer producer = new TransactionalProducer();
        assertFalse(producer.sendTransactional(List.of()));
    }

    private DemoTransaction sampleTransaction() {
        return DemoTransaction.builder()
                .sourceId("ACC-001")
                .targetId("ACC-002")
                .amount(new BigDecimal("100.50"))
                .currency("USD")
                .status(MessageStatus.CREATED)
                .description("Producer pattern test")
                .build();
    }
}
