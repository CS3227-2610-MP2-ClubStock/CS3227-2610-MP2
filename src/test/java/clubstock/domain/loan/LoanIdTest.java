package clubstock.domain.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LoanIdTest {

    @Test
    void constructor_paddedValue_storesTrimmedValue() {
        LoanId loanId = new LoanId("  loan-1  ");

        assertEquals("loan-1", loanId.value());
    }

    @Test
    void constructor_blankOrNullValue_rejectsValue() {
        assertThrows(IllegalArgumentException.class, () -> new LoanId(null));
        assertThrows(IllegalArgumentException.class, () -> new LoanId("  \t "));
    }

    @Test
    void constructor_caseVariantValues_preservesCase() {
        assertEquals("Loan-1", new LoanId("Loan-1").value());
        assertEquals("loan-1", new LoanId("loan-1").value());
    }
}
