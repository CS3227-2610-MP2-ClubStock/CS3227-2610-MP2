package clubstock.application;

/**
 * Describes what is known after a failed write transaction.
 */
public enum TransactionOutcome {
    /**
     * The write committed successfully before a subsequent cleanup failure.
     * Callers must retain resources referenced by the committed records.
     */
    COMMITTED,
    /** The transaction was rolled back and its writes are known not to be durable. */
    CONFIRMED_ROLLBACK,
    /** The caller cannot prove whether the transaction committed. */
    COMMIT_OUTCOME_UNKNOWN
}
