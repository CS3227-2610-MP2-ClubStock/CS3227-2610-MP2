package clubstock.application.loan;

import java.nio.file.Path;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.ManagedDamageImageStore;
import clubstock.application.port.StagedDamageImage;
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
import clubstock.domain.report.DamageImageReference;
import clubstock.domain.report.DamageReport;
import clubstock.domain.report.LossReport;

/**
 * Submits Member return and loss reports for individually assigned Loans.
 */
public final class MemberLoanService {
    private static final String MEMBER_UNAVAILABLE_MESSAGE =
            "This operation is not available for the current session.";

    private final TransactionManager transactions;
    private final SessionManager sessions;
    private final ManagedDamageImageStore damageImageStore;

    /**
     * Creates the Member Loan workflow service.
     *
     * @param transactions Shared read/write transaction boundary.
     * @param sessions Authenticated principal source.
     * @param damageImageStore Managed image staging, finalization, and recovery boundary.
     * @throws IllegalArgumentException If any dependency is null.
     */
    public MemberLoanService(TransactionManager transactions, SessionManager sessions,
            ManagedDamageImageStore damageImageStore) {
        if (transactions == null || sessions == null || damageImageStore == null) {
            throw new IllegalArgumentException("Member Loan dependencies cannot be null.");
        }
        this.transactions = transactions;
        this.sessions = sessions;
        this.damageImageStore = damageImageStore;
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
     * Submits one damaged return and its managed image evidence for an individual Loan.
     *
     * @param loanId Loan identity to return.
     * @param description Member's description of the damage.
     * @param sourcePath Selected image path, which may be absolute or relative.
     * @throws ApplicationException If the session, Loan, description, image, or storage state is
     *         invalid, or the transaction outcome cannot be confirmed.
     */
    public void submitDamagedReturn(String loanId, String description, Path sourcePath) {
        MemberId memberId = sessions.requireMember();
        LoanId validatedLoanId = validatedLoanId(loanId);
        String validatedDescription = validateDamageDescription(description);
        if (sourcePath == null) {
            throw validation("Select a JPEG or PNG image for the damage report.");
        }

        damageImageStore.withExclusiveAccess(() -> {
            submitDamagedReturnWhileLocked(memberId, validatedLoanId, validatedDescription,
                    sourcePath);
            return null;
        });
    }

    /**
     * Finalizes evidence and commits its report while reconciliation is excluded.
     */
    private void submitDamagedReturnWhileLocked(MemberId memberId, LoanId validatedLoanId,
            String validatedDescription, Path sourcePath) {
        transactions.read(unit -> {
            sessions.requireMember(memberId);
            requireActiveMember(unit, memberId);
            Loan loan = findOwnedOnLoan(unit, memberId, validatedLoanId.value());
            findOnLoanItem(unit, loan);
            return null;
        });

        StagedDamageImage stagedImage = damageImageStore.stage(sourcePath);
        DamageImageReference imageReference;
        try {
            imageReference = damageImageStore.finalizeImage(stagedImage);
        } catch (RuntimeException exception) {
            throw failureAfterStage(exception, stagedImage);
        }

        DamageReport damageReport;
        try {
            damageReport = DamageReport.create(validatedLoanId, imageReference,
                    validatedDescription);
        } catch (IllegalArgumentException exception) {
            throw failureAfterFinalization(exception, stagedImage, imageReference,
                    null);
        }

        try {
            transactions.write(unit -> {
                sessions.requireMember(memberId);
                requireActiveMember(unit, memberId);
                Loan loan = findOwnedOnLoan(unit, memberId, validatedLoanId.value());
                EquipmentItem item = findOnLoanItem(unit, loan);

                try {
                    loan.submitReturn(ReportedReturnCondition.DAMAGED);
                    item.holdForVerification();
                } catch (IllegalStateException exception) {
                    throw conflict("This Loan is no longer awaiting return.", exception);
                }

                unit.loans().update(loan);
                unit.equipmentItems().update(item);
                unit.damageReports().insert(damageReport);
                return null;
            });
        } catch (RuntimeException exception) {
            TransactionOutcome outcome = transactionOutcome(exception);
            if (outcome == TransactionOutcome.COMMITTED
                    && isDamagedReturnCommitted(validatedLoanId, memberId, imageReference)) {
                discardStageOrThrow(stagedImage, exception, outcome);
                return;
            }
            throw failureAfterFinalization(exception, stagedImage, imageReference, outcome);
        }

        discardStageOrThrow(stagedImage, null, TransactionOutcome.COMMITTED);
    }

    /**
     * Returns whether the committed repositories contain the expected damaged-return state.
     */
    private boolean isDamagedReturnCommitted(LoanId loanId, MemberId memberId,
            DamageImageReference imageReference) {
        try {
            return transactions.read(unit -> {
                Loan loan = unit.loans().findById(loanId).orElse(null);
                if (loan == null || !loan.memberId().equals(memberId)
                        || loan.status() != LoanStatus.RETURN_PENDING
                        || loan.reportedReturnCondition().orElse(null)
                                != ReportedReturnCondition.DAMAGED) {
                    return false;
                }
                EquipmentItem item = unit.equipmentItems().findById(loan.equipmentId()).orElse(null);
                DamageReport report = unit.damageReports().findByLoanId(loanId).orElse(null);
                return item != null && item.isVerificationPending()
                        && item.availability() == EquipmentAvailability.UNAVAILABLE
                        && report != null
                        && report.imageReference().storageKey().equals(imageReference.storageKey());
            });
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Cleans up stage storage after an operation whose finalized-file handling is already safe.
     */
    private void discardStageOrThrow(StagedDamageImage stagedImage, RuntimeException cause,
            TransactionOutcome outcome) {
        try {
            damageImageStore.discardStaged(stagedImage);
        } catch (RuntimeException cleanupException) {
            if (cause != null) {
                cleanupException.addSuppressed(cause);
            }
            throw imageStorageFailure(cleanupException, outcome,
                    outcome == TransactionOutcome.COMMITTED
                            ? "The damaged return was saved, but temporary image cleanup failed."
                            : "Damage evidence could not be cleaned up safely.");
        }
    }

    /**
     * Handles finalization failure by removing only the known staged file.
     */
    private RuntimeException failureAfterStage(RuntimeException cause,
            StagedDamageImage stagedImage) {
        try {
            damageImageStore.discardStaged(stagedImage);
        } catch (RuntimeException cleanupException) {
            cleanupException.addSuppressed(cause);
            return imageStorageFailure(cleanupException, null,
                    "Damage evidence could not be cleaned up safely.");
        }
        return cause;
    }

    /**
     * Handles a failed write while retaining files whenever a committed reference is possible.
     */
    private RuntimeException failureAfterFinalization(RuntimeException cause,
            StagedDamageImage stagedImage, DamageImageReference imageReference,
            TransactionOutcome outcome) {
        boolean mayDeleteFinalized = outcome == TransactionOutcome.CONFIRMED_ROLLBACK;
        if (outcome != TransactionOutcome.CONFIRMED_ROLLBACK
                && outcome != TransactionOutcome.COMMITTED) {
            Boolean isReferenced = isImageReferenceCommitted(imageReference);
            mayDeleteFinalized = Boolean.FALSE.equals(isReferenced);
            if (!mayDeleteFinalized) {
                outcome = TransactionOutcome.COMMIT_OUTCOME_UNKNOWN;
            }
        }

        RuntimeException cleanupFailure = null;
        if (mayDeleteFinalized) {
            try {
                damageImageStore.discardFinalized(imageReference);
            } catch (RuntimeException exception) {
                cleanupFailure = exception;
            }
        }
        try {
            damageImageStore.discardStaged(stagedImage);
        } catch (RuntimeException exception) {
            if (cleanupFailure == null) {
                cleanupFailure = exception;
            } else {
                cleanupFailure.addSuppressed(exception);
            }
        }

        if (cleanupFailure != null) {
            cleanupFailure.addSuppressed(cause);
            throw imageStorageFailure(cleanupFailure, outcome,
                    outcome == TransactionOutcome.COMMITTED
                            ? "The damaged return may have been saved, but image cleanup failed."
                            : "Damage evidence could not be cleaned up safely.");
        }

        if (outcome == TransactionOutcome.COMMITTED) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "The damaged return may have been saved. Refresh the Loan before retrying.",
                    cause, TransactionOutcome.COMMITTED);
        }
        if (cause instanceof ApplicationException applicationException
                && (outcome == null || applicationException.transactionOutcome().isPresent())) {
            throw applicationException;
        }
        if (outcome != null) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "The damaged return could not be confirmed. Refresh the Loan before retrying.",
                    cause, outcome);
        }
        throw cause;
    }

    /**
     * Independently checks all committed reports before compensating an uncertain write.
     *
     * @return True/false when the scan succeeds, or null when its result is uncertain.
     */
    private Boolean isImageReferenceCommitted(DamageImageReference imageReference) {
        try {
            return transactions.read(unit -> unit.damageReports().findAll().stream()
                    .anyMatch(report -> report.imageReference().storageKey()
                            .equals(imageReference.storageKey())));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static TransactionOutcome transactionOutcome(RuntimeException exception) {
        if (exception instanceof ApplicationException applicationException) {
            return applicationException.transactionOutcome().orElse(null);
        }
        return null;
    }

    private static ApplicationException imageStorageFailure(Throwable cause,
            TransactionOutcome outcome, String message) {
        return new ApplicationException(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                message, cause, outcome);
    }

    private static String validateDamageDescription(String description) {
        if (description == null || description.isBlank()) {
            throw validation("Enter a description for the damage report.");
        }
        return description.strip();
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
