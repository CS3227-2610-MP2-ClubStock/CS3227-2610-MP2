package clubstock.domain.report;

/**
 * Represents validated metadata for a managed damage-evidence image.
 *
 * @param storageKey Relative opaque application-managed storage key.
 * @param format Supported image format.
 * @param sizeBytes Image size in bytes.
 */
public record DamageImageReference(String storageKey, DamageImageFormat format, long sizeBytes) {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    /**
     * Creates a damage image reference after validating its metadata.
     *
     * @param storageKey Relative opaque application-managed storage key.
     * @param format Supported image format.
     * @param sizeBytes Image size in bytes.
     * @throws IllegalArgumentException If metadata is invalid or the size is outside the limit.
     */
    public DamageImageReference {
        storageKey = ReportValidation.requireRelativeStorageKey(storageKey);
        ReportValidation.requireNonNull(format, "Damage image format");
        if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Damage image size must be between 1 byte and 5 MiB.");
        }
    }
}
