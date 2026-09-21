package clubstock.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ExcoAccountTest {

    @Test
    void createForFirstRun_newAccountRequiresSetupWithoutHash() {
        ExcoAccount account = ExcoAccount.createForFirstRun();

        assertTrue(account.requiresPasswordSetup());
        assertEquals(Optional.empty(), account.passwordHash());
    }

    @Test
    void completeInitialPasswordSetup_validHashCompletesSetupOnce() {
        ExcoAccount account = ExcoAccount.createForFirstRun();
        PasswordHash passwordHash = new PasswordHash("hash-1");

        account.completeInitialPasswordSetup(passwordHash);

        assertFalse(account.requiresPasswordSetup());
        assertEquals(Optional.of(passwordHash), account.passwordHash());
    }

    @Test
    void completeInitialPasswordSetup_nullHash_preservesFirstRunState() {
        ExcoAccount account = ExcoAccount.createForFirstRun();

        assertThrows(IllegalArgumentException.class,
                () -> account.completeInitialPasswordSetup(null));
        assertTrue(account.requiresPasswordSetup());
        assertEquals(Optional.empty(), account.passwordHash());
    }

    @Test
    void completeInitialPasswordSetup_repeatedSetup_rejectsWithoutOverwrite() {
        ExcoAccount account = ExcoAccount.createForFirstRun();
        PasswordHash originalHash = new PasswordHash("hash-1");
        PasswordHash replacementHash = new PasswordHash("hash-2");
        account.completeInitialPasswordSetup(originalHash);

        assertThrows(IllegalStateException.class,
                () -> account.completeInitialPasswordSetup(replacementHash));
        assertEquals(Optional.of(originalHash), account.passwordHash());
    }
}
