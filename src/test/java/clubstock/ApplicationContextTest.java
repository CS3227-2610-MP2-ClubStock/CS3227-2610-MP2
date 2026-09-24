package clubstock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.auth.AccountRole;
import clubstock.ui.auth.UserRole;

class ApplicationContextTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReopensAnIsolatedApplicationDatabase() {
        ApplicationContext first = ApplicationContext.create(temporaryDirectory);
        ApplicationContext second = ApplicationContext.create(temporaryDirectory);

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), first.dataDirectory());
        assertEquals(first.dataDirectory(), second.dataDirectory());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("clubstock.db")));

        char[] password = "initial-exco-password".toCharArray();
        char[] confirmation = "initial-exco-password".toCharArray();
        first.authentication().completeExcoSetup(password, confirmation);

        assertEquals(UserRole.EXCO,
                first.authentication().currentPrincipal().orElseThrow().role());
        assertEquals(AccountRole.EXCO, first.sessionManager().requireExco().role());
        assertNotNull(first.transactionManager());
        assertNotNull(first.availabilityPolicy());
        assertNotNull(first.inventoryService());
        assertNotNull(first.memberCatalogService());
        assertFalse(second.authentication().isExcoSetupRequired());
        assertTrue(second.sessionManager().currentPrincipal().isEmpty());
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }
}
