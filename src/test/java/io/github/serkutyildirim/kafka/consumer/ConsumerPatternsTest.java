package io.github.serkutyildirim.kafka.consumer;

import io.github.serkutyildirim.kafka.config.KafkaTopicConfig;
import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.model.MessageStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConsumerPatternsTest {

    @Mock
    private KafkaTemplate<String, DemoTransaction> kafkaTemplate;

    @Mock
    private Acknowledgment acknowledgment;

    private SimpleConsumer simpleConsumer;
    private BatchConsumer batchConsumer;
    private GroupedConsumer groupedConsumer;
    private ManualAckConsumer manualAckConsumer;
    private ErrorHandlingConsumer errorHandlingConsumer;

    @BeforeEach
    void setUp() {
        simpleConsumer = new SimpleConsumer();
        batchConsumer = new BatchConsumer();
        groupedConsumer = new GroupedConsumer();
        manualAckConsumer = new ManualAckConsumer(kafkaTemplate);
        errorHandlingConsumer = new ErrorHandlingConsumer();
        ReflectionTestUtils.setField(errorHandlingConsumer, "kafkaTemplate", kafkaTemplate);
    }

    @Test
    void simpleConsumerShouldProcessValidRecord() {
        assertDoesNotThrow(() -> simpleConsumer.consume(record(validTransaction("OK"), 0L)));
    }

    @Test
    void groupedConsumerShouldProcessValidRecord() {
        assertDoesNotThrow(() -> groupedConsumer.consume(record(validTransaction("OK"), 1L)));
    }

    @Test
    void batchConsumerShouldProcessBatchSuccessfully() {
        List<ConsumerRecord<String, DemoTransaction>> records = List.of(
            record(validTransaction("BATCH-1"), 0L),
            record(validTransaction("BATCH-2"), 1L)
        );

        assertDoesNotThrow(() -> batchConsumer.consumeBatch(records));
    }

    @Test
    void batchConsumerShouldFailWholeBatchWhenOneRecordFails() {
        List<ConsumerRecord<String, DemoTransaction>> records = List.of(
            record(validTransaction("BATCH-1"), 0L),
            record(validTransaction("FAIL_BATCH"), 1L)
        );

        assertThrows(IllegalStateException.class, () -> batchConsumer.consumeBatch(records));
    }

    @Test
    void manualAckConsumerShouldAcknowledgeAfterSuccessfulProcessing() {
        manualAckConsumer.consume(record(validTransaction("OK"), 5L), acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(kafkaTemplate, never()).send(eq(KafkaTopicConfig.DEMO_DLQ_TOPIC), any(), any());
    }

    @Test
    void manualAckConsumerShouldRetryTwiceThenSendToDlq() {
        ConsumerRecord<String, DemoTransaction> failingRecord = record(validTransaction("FAIL_MANUAL"), 9L);

        manualAckConsumer.consume(failingRecord, acknowledgment);
        manualAckConsumer.consume(failingRecord, acknowledgment);
        verify(acknowledgment, never()).acknowledge();

        manualAckConsumer.consume(failingRecord, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        verify(kafkaTemplate, times(1)).send(KafkaTopicConfig.DEMO_DLQ_TOPIC, failingRecord.key(), failingRecord.value());
    }

    @Test
    void errorHandlingConsumerShouldNotAcknowledgeRetryableFailureBeforeMaxRetries() {
        ConsumerRecord<String, DemoTransaction> retryableRecord = record(validTransaction("RETRY"), 11L);

        errorHandlingConsumer.consume(retryableRecord, acknowledgment);
        errorHandlingConsumer.consume(retryableRecord, acknowledgment);

        verify(acknowledgment, never()).acknowledge();
        verify(kafkaTemplate, never()).send(eq(KafkaTopicConfig.DEMO_DLQ_TOPIC), any(), any());
    }

    @Test
    void errorHandlingConsumerShouldSendRetryableFailureToDlqAfterThirdAttempt() {
        ConsumerRecord<String, DemoTransaction> retryableRecord = record(validTransaction("RETRY"), 12L);

        errorHandlingConsumer.consume(retryableRecord, acknowledgment);
        errorHandlingConsumer.consume(retryableRecord, acknowledgment);
        errorHandlingConsumer.consume(retryableRecord, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        verify(kafkaTemplate, times(1)).send(KafkaTopicConfig.DEMO_DLQ_TOPIC, retryableRecord.key(), retryableRecord.value());
    }

    @Test
    void errorHandlingConsumerShouldDlqPermanentFailureImmediately() {
        ConsumerRecord<String, DemoTransaction> invalidRecord = record(validTransaction("INVALID"), 15L);

        errorHandlingConsumer.consume(invalidRecord, acknowledgment);

        verify(acknowledgment, times(1)).acknowledge();
        verify(kafkaTemplate, times(1)).send(KafkaTopicConfig.DEMO_DLQ_TOPIC, invalidRecord.key(), invalidRecord.value());
    }

    private ConsumerRecord<String, DemoTransaction> record(DemoTransaction transaction, long offset) {
        return new ConsumerRecord<>(KafkaTopicConfig.DEMO_MESSAGES_TOPIC, 0, offset, transaction.getSourceId(), transaction);
    }

    private DemoTransaction validTransaction(String description) {
        return DemoTransaction.builder()
            .sourceId("ACC-001")
            .targetId("ACC-002")
            .amount(new BigDecimal("100.50"))
            .currency("USD")
            .status(MessageStatus.CREATED)
            .description(description)
            .build();
    }
}

