package clubstock.application.request;

import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;

/**
 * Performs Exco-authorized pending LoanRequest queries and manual rejection.
 */
public final class ExcoRequestService {
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final AvailabilityPolicy availabilityPolicy;

    /**
     * Creates the Exco request-decision service.
     *
     * @param transactionManager Shared read/write transaction boundary.
     * @param sessionManager Authenticated principal source.
     * @param availabilityPolicy Shared caller-owned availability calculation.
     */
    public ExcoRequestService(TransactionManager transactionManager, SessionManager sessionManager,
            AvailabilityPolicy availabilityPolicy) {
        if (transactionManager == null || sessionManager == null || availabilityPolicy == null) {
            throw new IllegalArgumentException("Exco request dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.availabilityPolicy = availabilityPolicy;
    }

    /**
     * Lists the current pending-request queue in deterministic submission order.
     *
     * @return Complete Exco-safe summaries ordered by requestedAt then Request ID.
     */
    public List<ExcoPendingRequest> listPendingRequests() {
        sessionManager.requireExco();
        return transactionManager.read(unitOfWork -> unitOfWork.loanRequests()
                .findPendingOrderedByRequestedAt().stream()
                .map(request -> pendingRequest(unitOfWork, request))
                .toList());
    }

    /**
     * Rejects a selected request only while it remains pending.
     *
     * @param selection Selected opaque request identity.
     */
    public void rejectRequest(PendingRequestSelection selection) {
        sessionManager.requireExco();
        if (selection == null) {
            throw validation("Select a pending request before rejecting it.", null);
        }
        LoanRequestId requestId = validatedRequestId(selection.loanRequestId());
        transactionManager.write(unitOfWork -> {
            LoanRequest request = unitOfWork.loanRequests().findById(requestId).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "The selected request no longer exists.", null));
            try {
                request.reject();
            } catch (IllegalStateException exception) {
                throw conflict("This request is no longer pending.", exception);
            }
            unitOfWork.loanRequests().update(request);
            return null;
        });
    }

    private ExcoPendingRequest pendingRequest(UnitOfWork unitOfWork, LoanRequest request) {
        Member member = unitOfWork.members().findById(request.memberId()).orElseThrow(() ->
                integrityFailure("A pending request references a missing Member."));
        EquipmentType type = unitOfWork.equipmentTypes().findById(request.equipmentTypeId())
                .orElseThrow(() -> integrityFailure(
                        "A pending request references a missing equipment type."));
        return new ExcoPendingRequest(request.loanRequestId().value(), request.memberId().value(),
                member.name(), request.equipmentTypeId().value(), type.name().value(),
                request.requestedQuantity(), availabilityPolicy.countAvailable(unitOfWork,
                        request.equipmentTypeId()), request.requestedStartDate(),
                request.requestedEndDate(), request.requestedAt(), request.details());
    }

    private static LoanRequestId validatedRequestId(String loanRequestId) {
        try {
            return new LoanRequestId(loanRequestId);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static ApplicationException validation(String message, Throwable cause) {
        String safeMessage = message == null || message.isBlank()
                ? "Request selection is invalid." : message;
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, safeMessage, cause);
    }

    private static ApplicationException conflict(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.CONFLICT, message, cause);
    }

    private static ApplicationException integrityFailure(String message) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE, message, null);
    }
}
