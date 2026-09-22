package clubstock.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DamageImageReferenceTest {
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    @Test
    void constructor_validMetadata_storesNormalizedReference() {
        DamageImageReference reference = new DamageImageReference("  reports/image.png  ",
                DamageImageFormat.PNG, MAX_SIZE_BYTES);

        assertEquals("reports/image.png", reference.storageKey());
        assertEquals(DamageImageFormat.PNG, reference.format());
        assertEquals(MAX_SIZE_BYTES, reference.sizeBytes());
    }

    @Test
    void constructor_boundarySizes_acceptsOneByteAndFiveMiB() {
        assertEquals(1, new DamageImageReference("one.jpg", DamageImageFormat.JPEG, 1)
                .sizeBytes());
        assertEquals(MAX_SIZE_BYTES,
                new DamageImageReference("maximum.jpg", DamageImageFormat.JPEG, MAX_SIZE_BYTES)
                        .sizeBytes());
    }

    @Test
    void constructor_invalidKey_rejectsBlankAbsoluteAndTraversalValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference(null, DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("  ", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("/reports/image.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("\\reports\\image.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("C:/reports/image.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("C:..\\outside.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("C:../outside.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("c:relative.png", DamageImageFormat.PNG, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("reports/../image.png", DamageImageFormat.PNG, 1));
    }

    @Test
    void constructor_invalidFormatOrSize_rejectsMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("image.png", null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("image.png", DamageImageFormat.PNG, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("image.png", DamageImageFormat.PNG, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new DamageImageReference("image.png", DamageImageFormat.PNG,
                        MAX_SIZE_BYTES + 1));
    }
}
