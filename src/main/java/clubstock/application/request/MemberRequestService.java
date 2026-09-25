package clubstock.application.request;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.port.IdGenerator;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

/**
 * Previews, submits, lists, and cancels requests for the authenticated Member.
 */
public final class MemberRequestService {
    private static final String MEMBER_UNAVAILABLE_MESSAGE =
            "This operation is not available for the current session.";

    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final AvailabilityPolicy availabilityPolicy;
    private final IdGenerator idGenerator;
    private final Clock clock;

    /**
     * Creates the Member request service.
     *
     * @param transactionManager Shared read/write transaction boundary.
     * @param sessionManager Authenticated principal source.
     * @param availabilityPolicy Shared caller-owned available-quantity calculation.
     * @param idGenerator Request identity generator.
     * @param clock Clock used to record submission time.
     * @throws IllegalArgumentException If any dependency is null.
     */
    public MemberRequestService(TransactionManager transactionManager,
            SessionManager sessionManager, AvailabilityPolicy availabilityPolicy,
            IdGenerator idGenerator, Clock clock) {
        if (transactionManager == null || sessionManager == null || availabilityPolicy == null
                || idGenerator == null || clock == null) {
            throw new IllegalArgumentException("Member request dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.availabilityPolicy = availabilityPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Returns a validated request draft and the current available quantity for its type.
     *
     * @param draft Member-entered request values.
     * @return Current type and stock summary with normalized draft details.
     */
    public RequestPreview preview(RequestDraft draft) {
        MemberId memberId = sessionManager.requireMember();
        return transactionManager.read(unitOfWork -> {
            sessionManager.requireMember(memberId);
            requireActiveMember(unitOfWork, memberId);
            RequestDraft validatedDraft = validatedDraft(draft);
            EquipmentType type = offeredType(unitOfWork, validatedDraft.equipmentTypeId());
            int availableQuantity = availabilityPolicy.countAvailable(unitOfWork,
                    type.equipmentTypeId());
            return new RequestPreview(type.equipmentTypeId(), type.name().value(),
                    availableQuantity, validatedDraft);
        });
    }

    /**
     * Revalidates and submits a pending request using the current shared state.
     *
     * @param draft Member-entered request values.
     * @param confirmedZeroStock Whether the Member confirmed submission when no items are
     *        currently available.
     * @return Generated LoanRequest identity.
     * @throws ApplicationException If the session, request details, type, or stock confirmation
     *         is invalid.
     */
    public LoanRequestId submit(RequestDraft draft, boolean confirmedZeroStock) {
        MemberId memberId = sessionManager.requireMember();
        return transactionManager.write(unitOfWork -> {
            sessionManager.requireMember(memberId);
            requireActiveMember(unitOfWork, memberId);
            RequestDraft validatedDraft = validatedDraft(draft);
            EquipmentType type = offeredType(unitOfWork, validatedDraft.equipmentTypeId());
            int availableQuantity = availabilityPolicy.countAvailable(unitOfWork,
                    type.equipmentTypeId());
            if (availableQuantity == 0 && !confirmedZeroStock) {
                throw new ApplicationException(
                        ApplicationErrorCode.ZERO_STOCK_CONFIRMATION_REQUIRED,
                        "Confirm submission while no items are currently available.", null);
            }

            LoanRequestId requestId = generatedRequestId();
            LoanRequest request = LoanRequest.submit(requestId, memberId,
                    type.equipmentTypeId(), validatedDraft.quantity(), validatedDraft.startDate(),
                    validatedDraft.endDate(), validatedDraft.details(), clock);
            unitOfWork.loanRequests().insert(request);
            return requestId;
        });
    }

    /**
     * Returns an immutable snapshot of every request owned by the authenticated Member.
     *
     * @return Own requests ordered by submission time descending, then Request ID ascending.
     */
    public List<OwnRequest> listOwnRequests() {
        MemberId memberId = sessionManager.requireMember();
        return transactionManager.read(unitOfWork -> {
            sessionManager.requireMember(memberId);
            requireActiveMember(unitOfWork, memberId);
            List<OwnRequest> requests = unitOfWork.loanRequests().findByMember(memberId).stream()
                    .map(request -> ownRequest(unitOfWork, request))
                    .sorted(Comparator.comparing(OwnRequest::requestedAt).reversed()
                            .thenComparing(request -> request.loanRequestId().value()))
                    .toList();
            return List.copyOf(requests);
        });
    }

    /**
     * Cancels one of the authenticated Member's pending requests.
     *
     * @param requestId Request identity to cancel.
     * @throws ApplicationException If the session, request identity, ownership, or request status
     *         is invalid.
     */
    public void cancelRequest(LoanRequestId requestId) {
        MemberId memberId = sessionManager.requireMember();
        transactionManager.write(unitOfWork -> {
            sessionManager.requireMember(memberId);
            requireActiveMember(unitOfWork, memberId);
            if (requestId == null) {
                throw validation("Select a request to cancel.");
            }
            LoanRequest request = unitOfWork.loanRequests().findById(requestId).orElseThrow(() ->
                    new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                            "The selected request no longer exists.", null));
            if (!request.memberId().equals(memberId)) {
                throw new ApplicationException(ApplicationErrorCode.AUTHORIZATION_DENIED,
                        "This operation is not available for the current session.", null);
            }
            if (request.status() != LoanRequestStatus.PENDING) {
                throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                        "Only a pending request can be cancelled.", null);
            }
            try {
                request.cancelBy(memberId);
            } catch (IllegalStateException exception) {
                throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                        "This request is no longer pending.", exception);
            }
            unitOfWork.loanRequests().update(request);
            return null;
        });
    }

