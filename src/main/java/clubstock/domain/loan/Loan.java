package clubstock.domain.loan;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.request.LoanRequestId;

/**
 * Represents one physical equipment item assigned to one Member.
 */
public final class Loan {
    private final LoanId loanId;
    private final LoanRequestId loanRequestId;
    private final MemberId memberId;
    private final EquipmentId equipmentId;
    private final Instant startedAt;
    private final LocalDate endDate;
    private LoanStatus status;
    private ReportedReturnCondition reportedReturnCondition;

    private Loan(LoanId loanId, LoanRequestId loanRequestId, MemberId memberId,
            EquipmentId equipmentId, LocalDate endDate, Clock clock) {
        this.loanId = loanId;
        this.loanRequestId = loanRequestId;
        this.memberId = memberId;
        this.equipmentId = equipmentId;
        this.endDate = endDate;
        startedAt = clock.instant();
        status = LoanStatus.ON_LOAN;
    }

    /**
     * Starts an individual loan immediately using the supplied clock.
     *
     * @param loanId Stable loan identity.
     * @param loanRequestId Source request identity.
     * @param memberId Assigned Member identity.
     * @param equipmentId Assigned physical item identity.
     * @param endDate Immutable requested loan end date.
     * @param clock Clock used to record the loan start time.
     * @return Newly started loan.
     * @throws IllegalArgumentException If an argument is null.
     */
    public static Loan start(LoanId loanId, LoanRequestId loanRequestId, MemberId memberId,
            EquipmentId equipmentId, LocalDate endDate, Clock clock)
            throws IllegalArgumentException {
        LoanValidation.requireNonNull(loanId, "Loan ID");
        LoanValidation.requireNonNull(loanRequestId, "Loan request ID");
        LoanValidation.requireNonNull(memberId, "Member ID");
        LoanValidation.requireNonNull(equipmentId, "Equipment ID");
        LoanValidation.requireNonNull(endDate, "End date");
        LoanValidation.requireNonNull(clock, "Clock");

        return new Loan(loanId, loanRequestId, memberId, equipmentId, endDate, clock);
    }

    /**
     * Restores a Loan from persisted lifecycle state without changing its start time.
     *
     * @param loanId Stable Loan identity.
     * @param loanRequestId Source request identity.
     * @param memberId Assigned Member identity.
     * @param equipmentId Assigned physical item identity.
     * @param startedAt Original Loan start instant.
     * @param endDate Immutable Loan end date.
     * @param status Persisted lifecycle status.
     * @param reportedReturnCondition Persisted advisory return condition, when applicable.
     * @return Restored Loan.
     * @throws IllegalArgumentException If persisted state is invalid.
     */
    public static Loan restore(LoanId loanId, LoanRequestId loanRequestId, MemberId memberId,
            EquipmentId equipmentId, Instant startedAt, LocalDate endDate, LoanStatus status,
            ReportedReturnCondition reportedReturnCondition) throws IllegalArgumentException {
        LoanValidation.requireNonNull(startedAt, "Started at");
        LoanValidation.requireNonNull(status, "Loan status");
        Loan loan = start(loanId, loanRequestId, memberId, equipmentId, endDate,
                Clock.fixed(startedAt, java.time.ZoneOffset.UTC));
        loan.status = status;
        loan.reportedReturnCondition = reportedReturnCondition;
        loan.validateRestoredState();
        return loan;
    }

    /**
     * Returns this loan's immutable identity.
     *
     * @return Loan identity.
     */
    public LoanId loanId() {
        return loanId;
    }

    /**
     * Returns the request that produced this loan.
     *
     * @return Source loan request identity.
     */
    public LoanRequestId loanRequestId() {
        return loanRequestId;
    }

    /**
     * Returns the Member assigned to this loan.
     *
     * @return Assigned Member identity.
     */
    public MemberId memberId() {
        return memberId;
    }

    /**
     * Returns the physical item assigned to this loan.
     *
     * @return Assigned equipment identity.
     */
    public EquipmentId equipmentId() {
        return equipmentId;
    }

