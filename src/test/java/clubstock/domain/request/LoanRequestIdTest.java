package clubstock.domain.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LoanRequestIdTest {

    @Test
    void constructor_paddedValue_trimsSurroundingWhitespace() {
        LoanRequestId loanRequestId = new LoanRequestId("  request-1  ");

        assertEquals("request-1", loanRequestId.value());
    }

    @Test
    void constructor_nullOrBlankValue_rejectsInput() {
        assertThrows(IllegalArgumentException.class, () -> new LoanRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new LoanRequestId(" \t "));
    }

    @Test
    void equality_caseVariantValues_remainsCaseSensitive() {
        LoanRequestId upperCaseId = new LoanRequestId("Request-1");
        LoanRequestId lowerCaseId = new LoanRequestId("request-1");

        assertNotEquals(upperCaseId, lowerCaseId);
    }
}
