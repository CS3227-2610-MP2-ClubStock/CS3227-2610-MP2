package clubstock.application.loan;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.loan.ReportedReturnCondition;
import clubstock.domain.report.LossReport;

/**
 * Submits Member return and loss reports for individually assigned Loans.
 */
public final class MemberLoanService {
    private static final String MEMBER_UNAVAILABLE_MESSAGE =
            "This operation is not available for the current session.";

    private final TransactionManager transactions;
    private final SessionManager sessions;

    /**
     * Creates the Member Loan workflow service.
     *
     * @param transactions Shared read/write transaction boundary.
     * @param sessions Authenticated principal source.
     * @throws IllegalArgumentException If either dependency is null.
     */
    public MemberLoanService(TransactionManager transactions, SessionManager sessions) {
        if (transactions == null || sessions == null) {
            throw new IllegalArgumentException("Member Loan dependencies cannot be null.");
        }
        this.transactions = transactions;
        this.sessions = sessions;
    }

    /**
     * Submits one good-condition return for an individual Loan owned by the current Member.
     *
     * @param loanId Loan identity to return.
     * @throws ApplicationException If the session, ownership, Loan status, or equipment state is
     *         invalid.
     */
    public void submitGoodReturn(String loanId) {
        MemberId memberId = sessions.requireMember();
        transactions.write(unit -> {
            sessions.requireMember(memberId);
            requireActiveMember(unit, memberId);
            Loan loan = findOwnedOnLoan(unit, memberId, loanId);
            EquipmentItem item = findOnLoanItem(unit, loan);

            try {
                loan.submitReturn(ReportedReturnCondition.GOOD);
                item.holdForVerification();
            } catch (IllegalStateException exception) {
                throw conflict("This Loan is no longer awaiting return.", exception);
            }

            unit.loans().update(loan);
            unit.equipmentItems().update(item);
            return null;
        });
    }

    /**
     * Submits a loss report for an individual Loan owned by the current Member.
     *
     * @param loanId Loan identity to report as lost.
     * @param description Member's description of the loss.
     * @throws ApplicationException If the session, ownership, description, Loan status, or
     *         equipment state is invalid.
     */
    public void submitLost(String loanId, String description) {
        MemberId memberId = sessions.requireMember();
        transactions.write(unit -> {
            sessions.requireMember(memberId);
            requireActiveMember(unit, memberId);
            Loan loan = findOwnedOnLoan(unit, memberId, loanId);
            EquipmentItem item = findOnLoanItem(unit, loan);
            LossReport lossReport = createLossReport(loan, description);

            try {
                loan.submitLost();
                item.holdForVerification();
            } catch (IllegalStateException exception) {
                throw conflict("This Loan is no longer awaiting a loss report.", exception);
            }

            unit.loans().update(loan);
            unit.equipmentItems().update(item);
            unit.lossReports().insert(lossReport);
            return null;
        });
    }

    /**
     * Returns an owned Loan that remains on loan after transaction-scoped checks.
     *
     * @param unit Current transaction-scoped repositories.
     * @param memberId Authenticated Member identity.
     * @param loanId Supplied Loan identity.
     * @return Matching Loan currently assigned to the Member.
     * @throws ApplicationException If the ID is invalid, the Loan is missing, ownership differs,
     *         or its status is not ON_LOAN.
     */
    private static Loan findOwnedOnLoan(UnitOfWork unit, MemberId memberId, String loanId) {
        LoanId validatedLoanId = validatedLoanId(loanId);
        Loan loan = unit.loans().findById(validatedLoanId).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "The selected Loan no longer exists.", null));
        if (!loan.memberId().equals(memberId)) {
            throw new ApplicationException(ApplicationErrorCode.AUTHORIZATION_DENIED,
                    MEMBER_UNAVAILABLE_MESSAGE, null);
        }
        if (loan.status() != LoanStatus.ON_LOAN) {
            throw conflict("Only an active Loan can be returned or reported lost.", null);
        }
        return loan;
    }

    /**
     * Returns the physical item in its expected active-loan state.
     *
     * @param unit Current transaction-scoped repositories.
     * @param loan Loan whose assigned item is checked.
     * @return Assigned item currently on loan.
     * @throws ApplicationException If the Loan references missing or inconsistent equipment.
     */
    private static EquipmentItem findOnLoanItem(UnitOfWork unit, Loan loan) {
        EquipmentItem item = unit.equipmentItems().findById(loan.equipmentId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "The active Loan references missing equipment.", null));
        if (item.availability() != EquipmentAvailability.ON_LOAN
                || item.isVerificationPending() || item.isRetired()) {
            throw conflict("The assigned equipment is no longer available for this action.", null);
        }
        return item;
    }

    /**
     * Requires the authenticated Member account to remain active in the current transaction.
     *
     * @param unit Current transaction-scoped repositories.
     * @param memberId Authenticated Member identity.
     * @throws ApplicationException If the Member account is missing or inactive.
     */
    private static void requireActiveMember(UnitOfWork unit, MemberId memberId) {
        Member member = unit.members().findById(memberId)
                .orElseThrow(MemberLoanService::memberUnavailable);
        if (!member.isActive()) {
            throw memberUnavailable();
        }
    }

    /**
     * Creates a loss report with a validated nonblank description.
     *
     * @param loan Loan being reported.
     * @param description Member's loss description.
     * @return Validated loss report.
     * @throws ApplicationException If the description is null or blank.
     */
    private static LossReport createLossReport(Loan loan, String description) {
        if (description == null || description.isBlank()) {
            throw validation("Enter a description for the lost item.");
        }
        try {
            return LossReport.create(loan.loanId(), description);
        } catch (IllegalArgumentException exception) {
            throw validation("Enter a description for the lost item.", exception);
        }
    }

    /**
     * Parses a required Loan ID.
     *
     * @param loanId Supplied Loan identity.
     * @return Validated Loan identity.
     * @throws ApplicationException If the ID is null or blank.
     */
    private static LoanId validatedLoanId(String loanId) {
        if (loanId == null || loanId.isBlank()) {
            throw validation("Select a Loan to return or report lost.");
        }
        try {
            return new LoanId(loanId);
        } catch (IllegalArgumentException exception) {
            throw validation("Select a valid Loan to return or report lost.", exception);
        }
    }

    /**
     * Creates the safe denial used when the signed-in Member account is absent or inactive.
     *
     * @return Authorization failure.
     */
    private static ApplicationException memberUnavailable() {
        return new ApplicationException(ApplicationErrorCode.AUTHORIZATION_DENIED,
                MEMBER_UNAVAILABLE_MESSAGE, null);
    }

    /**
     * Creates a validation failure for invalid Member-entered values.
     *
     * @param message Safe validation message.
     * @return Validation failure.
     */
    private static ApplicationException validation(String message) {
        return validation(message, null);
    }

    /**
     * Creates a validation failure while preserving its domain-validation cause.
     *
     * @param message Safe validation message.
     * @param cause Domain-validation cause.
     * @return Validation failure.
     */
    private static ApplicationException validation(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, message, cause);
    }

    /**
     * Creates a conflict for a Loan transition that is no longer valid.
     *
     * @param message Safe display message.
     * @param cause Domain-transition cause, when present.
     * @return Conflict failure.
     */
    private static ApplicationException conflict(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.CONFLICT, message, cause);
    }
}
