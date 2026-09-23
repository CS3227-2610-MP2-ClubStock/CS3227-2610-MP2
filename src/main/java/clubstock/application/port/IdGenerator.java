package clubstock.application.port;

/**
 * Generates identifiers for application-owned records.
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * Returns a newly generated identifier.
     *
     * @return Generated identifier.
     */
    String generateId();
}