    /**
     * Returns the instant at which this loan began.
     *
     * @return Loan start instant.
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Returns this loan's immutable end date.
     *
     * @return Loan end date.
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Returns the current loan lifecycle status.
     *
     * @return Current loan status.
     */
    public LoanStatus status() {
        return status;
    }

    /**
     * Returns the Member's reported return condition when a return is pending.
     *
     * @return Reported return condition, or empty when none was submitted.
     */
    public Optional<ReportedReturnCondition> reportedReturnCondition() {
        return Optional.ofNullable(reportedReturnCondition);
    }

    /**
     * Submits an individual return for Exco verification.
     *
     * @param condition Condition reported by the Member.
     * @throws IllegalArgumentException If the condition is null.
     * @throws IllegalStateException If this loan is not currently on loan.
     */
    public void submitReturn(ReportedReturnCondition condition)
            throws IllegalArgumentException, IllegalStateException {
        requireStatus(LoanStatus.ON_LOAN, "submit a return");
        LoanValidation.requireNonNull(condition, "Reported return condition");

        reportedReturnCondition = condition;
        status = LoanStatus.RETURN_PENDING;
    }

    /**
     * Submits a loss report for Exco confirmation.
     *
     * @throws IllegalStateException If this loan is not currently on loan.
     */
    public void submitLost() throws IllegalStateException {
        requireStatus(LoanStatus.ON_LOAN, "submit a loss report");
        status = LoanStatus.LOST_PENDING;
    }

    /**
     * Completes a pending return after Exco verification.
     *
     * @throws IllegalStateException If this loan is not awaiting return verification.
     */
    public void completeReturn() throws IllegalStateException {
        requireStatus(LoanStatus.RETURN_PENDING, "complete a return");
        status = LoanStatus.COMPLETED;
    }

    /**
     * Completes a pending loss report after Exco confirmation.
     *
     * @throws IllegalStateException If this loan is not awaiting loss confirmation.
     */
    public void completeLoss() throws IllegalStateException {
        requireStatus(LoanStatus.LOST_PENDING, "complete a loss report");
        status = LoanStatus.COMPLETED;
    }

    /**
     * Returns whether this loan is overdue on the supplied local date.
     *
     * @param currentDate Date used for the overdue comparison.
     * @return True only when the loan is ON_LOAN and the current date is after its end date.
     * @throws IllegalArgumentException If the current date is null.
     */
    public boolean isOverdue(LocalDate currentDate) throws IllegalArgumentException {
        LoanValidation.requireNonNull(currentDate, "Current date");
        return status == LoanStatus.ON_LOAN && currentDate.isAfter(endDate);
    }

    /**
     * Validates the persisted relationship between Loan status and reported condition.
     *
     * @throws IllegalArgumentException If the relationship is invalid.
     */
    private void validateRestoredState() throws IllegalArgumentException {
        boolean hasCondition = reportedReturnCondition != null;
        if ((status == LoanStatus.ON_LOAN || status == LoanStatus.LOST_PENDING) && hasCondition) {
            throw new IllegalArgumentException("Loan status cannot have a return condition.");
        }
        if (status == LoanStatus.RETURN_PENDING && !hasCondition) {
            throw new IllegalArgumentException("Pending return must have a return condition.");
        }
    }

    /**
     * Requires the loan to be in the expected state before a lifecycle transition.
     *
     * @param expectedStatus Required current status.
     * @param operationName Operation being attempted.
     * @throws IllegalStateException If the current status differs from the expected status.
     */
    private void requireStatus(LoanStatus expectedStatus, String operationName)
            throws IllegalStateException {
        if (status != expectedStatus) {
            throw new IllegalStateException("Cannot " + operationName + " loan in state "
                    + status + ".");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Loan loan)) {
            return false;
        }

        return loanId.equals(loan.loanId);
    }

    @Override
    public int hashCode() {
        return loanId.hashCode();
    }
}
