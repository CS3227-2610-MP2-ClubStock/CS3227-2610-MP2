package clubstock.application.request;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.port.IdGenerator;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

/** Performs Exco-authorized, atomic request approval and item allocation. */
public final class ApprovalService {
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final AvailabilityPolicy availabilityPolicy;
    private final IdGenerator idGenerator;
    private final Clock clock;

    /** Creates the approval boundary. */
    public ApprovalService(TransactionManager transactionManager, SessionManager sessionManager,
            AvailabilityPolicy availabilityPolicy, IdGenerator idGenerator, Clock clock) {
        if (transactionManager == null || sessionManager == null || availabilityPolicy == null
                || idGenerator == null || clock == null) {
            throw new IllegalArgumentException("Approval dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.availabilityPolicy = availabilityPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /** Returns currently allocatable IDs for a pending request, for Exco selection. */
    public List<String> listAvailableEquipmentIds(String requestId) {
        sessionManager.requireExco();
        LoanRequestId id = requestId(requestId);
        return transactionManager.read(unitOfWork -> {
            LoanRequest request = pendingRequest(unitOfWork, id);
            return unitOfWork.equipmentItems().findByType(request.equipmentTypeId()).stream()
                    .filter(item -> !item.isRetired())
                    .filter(item -> item.availability() == EquipmentAvailability.AVAILABLE)
                    .map(item -> item.equipmentId().value()).toList();
        });
    }

    /** Approves the selected pending request atomically. */
    public void approve(ApprovalSelection selection) {
        sessionManager.requireExco();
        if (selection == null || selection.equipmentIds().isEmpty()) {
            throw validation("Select at least one available item.", null);
        }
        LoanRequestId requestId = requestId(selection.loanRequestId());
        List<EquipmentId> itemIds = itemIds(selection.equipmentIds());
        if (new HashSet<>(itemIds).size() != itemIds.size()) {
            throw validation("Each selected equipment ID must be unique.", null);
        }
        transactionManager.write(unitOfWork -> {
            LoanRequest request = pendingRequest(unitOfWork, requestId);
            if (itemIds.size() > request.requestedQuantity()) {
                throw validation("Selected quantity cannot exceed the requested quantity.", null);
            }
            List<EquipmentItem> items = itemIds.stream().map(itemId -> unitOfWork.equipmentItems()
                    .findById(itemId).orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.NOT_FOUND, "A selected equipment item no longer exists.", null)))
                    .toList();
            for (EquipmentItem item : items) {
                if (item.isRetired() || item.availability() != EquipmentAvailability.AVAILABLE) {
                    throw conflict("A selected equipment item is no longer available.", null);
                }
                if (!item.equipmentTypeId().equals(request.equipmentTypeId())) {
                    throw validation("Selected equipment must match the requested equipment type.", null);
                }
            }
            for (EquipmentItem item : items) {
                item.allocate();
                unitOfWork.equipmentItems().update(item);
                unitOfWork.loans().insert(Loan.start(new LoanId(idGenerator.generateId()),
                        request.loanRequestId(), request.memberId(), item.equipmentId(),
                        request.requestedEndDate(), clock));
            }
            request.approve(items.size());
            unitOfWork.loanRequests().update(request);
            if (availabilityPolicy.countAvailable(unitOfWork, request.equipmentTypeId()) == 0) {
                unitOfWork.loanRequests().findPendingOrderedByRequestedAt().stream()
                        .filter(other -> !other.loanRequestId().equals(request.loanRequestId()))
                        .filter(other -> other.equipmentTypeId().equals(request.equipmentTypeId()))
                        .forEach(other -> { other.reject(); unitOfWork.loanRequests().update(other); });
            }
            return null;
        });
    }

    private static LoanRequest pendingRequest(UnitOfWork unitOfWork, LoanRequestId requestId) {
        LoanRequest request = unitOfWork.loanRequests().findById(requestId).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                        "The selected request no longer exists.", null));
        if (request.status() != LoanRequestStatus.PENDING) {
            throw conflict("This request is no longer pending.", null);
        }
        return request;
    }

    private static LoanRequestId requestId(String value) {
        try { return new LoanRequestId(value); } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static List<EquipmentId> itemIds(List<String> values) {
        try { return values.stream().map(EquipmentId::new).toList(); }
        catch (IllegalArgumentException exception) { throw validation(exception.getMessage(), exception); }
    }

    private static ApplicationException validation(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, message, cause);
    }

    private static ApplicationException conflict(String message, Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.CONFLICT, message, cause);
    }
}
