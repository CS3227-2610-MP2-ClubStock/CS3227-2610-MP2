package clubstock.domain.loan;

/**
 * Represents the lifecycle state of an individual loan.
 */
public enum LoanStatus {
    /** Item is currently assigned to a Member. */
    ON_LOAN,
    /** Member has submitted a return awaiting Exco verification. */
    RETURN_PENDING,
    /** Member has submitted a loss report awaiting Exco confirmation. */
    LOST_PENDING,
    /** Exco has completed the return or loss workflow. */
    COMPLETED
}
