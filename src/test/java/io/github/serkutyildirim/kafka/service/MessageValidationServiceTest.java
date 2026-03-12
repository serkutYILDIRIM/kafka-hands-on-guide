package io.github.serkutyildirim.kafka.service;

import io.github.serkutyildirim.kafka.model.DemoTransaction;
import io.github.serkutyildirim.kafka.model.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MessageValidationService}.
 *
 * <p>Follows the same no-Spring, plain-JUnit style used in {@code ProducerPatternsTest}
 * and {@code ConsumerPatternsTest}: each test instantiates the class under test directly,
 * uses no mocks (the service has no external dependencies), and asserts a single behaviour.</p>
 *
 * <p><b>What is tested:</b></p>
 * <ul>
 *   <li>Null transaction guard</li>
 *   <li>Blank sourceId / targetId rejection</li>
 *   <li>Non-positive amount rejection</li>
 *   <li>Unsupported / malformed currency rejection</li>
 *   <li>Full happy-path acceptance</li>
 *   <li>Currency whitelist logic ({@code isValidCurrency})</li>
 *   <li>Mock stubs ({@code validateBalance}, {@code checkDuplicateTransaction}) run without throwing</li>
 * </ul>
 */
class MessageValidationServiceTest {

    private MessageValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new MessageValidationService();
    }

    // -------------------------------------------------------------------------
    // validateTransaction — null guard
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldRejectNullTransaction() {
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(null));
    }

    // -------------------------------------------------------------------------
    // validateTransaction — sourceId
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldRejectBlankSourceId() {
        DemoTransaction transaction = baseBuilder().sourceId("  ").build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    // -------------------------------------------------------------------------
    // validateTransaction — targetId
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldRejectBlankTargetId() {
        DemoTransaction transaction = baseBuilder().targetId("").build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    // -------------------------------------------------------------------------
    // validateTransaction — amount
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldRejectZeroAmount() {
        DemoTransaction transaction = baseBuilder().amount(BigDecimal.ZERO).build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    @Test
    void validateTransactionShouldRejectNegativeAmount() {
        DemoTransaction transaction = baseBuilder().amount(new BigDecimal("-0.01")).build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    // -------------------------------------------------------------------------
    // validateTransaction — currency
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldRejectUnsupportedCurrencyCode() {
        DemoTransaction transaction = baseBuilder().currency("XYZ").build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    @Test
    void validateTransactionShouldRejectMalformedCurrencyCode() {
        DemoTransaction transaction = baseBuilder().currency("US").build();
        assertThrows(IllegalArgumentException.class,
                () -> validationService.validateTransaction(transaction));
    }

    // -------------------------------------------------------------------------
    // validateTransaction — happy path
    // -------------------------------------------------------------------------

    @Test
    void validateTransactionShouldPassForFullyValidTransaction() {
        assertDoesNotThrow(() -> validationService.validateTransaction(validTransaction()));
    }

    // -------------------------------------------------------------------------
    // isValidCurrency
    // -------------------------------------------------------------------------

    @Test
    void isValidCurrencyShouldReturnTrueForSupportedCodes() {
        assertTrue(validationService.isValidCurrency("USD"));
        assertTrue(validationService.isValidCurrency("EUR"));
        assertTrue(validationService.isValidCurrency("GBP"));
        assertTrue(validationService.isValidCurrency("JPY"));
        assertTrue(validationService.isValidCurrency("CHF"));
    }

    @Test
    void isValidCurrencyShouldReturnFalseForUnknownCode() {
        assertFalse(validationService.isValidCurrency("XYZ"));
    }

    @Test
    void isValidCurrencyShouldReturnFalseForTooShortCode() {
        assertFalse(validationService.isValidCurrency("US"));
    }

    @Test
    void isValidCurrencyShouldReturnFalseForTooLongCode() {
        assertFalse(validationService.isValidCurrency("USDD"));
    }

    @Test
    void isValidCurrencyShouldReturnFalseForLowercaseCode() {
        assertFalse(validationService.isValidCurrency("usd"));
    }

    @Test
    void isValidCurrencyShouldReturnFalseForNullInput() {
        assertFalse(validationService.isValidCurrency(null));
    }

    // -------------------------------------------------------------------------
    // validateBalance — mock stub, must not throw
    // -------------------------------------------------------------------------

    @Test
    void validateBalanceShouldNotThrowInMockImplementation() {
        assertDoesNotThrow(() ->
                validationService.validateBalance("ACC-001", new BigDecimal("500.00")));
    }

    // -------------------------------------------------------------------------
    // checkDuplicateTransaction — mock stub, must not throw
    // -------------------------------------------------------------------------

    @Test
    void checkDuplicateTransactionShouldNotThrowInMockImplementation() {
        assertDoesNotThrow(() ->
                validationService.checkDuplicateTransaction("some-uuid-1234"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a builder pre-filled with valid values so individual tests only need to
     * override the field they want to make invalid.
     */
    private DemoTransaction.DemoTransactionBuilder<?, ?> baseBuilder() {
        return DemoTransaction.builder()
                .sourceId("ACC-001")
                .targetId("ACC-002")
                .amount(new BigDecimal("100.50"))
                .currency("USD")
                .status(MessageStatus.CREATED);
    }

    private DemoTransaction validTransaction() {
        return baseBuilder().description("Validation service test").build();
    }
}

