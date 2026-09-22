package clubstock.domain.loan;

/**
 * Provides validation helpers shared by loan-domain value objects and entities.
 */
final class LoanValidation {

    private LoanValidation() {
    }

    /**
     * Returns the supplied value when it is non-null.
     *
     * @param value Value to validate.
     * @param fieldName Name of the validated field.
     * @param <T> Type of the value.
     * @return The non-null value.
     * @throws IllegalArgumentException If the value is null.
     */
    static <T> T requireNonNull(T value, String fieldName) throws IllegalArgumentException {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null.");
        }

        return value;
    }
}