    /**
     * Returns a trimmed, valid draft for the current request operation.
     *
     * @param draft Member-entered request values.
     * @return Validated draft with absent details normalized to {@code null}.
     * @throws ApplicationException If any required field is missing or invalid.
     */
    private static RequestDraft validatedDraft(RequestDraft draft) {
        if (draft == null || draft.equipmentTypeId() == null || draft.quantity() <= 0
                || draft.startDate() == null || draft.endDate() == null) {
            throw validation("Request details are incomplete or invalid.");
        }
        if (draft.endDate().isBefore(draft.startDate())) {
            throw validation("Requested end date cannot be before the start date.");
        }
        String details = draft.details();
        if (details != null) {
            details = details.isBlank() ? null : details.strip();
        }
        return new RequestDraft(draft.equipmentTypeId(), draft.quantity(), draft.startDate(),
                draft.endDate(), details);
    }

    /**
     * Maps a persisted request and its current equipment type name to a Member-safe summary.
     *
     * @param unitOfWork Current transaction-scoped repositories.
     * @param request Persisted request owned by the authenticated Member.
     * @return Request summary without physical equipment identities.
     */
    private static OwnRequest ownRequest(UnitOfWork unitOfWork, LoanRequest request) {
        EquipmentType type = unitOfWork.equipmentTypes().findById(request.equipmentTypeId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "A request references a missing equipment type.", null));
        return new OwnRequest(request.loanRequestId(), request.equipmentTypeId(),
                type.name().value(), request.requestedQuantity(), request.requestedStartDate(),
                request.requestedEndDate(), request.requestedAt(), request.status(),
                request.approvedQuantity());
    }

    /**
     * Requires the authenticated account to remain active inside the current transaction.
     *
     * @param unitOfWork Current transaction-scoped repositories.
     * @param memberId Authenticated Member identity.
     * @throws ApplicationException If the Member account is missing or inactive.
     */
    private static void requireActiveMember(UnitOfWork unitOfWork, MemberId memberId) {
        Member member = unitOfWork.members().findById(memberId)
                .orElseThrow(MemberRequestService::memberUnavailable);
        if (!member.isActive()) {
            throw memberUnavailable();
        }
    }

    /**
     * Returns a type that remains offered for Member requests.
     *
     * @param unitOfWork Current transaction-scoped repositories.
     * @param equipmentTypeId Requested type identity.
     * @return Existing offered equipment type.
     * @throws ApplicationException If the type is missing or no longer offered.
     */
    private static EquipmentType offeredType(UnitOfWork unitOfWork,
            EquipmentTypeId equipmentTypeId) {
        EquipmentType type = unitOfWork.equipmentTypes().findById(equipmentTypeId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "The selected equipment type no longer exists.", null));
        if (!type.isOffered()) {
            throw validation("The selected equipment type is not currently offered.");
        }
        return type;
    }

    /**
     * Returns a generated request ID or reports an invalid generator result as a persistence error.
     *
     * @return Valid generated LoanRequest identity.
     * @throws ApplicationException If the generated identity is invalid.
     */
    private LoanRequestId generatedRequestId() {
        try {
            return new LoanRequestId(idGenerator.generateId());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "A valid request ID could not be generated.", exception);
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
     * Creates a validation failure for invalid Member request details.
     *
     * @param message Safe validation message.
     * @return Validation failure.
     */
    private static ApplicationException validation(String message) {
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, message, null);
    }
}
