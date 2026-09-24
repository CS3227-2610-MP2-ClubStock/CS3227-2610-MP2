package clubstock.application.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;

class Pbkdf2PasswordHasherTest {
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashAndMatches_useDistinctSaltsAndClearInputArrays() {
        char[] firstPassword = "  password  ".toCharArray();
        char[] secondPassword = "  password  ".toCharArray();

        var firstHash = hasher.hash(firstPassword);
        var secondHash = hasher.hash(secondPassword);

        assertNotEquals(firstHash, secondHash);
        assertArrayEquals(new char[firstPassword.length], firstPassword);
        assertArrayEquals(new char[secondPassword.length], secondPassword);

        char[] matchingPassword = "  password  ".toCharArray();
        char[] trimmedPassword = "password".toCharArray();
        assertTrue(hasher.matches(matchingPassword, firstHash));
        assertFalse(hasher.matches(trimmedPassword, firstHash));
        assertArrayEquals(new char[matchingPassword.length], matchingPassword);
        assertArrayEquals(new char[trimmedPassword.length], trimmedPassword);
    }

    @Test
    void hash_countsUnicodeCodePointsAndRejectsWhitespaceOnlyPasswords() {
        char[] eightCodePoints = "😀😀😀😀😀😀😀😀".toCharArray();
        char[] sevenCodePoints = "😀😀😀😀😀😀😀".toCharArray();
        char[] whitespaceOnly = "\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0\u00a0".toCharArray();

        hasher.hash(eightCodePoints);
        ApplicationException tooShort = assertThrows(ApplicationException.class,
                () -> hasher.hash(sevenCodePoints));
        ApplicationException blank = assertThrows(ApplicationException.class,
                () -> hasher.hash(whitespaceOnly));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, tooShort.errorCode());
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, blank.errorCode());
        assertArrayEquals(new char[sevenCodePoints.length], sevenCodePoints);
        assertArrayEquals(new char[whitespaceOnly.length], whitespaceOnly);
    }

    @Test
    void matches_reportsMalformedStoredHashAsPersistenceFailure() {
        char[] password = "long-enough".toCharArray();

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> hasher.matches(password,
                        new clubstock.domain.account.PasswordHash("not-a-pbkdf2-hash")));

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        assertTrue(failure.displayMessage().contains("Stored account credentials"));
        assertArrayEquals(new char[password.length], password);
    }
}
