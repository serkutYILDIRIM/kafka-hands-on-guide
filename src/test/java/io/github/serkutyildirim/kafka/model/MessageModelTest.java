package io.github.serkutyildirim.kafka.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MessageModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void transactionBuilderShouldPopulateMetadataAndType() {
        DemoTransaction transaction = DemoTransaction.builder()
            .sourceId("ACC-001")
            .targetId("ACC-002")
            .amount(new BigDecimal("100.50"))
            .currency("USD")
            .status(MessageStatus.CREATED)
            .description("Payment for order #12345")
            .build();

        assertNotNull(transaction.getMessageId());
        assertNotNull(transaction.getTimestamp());
        assertEquals("DEMO_TRANSACTION", transaction.getMessageType());
    }

    @Test
    void notificationBuilderShouldPopulateMetadataAndType() {
        DemoNotification notification = DemoNotification.builder()
            .recipientId("user-123")
            .content("Payment processed successfully")
            .notificationType(NotificationType.PUSH)
            .priority(Priority.HIGH)
            .build();

        assertNotNull(notification.getMessageId());
        assertNotNull(notification.getTimestamp());
        assertEquals("DEMO_NOTIFICATION", notification.getMessageType());
    }

    @Test
    void validationShouldRejectInvalidTransactionPayload() {
        DemoTransaction invalidTransaction = DemoTransaction.builder()
            .sourceId(" ")
            .targetId("")
            .amount(BigDecimal.ZERO)
            .currency("us")
            .status(null)
            .description("x".repeat(256))
            .build();

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<DemoTransaction>> violations = validator.validate(invalidTransaction);

            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sourceId")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("targetId")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("amount")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("currency")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status")));
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
        }
    }

    @Test
    void polymorphicSerializationShouldRoundTripNotification() throws Exception {
        BaseMessage message = DemoNotification.builder()
            .recipientId("user-456")
            .content("A new device signed into your account")
            .notificationType(NotificationType.EMAIL)
            .priority(Priority.MEDIUM)
            .build();

        String json = objectMapper.writeValueAsString(message);
        BaseMessage deserialized = objectMapper.readValue(json, BaseMessage.class);

        assertTrue(json.contains("\"type\":\"DEMO_NOTIFICATION\""));
        assertInstanceOf(DemoNotification.class, deserialized);
        assertEquals("DEMO_NOTIFICATION", deserialized.getMessageType());
    }

    @Test
    void subclassTypeShouldWinOverSpoofedMessageTypeField() throws Exception {
        String spoofedJson = """
            {
              "type": "DEMO_TRANSACTION",
              "messageType": "SPOOFED",
              "sourceId": "ACC-001",
              "targetId": "ACC-002",
              "amount": 42.15,
              "currency": "USD",
              "status": "CREATED"
            }
            """;

        BaseMessage deserialized = objectMapper.readValue(spoofedJson, BaseMessage.class);

        assertInstanceOf(DemoTransaction.class, deserialized);
        assertEquals("DEMO_TRANSACTION", deserialized.getMessageType());
    }
}
