package clubstock.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MemberIdTest {

    @Test
    void constructor_paddedValue_trimsSurroundingWhitespace() {
        MemberId memberId = new MemberId("  Keith01  ");

        assertEquals("Keith01", memberId.value());
    }

    @Test
    void constructor_unicodePaddedValue_stripsSurroundingWhitespace() {
        MemberId memberId = new MemberId("\u2003Keith01\u2003");

        assertEquals("Keith01", memberId.value());
    }

    @Test
    void constructor_nullValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new MemberId(null));
    }

    @Test
    void constructor_blankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new MemberId(" \t "));
    }

    @Test
    void equality_caseVariantValues_remainsCaseSensitive() {
        MemberId upperCaseId = new MemberId("Keith01");
        MemberId lowerCaseId = new MemberId("keith01");

        assertNotEquals(upperCaseId, lowerCaseId);
    }
}
