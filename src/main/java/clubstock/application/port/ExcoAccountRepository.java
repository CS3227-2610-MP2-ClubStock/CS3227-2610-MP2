package clubstock.application.port;

import clubstock.domain.account.ExcoAccount;

/**
 * Persists the singleton Exco account.
 */
public interface ExcoAccountRepository {

    /**
     * Returns the persisted singleton account.
     *
     * @return Persisted singleton account.
     */
    ExcoAccount get();

    /**
     * Saves the singleton account.
     *
     * @param account Account to persist.
     */
    void save(ExcoAccount account);
}
