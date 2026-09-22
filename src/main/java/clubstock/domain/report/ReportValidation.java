package clubstock.domain.report;

/**
 * Provides validation helpers shared by report-domain value objects and entities.
 */
final class ReportValidation {

    private ReportValidation() {
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
     * Returns stripped text when it is nonblank.
     *
     * @param value Text to validate.
     * @param fieldName Name of the validated field.
     * @return Stripped nonblank text.
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
     * Returns a validated relative storage key.
     *
     * @param storageKey Opaque application-managed storage key.
     * @return Stripped relative storage key.
     * @throws IllegalArgumentException If the key is blank, absolute, or traverses a parent.
     */
    static String requireRelativeStorageKey(String storageKey) throws IllegalArgumentException {
        String normalizedKey = requireTrimmedNonBlank(storageKey, "Damage image storage key");
        if (normalizedKey.startsWith("/") || normalizedKey.startsWith("\\")
                || isWindowsDriveQualifiedPath(normalizedKey)
                || containsParentSegment(normalizedKey)) {
            throw new IllegalArgumentException(
                    "Damage image storage key must be a relative non-traversing path.");
        }

        return normalizedKey;
    }

    /**
     * Returns whether the key uses a Windows drive-qualified path.
     *
     * @param storageKey Storage key to inspect.
     * @return True when the key starts with a drive letter and colon.
     */
    private static boolean isWindowsDriveQualifiedPath(String storageKey) {
        return storageKey.length() >= 2
                && Character.isLetter(storageKey.charAt(0))
                && storageKey.charAt(1) == ':';
    }

    /**
     * Returns whether any path segment attempts to traverse to a parent directory.
     *
     * @param storageKey Storage key to inspect.
     * @return True when a path segment equals `..`.
     */
    private static boolean containsParentSegment(String storageKey) {
        String[] segments = storageKey.split("[/\\\\]");
        for (String segment : segments) {
            if (segment.equals("..")) {
                return true;
            }
        }

        return false;
    }
}
