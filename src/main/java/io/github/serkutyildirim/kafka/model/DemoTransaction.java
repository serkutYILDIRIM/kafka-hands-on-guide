package io.github.serkutyildirim.kafka.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Demo transaction message representing a financial transaction.
 * 
 * This class is used to demonstrate:
 * - JSON serialization/deserialization
 * - Message validation
 * - Transactional producer patterns
 * - Partition key routing (using sourceId as key)
 * 
 * TODO: Add business logic for transaction processing
 * TODO: Implement transaction validation rules
 * 
 * @author Serkut Yıldırım
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DemoTransaction extends BaseMessage {

    private static final long serialVersionUID = 1L;

    /**
     * Source account ID
     */
    @NotBlank(message = "Source ID cannot be blank")
    private String sourceId;

    /**
     * Target account ID
     */
    @NotBlank(message = "Target ID cannot be blank")
    private String targetId;

    /**
     * Transaction amount
     */
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Currency code (e.g., USD, EUR, TRY)
     */
    @NotBlank(message = "Currency cannot be blank")
    private String currency;

    /**
     * Transaction type (e.g., TRANSFER, PAYMENT, WITHDRAWAL)
     */
    private String transactionType;

    /**
     * Additional description or notes
     */
    private String description;

    /**
     * Constructor for creating a new transaction
     */
    public DemoTransaction(String sourceId, String targetId, BigDecimal amount, String currency) {
        super("TRANSACTION_SERVICE");
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = "TRANSFER";
    }

}
