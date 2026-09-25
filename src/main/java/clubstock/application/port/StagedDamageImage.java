package clubstock.application.port;

import java.util.UUID;

import clubstock.domain.report.DamageImageFormat;

/**
 * Identifies validated image bytes held temporarily by a managed damage-image store.
 *
 * @param stagingToken Random token assigned by the image store.
 * @param format Detected image format.
 * @param sizeBytes Validated image size.
 */
public record StagedDamageImage(String stagingToken, DamageImageFormat format, long sizeBytes) {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    /**
     * Creates a staged-image handle with validated metadata.
     *
     * @param stagingToken Store-generated UUID token.
     * @param format Detected image format.
     * @param sizeBytes Validated image size.
     * @throws IllegalArgumentException If the token or image metadata is invalid.
     */
    public StagedDamageImage {
        if (stagingToken == null || format == null || sizeBytes < 1
                || sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Staged damage image metadata is invalid.");
        }
        try {
            if (!UUID.fromString(stagingToken).toString().equals(stagingToken)) {
                throw new IllegalArgumentException("Staging token must be a canonical UUID.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Staging token must be a canonical UUID.", exception);
        }
    }
}
