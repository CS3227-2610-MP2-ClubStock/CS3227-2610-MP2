package clubstock.application.auth;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.domain.account.PasswordHash;

/**
 * Hashes passwords with salted PBKDF2-HMAC-SHA256.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {
    private static final String HASH_PREFIX = "pbkdf2-sha256";
    private static final String SECRET_KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private final SecureRandom secureRandom;

    /**
     * Creates a hasher with the system secure random source.
     */
    public Pbkdf2PasswordHasher() {
        this(new SecureRandom());
    }

    /**
     * Creates a hasher with an injectable secure random source.
     *
     * @param secureRandom Secure random source.
     */
    public Pbkdf2PasswordHasher(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new IllegalArgumentException("Secure random source cannot be null.");
        }
        this.secureRandom = secureRandom;
    }

    @Override
    public PasswordHash hash(char[] password) {
        requirePasswordArray(password);
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        byte[] derivedHash = null;
        try {
            validatePasswordPolicy(password);
            secureRandom.nextBytes(salt);
            derivedHash = derive(password, salt, ITERATIONS);
            String encoded = HASH_PREFIX + "$" + ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derivedHash);
            return new PasswordHash(encoded);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(salt, (byte) 0);
            clear(derivedHash);
        }
    }

    @Override
    public boolean matches(char[] password, PasswordHash passwordHash) {
        requirePasswordArray(password);
        if (passwordHash == null) {
            Arrays.fill(password, '\0');
            throw new IllegalArgumentException("Stored password hash cannot be null.");
        }

        byte[] salt = null;
        byte[] expectedHash = null;
        byte[] actualHash = null;
        try {
            StoredHash storedHash = parse(passwordHash);
            salt = storedHash.salt();
            expectedHash = storedHash.hash();
            actualHash = derive(password, salt, storedHash.iterations());
            return MessageDigest.isEqual(expectedHash, actualHash);
        } finally {
            Arrays.fill(password, '\0');
            clear(salt);
            clear(expectedHash);
            clear(actualHash);
        }
    }

    /**
     * Validates the documented minimum length and non-whitespace requirement.
     *
     * @param password Plaintext password characters.
     */
    private static void validatePasswordPolicy(char[] password) {
        int codePointCount = Character.codePointCount(password, 0, password.length);
        boolean hasNonWhitespace = false;
        for (int index = 0; index < password.length;) {
            int codePoint = Character.codePointAt(password, index, password.length);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                hasNonWhitespace = true;
                break;
            }
            index += Character.charCount(codePoint);
        }

        if (codePointCount < 8 || !hasNonWhitespace) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED,
                    "Password must contain at least eight Unicode code points and a non-whitespace character.",
                    null);
        }
    }

    /**
     * Derives a password hash and clears the cryptographic key specification.
     *
     * @param password Plaintext password characters.
     * @param salt Random salt.
     * @param iterations PBKDF2 iteration count.
     * @return Derived key bytes.
     */
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, HASH_LENGTH_BYTES * 8);
        try {
            return SecretKeyFactory.getInstance(SECRET_KEY_ALGORITHM)
                    .generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "Password credentials could not be processed.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    /**
     * Parses the retained password encoding and rejects malformed stored data.
     *
     * @param passwordHash Stored password hash.
     * @return Parsed salt, iteration count, and hash.
     */
    private static StoredHash parse(PasswordHash passwordHash) {
        String[] parts = passwordHash.encodedHash().split("\\$", -1);
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            throw malformedHash();
        }

        byte[] salt = null;
        byte[] hash = null;
        try {
            int iterations = Integer.parseInt(parts[1]);
            salt = Base64.getDecoder().decode(parts[2]);
            hash = Base64.getDecoder().decode(parts[3]);
            if (iterations != ITERATIONS || salt.length != SALT_LENGTH_BYTES
                    || hash.length != HASH_LENGTH_BYTES) {
                throw malformedHash();
            }
            StoredHash storedHash = new StoredHash(iterations, salt, hash);
            salt = null;
            hash = null;
            return storedHash;
        } catch (IllegalArgumentException exception) {
            throw malformedHash(exception);
        } finally {
            clear(salt);
            clear(hash);
        }
    }

    /**
     * Creates the safe failure for an invalid encoded password hash.
     *
     * @return Persistence failure.
     */
    private static ApplicationException malformedHash() {
        return malformedHash(null);
    }

    /**
     * Creates the safe failure for an invalid encoded password hash.
     *
     * @param cause Parsing failure, when available.
     * @return Persistence failure.
     */
    private static ApplicationException malformedHash(Throwable cause) {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                "Stored account credentials could not be read.", cause);
    }

    /**
     * Validates that a password array is present.
     *
     * @param password Password characters.
     */
    private static void requirePasswordArray(char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
    }

    /**
     * Clears a byte array when present.
     *
     * @param bytes Byte array.
     */
    private static void clear(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /**
     * Holds the parsed components of one stored hash.
     *
     * @param iterations PBKDF2 iteration count.
     * @param salt Encoded salt bytes.
     * @param hash Encoded hash bytes.
     */
    private record StoredHash(int iterations, byte[] salt, byte[] hash) {
    }
}
