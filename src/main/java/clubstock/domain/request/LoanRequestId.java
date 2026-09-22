package clubstock.domain.request;

/**
 * Represents an immutable identity for a loan request.
 *
 * @param value Trimmed loan request identifier.
 */
public record LoanRequestId(String value) {

    /**
     * Creates a loan request ID after validating and normalizing its value.
     *
     * @param value Loan request identifier to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public LoanRequestId {
        value = RequestValidation.requireTrimmedNonBlank(value, "Loan request ID");
    }
}
