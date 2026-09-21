package clubstock.domain.account;

/**
 * Provides validation helpers shared by account-domain value objects and entities.
 */
final class AccountValidation {

    private AccountValidation() {
    }

    /**
     * Returns the value with surrounding Unicode whitespace removed when the supplied text is
     * nonblank.
     *
     * @param value Text to validate.
     * @param fieldName Name of the validated field.
     * @return Nonblank text without surrounding Unicode whitespace.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    static String requireTrimmedNonBlank(String value, String fieldName)
            throws IllegalArgumentException {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank.");
        }

        return value.strip();
    }

    /**
     * Returns the supplied value when it is nonblank.
     *
     * @param value Text to validate.
     * @param fieldName Name of the validated field.
     * @return The original nonblank text.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    static String requireNonBlank(String value, String fieldName)
            throws IllegalArgumentException {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank.");
        }

        return value;
    }
}
