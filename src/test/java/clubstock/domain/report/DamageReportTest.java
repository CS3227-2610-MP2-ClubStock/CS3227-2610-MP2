package clubstock.domain.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import clubstock.domain.loan.LoanId;

class DamageReportTest {

    @Test
    void create_validEvidence_trimsDescriptionAndPreservesReferences() {
        LoanId loanId = new LoanId("loan-1");
        DamageImageReference imageReference = new DamageImageReference("damage/image.jpg",
                DamageImageFormat.JPEG, 42);

        DamageReport report = DamageReport.create(loanId, imageReference, "  Broken hinge  ");

        assertSame(loanId, report.loanId());
        assertSame(imageReference, report.imageReference());
        assertEquals("Broken hinge", report.description());
    }

    @Test
    void create_missingEvidenceOrDescription_rejectsBeforeCreation() {
        LoanId loanId = new LoanId("loan-1");
        DamageImageReference imageReference = new DamageImageReference("damage/image.jpg",
                DamageImageFormat.JPEG, 42);

        assertThrows(IllegalArgumentException.class,
                () -> DamageReport.create(null, imageReference, "description"));
        assertThrows(IllegalArgumentException.class,
                () -> DamageReport.create(loanId, null, "description"));
        assertThrows(IllegalArgumentException.class,
                () -> DamageReport.create(loanId, imageReference, null));
        assertThrows(IllegalArgumentException.class,
                () -> DamageReport.create(loanId, imageReference, "  \t "));
    }

    @Test
    void equality_sameLoanDifferentEvidence_comparesByLoanId() {
        LoanId loanId = new LoanId("loan-1");
        DamageReport first = DamageReport.create(loanId,
                new DamageImageReference("damage/one.jpg", DamageImageFormat.JPEG, 1), "One");
        DamageReport second = DamageReport.create(new LoanId("loan-1"),
                new DamageImageReference("damage/two.png", DamageImageFormat.PNG, 2), "Two");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentLoan_doesNotCompareEqual() {
        DamageImageReference imageReference = new DamageImageReference("damage/image.jpg",
                DamageImageFormat.JPEG, 42);

        DamageReport first = DamageReport.create(new LoanId("loan-1"), imageReference, "Damage");
        DamageReport second = DamageReport.create(new LoanId("loan-2"), imageReference, "Damage");

        assertNotEquals(first, second);
    }
}
