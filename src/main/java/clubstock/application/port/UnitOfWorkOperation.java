package clubstock.application.port;

/**
 * Executes one operation inside a repository unit of work.
 *
 * @param <T> Result type.
 */
@FunctionalInterface
public interface UnitOfWorkOperation<T> {

    /**
     * Executes the operation against the supplied unit of work.
     *
     * @param unitOfWork Active repository unit of work.
     * @return Operation result.
     */
    T execute(UnitOfWork unitOfWork);
}
