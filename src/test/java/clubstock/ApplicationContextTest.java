package clubstock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ui.auth.AuthenticatedPrincipal;
import clubstock.ui.auth.AuthenticationGateway;

class ApplicationContextTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReopensAnIsolatedApplicationDatabase() {
        AuthenticationGateway authentication = new StubAuthenticationGateway();

        ApplicationContext first = ApplicationContext.create(temporaryDirectory, authentication);
        ApplicationContext second = ApplicationContext.create(temporaryDirectory, authentication);

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), first.dataDirectory());
        assertEquals(first.dataDirectory(), second.dataDirectory());
        assertEquals(authentication, first.authentication());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("clubstock.db")));
    }

    private static final class StubAuthenticationGateway implements AuthenticationGateway {
        @Override
        public boolean isExcoSetupRequired() {
            return true;
        }

        @Override
        public void completeExcoSetup(char[] password, char[] confirmation) {
        }

        @Override
        public void authenticateExco(char[] password) {
        }

        @Override
        public Optional<AuthenticatedPrincipal> currentPrincipal() {
            return Optional.empty();
        }

        @Override
        public void logout() {
        }
    }
}
