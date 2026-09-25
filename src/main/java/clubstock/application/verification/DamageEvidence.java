package clubstock.application.verification;

import java.util.Arrays;

import clubstock.domain.report.DamageImageFormat;

/**
 * Safe in-memory image evidence retrieved from managed storage.
 */
public final class DamageEvidence {
    private final byte[] bytes;
    private final DamageImageFormat format;

    /**
     * Creates a defensive image-evidence value.
     *
     * @param bytes Image bytes.
     * @param format Validated image format.
     */
    public DamageEvidence(byte[] bytes, DamageImageFormat format) {
        if (bytes == null || bytes.length == 0 || format == null) {
            throw new IllegalArgumentException("Damage evidence requires image bytes and a format.");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.format = format;
    }

    /**
     * Returns a defensive copy of the image bytes.
     *
     * @return Image bytes.
     */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Returns the recorded image format.
     *
     * @return Image format.
     */
    public DamageImageFormat format() {
        return format;
    }
}
