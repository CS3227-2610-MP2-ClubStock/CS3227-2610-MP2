package clubstock.domain.account;

import java.util.Optional;

/**
 * Represents the singleton Exco account and its first-run credential setup state.
 */
public final class ExcoAccount {
    private PasswordHash passwordHash;

    private ExcoAccount() {
    }

    /**
     * Creates an Exco account that still requires initial password setup.
     *
     * @return A first-run Exco account.
     */
    public static ExcoAccount createForFirstRun() {
        return new ExcoAccount();
    }

    /**
     * Returns whether this account still requires its initial password setup.
     *
     * @return True when no credential has been established.
     */
    public boolean requiresPasswordSetup() {
        return passwordHash == null;
    }

    /**
     * Returns the established password hash when setup has completed.
     *
     * @return Optional current password hash.
     */
    public Optional<PasswordHash> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    /**
     * Completes initial password setup exactly once.
     *
     * @param passwordHash Initial opaque password hash.
     * @throws IllegalStateException If initial setup has already completed.
     * @throws IllegalArgumentException If the password hash is null.
     */
    public void completeInitialPasswordSetup(PasswordHash passwordHash)
            throws IllegalStateException, IllegalArgumentException {
        if (!requiresPasswordSetup()) {
            throw new IllegalStateException("Exco password setup has already completed.");
        }

        if (passwordHash == null) {
            throw new IllegalArgumentException("Password hash cannot be null.");
        }

        this.passwordHash = passwordHash;
    }
}
