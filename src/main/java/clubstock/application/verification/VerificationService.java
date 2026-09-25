package clubstock.application.verification;

import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.loan.ReportedReturnCondition;

/** Performs Exco-authorized authoritative resolution of pending return and loss reports. */
public final class VerificationService {
    private final TransactionManager transactions;
    private final SessionManager sessions;

    /**
     * Creates the verification boundary.
     *
     * @param transactions Shared transaction manager.
     * @param sessions Current authentication session.
     */
    public VerificationService(TransactionManager transactions, SessionManager sessions) {
        if (transactions == null || sessions == null) {
            throw new IllegalArgumentException("Verification dependencies cannot be null.");
        }
        this.transactions = transactions;
        this.sessions = sessions;
    }

    /**
     * Lists all Exco-visible advisory reports whose loans await resolution.
     *
     * @return Safe pending-report summaries.
     */
    public List<PendingVerification> listPending() {
        sessions.requireExco();
        return transactions.read(unit -> unit.loans().findAll().stream()
                .filter(VerificationService::isPendingVerification)
                .map(loan -> pendingVerification(unit, loan))
                .toList());
    }

    /**
     * Verifies a pending return as good.
     *
     * @param loanId Pending return Loan ID.
     */
    public void verifyGood(String loanId) {
        resolveReturn(loanId, true, false);
    }

    /**
     * Verifies a pending return as damaged with Exco's availability assessment.
     *
     * @param loanId Pending return Loan ID.
     * @param available Whether Exco clears the damaged item for allocation.
     */
    public void verifyDamaged(String loanId, boolean available) {
        resolveReturn(loanId, false, available);
    }

    /**
     * Confirms a pending loss report and permanently marks its item lost.
     *
     * @param loanId Pending loss Loan ID.
     */
    public void confirmLost(String loanId) {
        sessions.requireExco();
        transactions.write(unit -> {
            Loan loan = findLoan(unit, loanId);
            if (loan.status() != LoanStatus.LOST_PENDING
                    || unit.lossReports().findByLoanId(loan.loanId()).isEmpty()) {
                throw conflict();
            }
            var item = unit.equipmentItems().findById(loan.equipmentId())
                    .orElseThrow(() -> missing("Equipment item"));
            loan.completeLoss();
            item.confirmLost();
            unit.loans().update(loan);
            unit.equipmentItems().update(item);
            return null;
        });
    }

    private void resolveReturn(String loanId, boolean good, boolean available) {
        sessions.requireExco();
        transactions.write(unit -> {
            Loan loan = findLoan(unit, loanId);
            if (loan.status() != LoanStatus.RETURN_PENDING) {
                throw conflict();
            }
            requireDamageEvidenceWhenReported(unit, loan);
            var item = unit.equipmentItems().findById(loan.equipmentId())
                    .orElseThrow(() -> missing("Equipment item"));
            loan.completeReturn();
            if (good) {
                item.verifyGood();
            } else {
                item.verifyDamaged(available);
            }
            unit.loans().update(loan);
            unit.equipmentItems().update(item);
            return null;
        });
    }

    private static boolean isPendingVerification(Loan loan) {
        return loan.status() == LoanStatus.RETURN_PENDING || loan.status() == LoanStatus.LOST_PENDING;
    }

    private static PendingVerification pendingVerification(UnitOfWork unit, Loan loan) {
        var item = unit.equipmentItems().findById(loan.equipmentId())
                .orElseThrow(() -> missing("Equipment item"));
        var member = unit.members().findById(loan.memberId()).orElseThrow(() -> missing("Member"));
        var type = unit.equipmentTypes().findById(item.equipmentTypeId())
                .orElseThrow(() -> missing("Equipment type"));
        if (loan.status() == LoanStatus.RETURN_PENDING) {
            var damage = unit.damageReports().findByLoanId(loan.loanId());
            requireDamageEvidenceWhenReported(unit, loan);
            return new PendingVerification(loan.loanId().value(), member.name(), type.name().value(),
                    item.equipmentId().value(), "RETURN",
                    loan.reportedReturnCondition().map(Enum::name).orElse("GOOD"),
                    damage.map(report -> report.imageReference().storageKey()).orElse(""),
                    damage.map(report -> report.description()).orElse(""));
        }
        var loss = unit.lossReports().findByLoanId(loan.loanId())
                .orElseThrow(() -> missing("Loss report"));
        return new PendingVerification(loan.loanId().value(), member.name(), type.name().value(),
                item.equipmentId().value(), "LOSS", "LOST", "", loss.description());
    }

    private static Loan findLoan(UnitOfWork unit, String loanId) {
        return unit.loans().findById(id(loanId)).orElseThrow(() -> missing("Loan"));
    }

    private static void requireDamageEvidenceWhenReported(UnitOfWork unit, Loan loan) {
        if (loan.reportedReturnCondition().orElse(null) == ReportedReturnCondition.DAMAGED
                && unit.damageReports().findByLoanId(loan.loanId()).isEmpty()) {
            throw conflict();
        }
    }

    private static LoanId id(String value) {
        try {
            return new LoanId(value);
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED,
                    "Loan selection is invalid.", exception);
        }
    }

    private static ApplicationException missing(String kind) {
        return new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                kind + " no longer exists.", null);
    }

    private static ApplicationException conflict() {
        return new ApplicationException(ApplicationErrorCode.CONFLICT,
                "This report is no longer pending.", null);
    }
}
