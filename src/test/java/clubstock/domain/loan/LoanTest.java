package clubstock.domain.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.request.LoanRequestId;

class LoanTest {
    private static final Instant STARTED_AT = Instant.parse("2026-02-10T08:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(STARTED_AT, ZoneOffset.UTC);
    private static final LoanRequestId REQUEST_ID = new LoanRequestId("request-1");
    private static final MemberId MEMBER_ID = new MemberId("member-1");
    private static final EquipmentId EQUIPMENT_ID = new EquipmentId("item-1");
    private static final LocalDate END_DATE = LocalDate.of(2026, 2, 15);

    @Test
    void start_validAssignment_startsImmediatelyOnLoan() {
        LoanId loanId = new LoanId("loan-1");

        Loan loan = Loan.start(loanId, REQUEST_ID, MEMBER_ID, EQUIPMENT_ID, END_DATE,
                FIXED_CLOCK);

        assertSame(loanId, loan.loanId());
        assertSame(REQUEST_ID, loan.loanRequestId());
        assertSame(MEMBER_ID, loan.memberId());
        assertSame(EQUIPMENT_ID, loan.equipmentId());
        assertEquals(STARTED_AT, loan.startedAt());
        assertEquals(END_DATE, loan.endDate());
        assertEquals(LoanStatus.ON_LOAN, loan.status());
        assertTrue(loan.reportedReturnCondition().isEmpty());
    }

    @Test
    void restore_returnPendingLoan_preservesOriginalTimestampAndCondition() {
        Loan loan = Loan.restore(new LoanId("loan-1"), REQUEST_ID, MEMBER_ID, EQUIPMENT_ID,
                STARTED_AT, END_DATE, LoanStatus.RETURN_PENDING,
                ReportedReturnCondition.DAMAGED);

        assertEquals(STARTED_AT, loan.startedAt());
        assertEquals(LoanStatus.RETURN_PENDING, loan.status());
        assertEquals(ReportedReturnCondition.DAMAGED,
                loan.reportedReturnCondition().orElseThrow());
    }

    @Test
    void restore_onLoanWithCondition_rejectsInconsistentState() {
        assertThrows(IllegalArgumentException.class, () -> Loan.restore(new LoanId("loan-1"),
                REQUEST_ID, MEMBER_ID, EQUIPMENT_ID, STARTED_AT, END_DATE, LoanStatus.ON_LOAN,
                ReportedReturnCondition.GOOD));
    }

    @Test
    void start_nullValue_rejectsBeforeCreation() {
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(null, REQUEST_ID, MEMBER_ID, EQUIPMENT_ID, END_DATE,
                        FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(new LoanId("loan-1"), null, MEMBER_ID, EQUIPMENT_ID, END_DATE,
                        FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(new LoanId("loan-1"), REQUEST_ID, null, EQUIPMENT_ID, END_DATE,
                        FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(new LoanId("loan-1"), REQUEST_ID, MEMBER_ID, null, END_DATE,
                        FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(new LoanId("loan-1"), REQUEST_ID, MEMBER_ID, EQUIPMENT_ID, null,
                        FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> Loan.start(new LoanId("loan-1"), REQUEST_ID, MEMBER_ID, EQUIPMENT_ID,
                        END_DATE, null));
    }

    @Test
    void submitReturn_goodAndDamagedConditions_recordSeparatePendingBranches() {
        Loan goodLoan = createLoan("loan-1");
        Loan damagedLoan = createLoan("loan-2");

        goodLoan.submitReturn(ReportedReturnCondition.GOOD);
        damagedLoan.submitReturn(ReportedReturnCondition.DAMAGED);

        assertEquals(LoanStatus.RETURN_PENDING, goodLoan.status());
        assertEquals(ReportedReturnCondition.GOOD,
                goodLoan.reportedReturnCondition().orElseThrow());
        assertEquals(LoanStatus.RETURN_PENDING, damagedLoan.status());
        assertEquals(ReportedReturnCondition.DAMAGED,
                damagedLoan.reportedReturnCondition().orElseThrow());
    }

    @Test
    void submitReturn_oneLoanDoesNotChangeAnotherLoan() {
        Loan returnedLoan = createLoan("loan-1");
        Loan activeLoan = createLoan("loan-2");

        returnedLoan.submitReturn(ReportedReturnCondition.GOOD);

        assertEquals(LoanStatus.RETURN_PENDING, returnedLoan.status());
        assertEquals(LoanStatus.ON_LOAN, activeLoan.status());
        assertTrue(activeLoan.reportedReturnCondition().isEmpty());
    }

    @Test
    void submitLost_onLoan_becomesLossPendingWithoutReturnCondition() {
        Loan loan = createLoan("loan-1");

        loan.submitLost();

        assertEquals(LoanStatus.LOST_PENDING, loan.status());
        assertTrue(loan.reportedReturnCondition().isEmpty());
    }

    @Test
    void completeReturn_returnPending_becomesCompleted() {
        Loan loan = createLoan("loan-1");
        loan.submitReturn(ReportedReturnCondition.GOOD);

        loan.completeReturn();

        assertEquals(LoanStatus.COMPLETED, loan.status());
        assertEquals(ReportedReturnCondition.GOOD,
                loan.reportedReturnCondition().orElseThrow());
    }

    @Test
    void completeLoss_lossPending_becomesCompleted() {
        Loan loan = createLoan("loan-1");
        loan.submitLost();

        loan.completeLoss();

        assertEquals(LoanStatus.COMPLETED, loan.status());
    }

    @Test
    void invalidOrRepeatedTransitions_preserveLoanState() {
        Loan loan = createLoan("loan-1");

        assertThrows(IllegalArgumentException.class,
                () -> loan.submitReturn(null));
        assertEquals(LoanStatus.ON_LOAN, loan.status());
        assertTrue(loan.reportedReturnCondition().isEmpty());

        loan.submitReturn(ReportedReturnCondition.DAMAGED);
        assertThrows(IllegalStateException.class,
                () -> loan.submitReturn(ReportedReturnCondition.GOOD));
        assertThrows(IllegalStateException.class, loan::submitLost);
        assertThrows(IllegalStateException.class, loan::completeLoss);
        assertEquals(LoanStatus.RETURN_PENDING, loan.status());
        assertEquals(ReportedReturnCondition.DAMAGED,
                loan.reportedReturnCondition().orElseThrow());

        loan.completeReturn();
        assertThrows(IllegalStateException.class, loan::completeReturn);
        assertThrows(IllegalStateException.class, loan::completeLoss);
        assertThrows(IllegalStateException.class,
                () -> loan.submitReturn(ReportedReturnCondition.GOOD));
        assertEquals(LoanStatus.COMPLETED, loan.status());
        assertEquals(ReportedReturnCondition.DAMAGED,
                loan.reportedReturnCondition().orElseThrow());
    }

    @Test
    void completionMethods_rejectWrongPendingBranchWithoutMutation() {
        Loan lossPendingLoan = createLoan("loan-1");
        lossPendingLoan.submitLost();
        assertThrows(IllegalStateException.class, lossPendingLoan::completeReturn);
        assertEquals(LoanStatus.LOST_PENDING, lossPendingLoan.status());

        Loan returnPendingLoan = createLoan("loan-2");
        returnPendingLoan.submitReturn(ReportedReturnCondition.GOOD);
        assertThrows(IllegalStateException.class, returnPendingLoan::completeLoss);
        assertEquals(LoanStatus.RETURN_PENDING, returnPendingLoan.status());
    }

    @Test
    void isOverdue_onlyOnLoanAfterEndDate_returnsExpectedResultWithoutMutation() {
        Loan loan = createLoan("loan-1");

        assertFalse(loan.isOverdue(END_DATE.minusDays(1)));
        assertFalse(loan.isOverdue(END_DATE));
        assertTrue(loan.isOverdue(END_DATE.plusDays(1)));
        assertEquals(LoanStatus.ON_LOAN, loan.status());

        loan.submitReturn(ReportedReturnCondition.GOOD);
        assertFalse(loan.isOverdue(END_DATE.plusDays(1)));
        assertEquals(LoanStatus.RETURN_PENDING, loan.status());

        Loan lossPendingLoan = createLoan("loan-2");
        lossPendingLoan.submitLost();
        assertFalse(lossPendingLoan.isOverdue(END_DATE.plusDays(1)));
        lossPendingLoan.completeLoss();
        assertFalse(lossPendingLoan.isOverdue(END_DATE.plusDays(1)));
    }

    @Test
    void isOverdue_nullDate_rejectsWithoutMutation() {
        Loan loan = createLoan("loan-1");

        assertThrows(IllegalArgumentException.class, () -> loan.isOverdue(null));

        assertEquals(LoanStatus.ON_LOAN, loan.status());
    }

    @Test
    void equality_sameIdentityDifferentAssignment_comparesByLoanId() {
        Loan first = createLoan("loan-1");
        Loan second = Loan.start(new LoanId("loan-1"), new LoanRequestId("request-2"),
                new MemberId("member-2"), new EquipmentId("item-2"), END_DATE, FIXED_CLOCK);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentIdentity_doesNotCompareEqual() {
        assertNotEquals(createLoan("loan-1"), createLoan("loan-2"));
    }

    private static Loan createLoan(String loanId) {
        return Loan.start(new LoanId(loanId), REQUEST_ID, MEMBER_ID, EQUIPMENT_ID, END_DATE,
                FIXED_CLOCK);
    }
}
