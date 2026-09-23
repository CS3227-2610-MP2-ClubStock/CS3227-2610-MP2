package clubstock.application.port;

/**
 * Executes repository operations with explicit read and write boundaries.
 */
public interface TransactionManager {

    /**
     * Executes one consistent read operation.
     *
     * @param operation Read callback.
     * @param <T> Result type.
     * @return Callback result.
     */
    <T> T read(UnitOfWorkOperation<T> operation);

    /**
     * Executes one atomic write operation.
     * Failures distinguish confirmed rollback, unknown commit outcome, and a successful commit
     * followed by a cleanup failure. Only confirmed rollback permits rollback compensation
     * without independently checking committed references.
     *
     * @param operation Write callback.
     * @param <T> Result type.
     * @return Callback result after commit.
     */
    <T> T write(UnitOfWorkOperation<T> operation);
}
