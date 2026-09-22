package clubstock.domain.request;

/**
 * Defines the closed set of loan request lifecycle states.
 */
public enum LoanRequestStatus {
    /** Request is awaiting Exco's decision. */
    PENDING,

    /** Request has been approved with an assigned quantity. */
    APPROVED,

    /** Request has been rejected by Exco or by an exhaustion workflow. */
    REJECTED,

    /** Request has been cancelled by its requesting Member. */
    CANCELLED
}
