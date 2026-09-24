package clubstock.application.request;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Exco-safe queue data for one currently pending LoanRequest.
 *
 * @param loanRequestId Opaque request identity used for decisions.
 * @param memberId Requesting Member identity.
 * @param memberName Requesting Member display name.
 * @param equipmentTypeId Requested equipment-category identity.
 * @param equipmentTypeName Requested equipment-category display name.
 * @param requestedQuantity Requested item quantity.
 * @param availableQuantity Current active AVAILABLE item count.
 * @param requestedStartDate Informational requested start date.
 * @param requestedEndDate Requested end date.
 * @param requestedAt Submission timestamp.
 * @param details Optional request details.
 */
public record ExcoPendingRequest(String loanRequestId, String memberId, String memberName,
        String equipmentTypeId, String equipmentTypeName, int requestedQuantity,
        int availableQuantity, LocalDate requestedStartDate, LocalDate requestedEndDate,
        Instant requestedAt, Optional<String> details) {

    /**
     * Validates a complete, non-sensitive pending-queue row.
     */
    public ExcoPendingRequest {
        if (isBlank(loanRequestId) || isBlank(memberId) || isBlank(memberName)
                || isBlank(equipmentTypeId) || isBlank(equipmentTypeName)
                || requestedQuantity <= 0 || availableQuantity < 0 || requestedStartDate == null
                || requestedEndDate == null || requestedAt == null || details == null) {
            throw new IllegalArgumentException("Pending request details are invalid.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
