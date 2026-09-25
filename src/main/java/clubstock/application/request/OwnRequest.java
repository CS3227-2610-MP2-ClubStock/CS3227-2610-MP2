package clubstock.application.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.OptionalInt;

import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

/**
 * Member-safe details for one of the authenticated Member's requests.
 *
 * @param loanRequestId Request identity.
 * @param equipmentTypeId Requested equipment category identity.
 * @param equipmentTypeName Current equipment category name.
 * @param requestedQuantity Requested item quantity.
 * @param requestedStartDate Informational requested start date.
 * @param requestedEndDate Requested end date.
 * @param requestedAt Request submission time.
 * @param status Current request status.
 * @param approvedQuantity Approved quantity when the request is approved.
 */
public record OwnRequest(LoanRequestId loanRequestId, EquipmentTypeId equipmentTypeId,
        String equipmentTypeName, int requestedQuantity, LocalDate requestedStartDate,
        LocalDate requestedEndDate, Instant requestedAt, LoanRequestStatus status,
        OptionalInt approvedQuantity) {

    /**
     * Validates a complete, Member-safe request summary.
     *
     * @throws IllegalArgumentException If a required value is missing or if approved quantity
     *         does not match the request status and quantity.
     */
    public OwnRequest {
        if (loanRequestId == null || equipmentTypeId == null || equipmentTypeName == null
                || equipmentTypeName.isBlank() || requestedQuantity <= 0
                || requestedStartDate == null || requestedEndDate == null
                || requestedEndDate.isBefore(requestedStartDate) || requestedAt == null
                || status == null || approvedQuantity == null) {
            throw new IllegalArgumentException("Own-request details are invalid.");
        }
        boolean isApproved = status == LoanRequestStatus.APPROVED;
        if (isApproved != approvedQuantity.isPresent()
                || (approvedQuantity.isPresent()
                        && (approvedQuantity.getAsInt() <= 0
                                || approvedQuantity.getAsInt() > requestedQuantity))) {
            throw new IllegalArgumentException("Approved request quantity is inconsistent.");
        }
    }
}
