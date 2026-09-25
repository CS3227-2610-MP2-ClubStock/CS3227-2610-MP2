package clubstock.application.request;

/**
 * Identifies a pending request selected by Exco for a later decision workflow.
 *
 * @param loanRequestId Opaque immutable request identity.
 */
public record PendingRequestSelection(String loanRequestId) {
    /**
     * Validates the request-selection identity.
     */
    public PendingRequestSelection {
        if (loanRequestId == null || loanRequestId.isBlank()
                || !loanRequestId.equals(loanRequestId.strip())) {
            throw new IllegalArgumentException("Loan request selection is invalid.");
        }
    }
}
