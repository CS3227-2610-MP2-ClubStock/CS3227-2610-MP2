package clubstock.infrastructure.id;

import java.util.UUID;

import clubstock.application.port.IdGenerator;

/**
 * Generates UUID version 4 identifiers.
 */
public final class UuidIdGenerator implements IdGenerator {

    /**
     * Creates a UUID generator.
     */
    public UuidIdGenerator() {
    }

    /**
     * Returns a random UUID version 4 string.
     *
     * @return Canonical UUID string.
     */
    @Override
    public String generateId() {
        return UUID.randomUUID().toString();
    }
}
