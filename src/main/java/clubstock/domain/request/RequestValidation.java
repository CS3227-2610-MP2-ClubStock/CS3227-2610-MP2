package clubstock.domain.request;

/**
 * Provides validation helpers shared by request-domain value objects and entities.
 */
final class RequestValidation {

    private RequestValidation() {
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

    /**
     * Returns a normalized optional details value.
     *
     * @param details Optional request details.
     * @return Empty when details are null or blank, otherwise stripped details.
     */
    static String normalizeOptionalDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }

        return details.strip();
    }
}
