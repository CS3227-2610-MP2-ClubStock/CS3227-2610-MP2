package clubstock.application;

import java.util.Optional;

/**
 * Carries a stable application error code and safe display message.
 */
public final class ApplicationException extends RuntimeException {
    /** Stable error code. */
    private final ApplicationErrorCode errorCode;
    /** Safe user-facing message. */
    private final String displayMessage;
    /** Optional write outcome. */
    private final TransactionOutcome transactionOutcome;

    /**
     * Creates an application exception without a transaction outcome.
     *
     * @param errorCode Stable error code.
     * @param displayMessage Safe user-facing message.
     * @param cause Technical cause, when available.
     */
    public ApplicationException(ApplicationErrorCode errorCode, String displayMessage,
            Throwable cause) {
        this(errorCode, displayMessage, cause, null);
    }

    /**
     * Creates an application exception with a transaction outcome.
     *
     * @param errorCode Stable error code.
     * @param displayMessage Safe user-facing message.
     * @param cause Technical cause, when available.
     * @param transactionOutcome Known write outcome, when applicable.
     */
    public ApplicationException(ApplicationErrorCode errorCode, String displayMessage,
            Throwable cause, TransactionOutcome transactionOutcome) {
        super(displayMessage, cause);
        if (errorCode == null || displayMessage == null || displayMessage.isBlank()) {
            throw new IllegalArgumentException("Application error details cannot be blank.");
        }

        this.errorCode = errorCode;
        this.displayMessage = displayMessage;
        this.transactionOutcome = transactionOutcome;
    }

    /**
     * Returns the stable error code.
     *
     * @return Error code.
     */
    public ApplicationErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Returns the safe user-facing message.
     *
     * @return Display message.
     */
    public String displayMessage() {
        return displayMessage;
    }

    /**
     * Returns the write outcome when this failure came from a transaction.
     *
     * @return Optional transaction outcome.
     */
    public Optional<TransactionOutcome> transactionOutcome() {
        return Optional.ofNullable(transactionOutcome);
    }

    /**
     * Returns a copy carrying the supplied transaction outcome.
     *
     * @param outcome Transaction outcome.
     * @return Exception with the same error details and the supplied outcome.
     */
    public ApplicationException withTransactionOutcome(TransactionOutcome outcome) {
        return new ApplicationException(errorCode, displayMessage, getCause(), outcome);
    }
}
