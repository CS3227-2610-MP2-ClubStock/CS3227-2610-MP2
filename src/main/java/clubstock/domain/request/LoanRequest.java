package clubstock.domain.request;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentTypeId;

/**
 * Represents a Member's request for a quantity of one equipment type.
 */
public final class LoanRequest {
    private final LoanRequestId loanRequestId;
    private final MemberId memberId;
    private final EquipmentTypeId equipmentTypeId;
    private final int requestedQuantity;
    private final LocalDate requestedStartDate;
    private final LocalDate requestedEndDate;
    private final Optional<String> details;
    private final Instant requestedAt;
    private LoanRequestStatus status;
    private Integer approvedQuantity;

    private LoanRequest(LoanRequestId loanRequestId, MemberId memberId,
            EquipmentTypeId equipmentTypeId, int requestedQuantity, LocalDate requestedStartDate,
            LocalDate requestedEndDate, String details, Clock clock) {
        this.loanRequestId = loanRequestId;
        this.memberId = memberId;
        this.equipmentTypeId = equipmentTypeId;
        this.requestedQuantity = requestedQuantity;
        this.requestedStartDate = requestedStartDate;
        this.requestedEndDate = requestedEndDate;
        this.details = Optional.ofNullable(RequestValidation.normalizeOptionalDetails(details));
        requestedAt = clock.instant();
        status = LoanRequestStatus.PENDING;
    }

    /**
     * Submits a new pending loan request using the supplied clock.
     *
     * @param loanRequestId Stable request identity.
     * @param memberId Requesting Member identity.
     * @param equipmentTypeId Requested equipment type identity.
     * @param requestedQuantity Positive quantity requested by the Member.
     * @param requestedStartDate Informational requested start date.
     * @param requestedEndDate Requested end date, not before the start date.
     * @param details Optional request details.
     * @param clock Clock used to record submission time.
     * @return Newly submitted pending request.
     * @throws IllegalArgumentException If an argument is invalid or the date range is reversed.
     */
    public static LoanRequest submit(LoanRequestId loanRequestId, MemberId memberId,
            EquipmentTypeId equipmentTypeId, int requestedQuantity, LocalDate requestedStartDate,
            LocalDate requestedEndDate, String details, Clock clock)
            throws IllegalArgumentException {
        RequestValidation.requireNonNull(loanRequestId, "Loan request ID");
        RequestValidation.requireNonNull(memberId, "Member ID");
        RequestValidation.requireNonNull(equipmentTypeId, "Equipment type ID");
        RequestValidation.requireNonNull(requestedStartDate, "Requested start date");
        RequestValidation.requireNonNull(requestedEndDate, "Requested end date");
        RequestValidation.requireNonNull(clock, "Clock");

        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive.");
        }
        if (requestedEndDate.isBefore(requestedStartDate)) {
            throw new IllegalArgumentException(
                    "Requested end date cannot be before requested start date.");
        }

        return new LoanRequest(loanRequestId, memberId, equipmentTypeId, requestedQuantity,
                requestedStartDate, requestedEndDate, details, clock);
    }

    /**
     * Returns this request's immutable identity.
     *
     * @return Loan request identity.
     */
    public LoanRequestId loanRequestId() {
        return loanRequestId;
    }

    /**
     * Returns the requesting Member identity.
     *
     * @return Requesting Member identity.
     */
    public MemberId memberId() {
        return memberId;
    }

    /**
     * Returns the requested equipment type identity.
     *
     * @return Requested equipment type identity.
     */
    public EquipmentTypeId equipmentTypeId() {
        return equipmentTypeId;
    }

    /**
     * Returns the quantity requested by the Member.
     *
     * @return Requested quantity.
     */
    public int requestedQuantity() {
        return requestedQuantity;
    }

    /**
     * Returns the informational requested start date.
     *
     * @return Requested start date.
     */
    public LocalDate requestedStartDate() {
        return requestedStartDate;
    }

    /**
     * Returns the requested end date.
     *
     * @return Requested end date.
     */
    public LocalDate requestedEndDate() {
        return requestedEndDate;
    }

    /**
     * Returns normalized optional request details.
     *
     * @return Empty when no details were supplied.
     */
    public Optional<String> details() {
        return details;
    }

    /**
     * Returns the automatically recorded submission time.
     *
     * @return Request submission instant.
     */
    public Instant requestedAt() {
        return requestedAt;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return Current request status.
     */
    public LoanRequestStatus status() {
        return status;
    }

    /**
     * Returns the approved quantity when this request has been approved.
     *
     * @return Approved quantity, or empty when the request is not approved.
     */
    public OptionalInt approvedQuantity() {
        return approvedQuantity == null
                ? OptionalInt.empty()
                : OptionalInt.of(approvedQuantity);
    }

    /**
     * Approves this pending request with the selected quantity.
     *
     * @param approvedQuantity Quantity approved by Exco.
     * @throws IllegalArgumentException If the quantity is outside the requested range.
     * @throws IllegalStateException If this request is not pending.
     */
    public void approve(int approvedQuantity)
            throws IllegalArgumentException, IllegalStateException {
        requirePending("approve");
        if (approvedQuantity <= 0 || approvedQuantity > requestedQuantity) {
            throw new IllegalArgumentException(
                    "Approved quantity must be between 1 and requested quantity.");
        }

        this.approvedQuantity = approvedQuantity;
        status = LoanRequestStatus.APPROVED;
    }

    /**
     * Rejects this pending request.
     *
     * @throws IllegalStateException If this request is not pending.
     */
    public void reject() throws IllegalStateException {
        requirePending("reject");
        status = LoanRequestStatus.REJECTED;
    }

    /**
     * Cancels this pending request when requested by its owner.
     *
     * @param cancellingMemberId Member attempting cancellation.
     * @throws IllegalArgumentException If the Member is null or is not the owner.
     * @throws IllegalStateException If this request is not pending.
     */
    public void cancelBy(MemberId cancellingMemberId)
            throws IllegalArgumentException, IllegalStateException {
        requirePending("cancel");
        RequestValidation.requireNonNull(cancellingMemberId, "Cancelling Member ID");
        if (!memberId.equals(cancellingMemberId)) {
            throw new IllegalArgumentException("Only the requesting Member can cancel the request.");
        }

        status = LoanRequestStatus.CANCELLED;
    }

    /**
     * Requires this request to remain pending for a lifecycle transition.
     *
     * @param operationName Name of the attempted operation.
     * @throws IllegalStateException If the request is not pending.
     */
    private void requirePending(String operationName) throws IllegalStateException {
        if (status != LoanRequestStatus.PENDING) {
            throw new IllegalStateException("Cannot " + operationName + " request in state "
                    + status + ".");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof LoanRequest loanRequest)) {
            return false;
        }

        return loanRequestId.equals(loanRequest.loanRequestId);
    }

    @Override
    public int hashCode() {
        return loanRequestId.hashCode();
    }
}
