package clubstock.domain.loan;

/**
 * Represents an immutable identity for an individual loan.
 *
 * @param value Trimmed loan identifier.
 */
public record LoanId(String value) {

    /**
     * Creates a loan ID after validating and normalizing its value.
     *
     * @param value Loan identifier to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public LoanId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Loan ID cannot be null or blank.");
        }

        value = value.strip();
    }
}
