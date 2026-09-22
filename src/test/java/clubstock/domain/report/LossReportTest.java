package clubstock.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import clubstock.domain.loan.LoanId;

class LossReportTest {

    @Test
    void create_validDescription_trimsAndPreservesLoanIdentity() {
        LoanId loanId = new LoanId("loan-1");

        LossReport report = LossReport.create(loanId, "  Lost during transit  ");

        assertSame(loanId, report.loanId());
        assertEquals("Lost during transit", report.description());
    }

    @Test
    void create_missingLoanOrDescription_rejectsBeforeCreation() {
        LoanId loanId = new LoanId("loan-1");

        assertThrows(IllegalArgumentException.class,
                () -> LossReport.create(null, "description"));
        assertThrows(IllegalArgumentException.class,
                () -> LossReport.create(loanId, null));
        assertThrows(IllegalArgumentException.class,
                () -> LossReport.create(loanId, "  \t "));
    }

    @Test
    void equality_sameLoanDifferentDescription_comparesByLoanId() {
        LossReport first = LossReport.create(new LoanId("loan-1"), "First");
        LossReport second = LossReport.create(new LoanId("loan-1"), "Second");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentLoan_doesNotCompareEqual() {
        LossReport first = LossReport.create(new LoanId("loan-1"), "Description");
        LossReport second = LossReport.create(new LoanId("loan-2"), "Description");

        assertNotEquals(first, second);
    }
}
