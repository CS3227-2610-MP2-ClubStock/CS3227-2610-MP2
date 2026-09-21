package clubstock.domain.account;

/**
 * Carries an opaque encoded password hash without performing password hashing.
 *
 * @param encodedHash Nonblank encoded password hash.
 */
public record PasswordHash(String encodedHash) {

    /**
     * Creates an opaque password hash value.
     *
     * @param encodedHash Encoded password hash to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public PasswordHash {
        encodedHash = AccountValidation.requireNonBlank(encodedHash, "Password hash");
    }
}
