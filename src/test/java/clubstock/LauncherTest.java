package clubstock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defersNormalArgumentsToJavaFx() {
        assertEquals(Launcher.VERIFICATION_NOT_REQUESTED,
                Launcher.verifyInstall(new String[0], null));
        assertEquals(Launcher.VERIFICATION_NOT_REQUESTED,
                Launcher.verifyInstall(new String[] {"--some-javafx-option"}, null));
    }

    @Test
    void runsVerificationForTheExactStandaloneArgument() {
        assertEquals(PackagedInstallVerifier.SUCCESS,
                Launcher.verifyInstall(new String[] {"--verify-install"},
                        new PackagedInstallVerifier(new NoOpOperations(temporaryDirectory))));
    }

    @Test
    void rejectsVerificationArgumentsWithAdditionalValues() {
        assertEquals(PackagedInstallVerifier.FAILURE,
                Launcher.verifyInstall(new String[] {"--verify-install", "unexpected"}, null));
    }

    private static final class NoOpOperations implements PackagedInstallVerifier.Operations {
        private final Path parentDirectory;

        private NoOpOperations(Path parentDirectory) {
            this.parentDirectory = parentDirectory;
        }

        @Override
        public Path createTemporaryDirectory() throws IOException {
            return Files.createTempDirectory(parentDirectory, "launcher-test-");
        }

        @Override
        public void verifySqliteDriver() {
        }

        @Override
        public void verifyMigrationResource() {
        }

        @Override
        public void verifyUiResources() {
        }

        @Override
        public void initializePersistence(Path dataDirectory) {
        }

        @Override
        public void deleteTemporaryDirectory(Path dataDirectory) throws IOException {
            Files.delete(dataDirectory);
        }
    }
}
