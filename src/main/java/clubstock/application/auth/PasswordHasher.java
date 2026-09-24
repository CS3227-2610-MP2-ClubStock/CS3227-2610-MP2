package clubstock.application.auth;

import clubstock.domain.account.PasswordHash;

/**
 * Creates and verifies encoded account password hashes.
 */
public interface PasswordHasher {

    /**
     * Hashes a password after validating the account password policy.
     *
     * @param password Plaintext password characters, cleared before this call returns.
     * @return Encoded salted password hash.
     */
    PasswordHash hash(char[] password);

    /**
     * Verifies a password against a stored hash.
     *
     * @param password Plaintext password characters, cleared before this call returns.
     * @param passwordHash Stored password hash.
     * @return True when the password matches.
     */
    boolean matches(char[] password, PasswordHash passwordHash);
}
