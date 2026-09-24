package clubstock.application.request;

import java.util.List;

/**
 * Carries one pending request and the explicit physical items selected for approval.
 *
 * @param loanRequestId Opaque request identity.
 * @param equipmentIds Selected physical equipment identities.
 */
public record ApprovalSelection(String loanRequestId, List<String> equipmentIds) {
    /** Validates that the selection structure is present. */
    public ApprovalSelection {
        if (loanRequestId == null || loanRequestId.isBlank() || equipmentIds == null) {
            throw new IllegalArgumentException("Approval selection cannot be blank.");
        }
        equipmentIds = List.copyOf(equipmentIds);
    }
}
