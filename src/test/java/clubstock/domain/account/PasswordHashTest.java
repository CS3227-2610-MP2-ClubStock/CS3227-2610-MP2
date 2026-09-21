package clubstock.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

    @Test
    void constructor_encodedValue_preservesOpaqueHash() {
        PasswordHash passwordHash = new PasswordHash("  encoded-value  ");

        assertEquals("  encoded-value  ", passwordHash.encodedHash());
    }

    @Test
    void constructor_nullValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash(null));
    }

    @Test
    void constructor_blankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHash(" \t "));
    }
}
