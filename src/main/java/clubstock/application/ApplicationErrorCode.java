package clubstock.application;

/**
 * Defines stable error codes exposed by application services.
 */
public enum ApplicationErrorCode {
    /** Input failed a domain or service validation rule. */
    VALIDATION_FAILED,
    /** Credentials did not authenticate. */
    AUTHENTICATION_FAILED,
    /** The current principal is not allowed to perform an operation. */
    AUTHORIZATION_DENIED,
    /** A requested record does not exist. */
    NOT_FOUND,
    /** A unique or lifecycle constraint was violated. */
    CONFLICT,
    /** No allocatable stock exists for an operation. */
    NO_AVAILABLE_STOCK,
    /** A zero-stock request needs explicit Member confirmation before submission. */
    ZERO_STOCK_CONFIRMATION_REQUIRED,
    /** Persistent storage could not be read or written safely. */
    PERSISTENCE_FAILURE,
    /** Managed evidence storage failed. */
    IMAGE_STORAGE_FAILURE
}
