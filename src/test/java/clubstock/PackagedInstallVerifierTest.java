package clubstock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedInstallVerifierTest {
    private static final String DATA_DIRECTORY_PROPERTY = "clubstock.dataDir";

    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesTheActualPackagedDependenciesWithoutUsingConfiguredDataDirectory() {
        Path normalDataDirectory = temporaryDirectory.resolve("normal-data");
        String originalDataDirectory = System.getProperty(DATA_DIRECTORY_PROPERTY);
        System.setProperty(DATA_DIRECTORY_PROPERTY, normalDataDirectory.toString());
        try {
            assertEquals(PackagedInstallVerifier.SUCCESS,
                    new PackagedInstallVerifier().verify());
        } finally {
            restoreDataDirectory(originalDataDirectory);
        }

        assertFalse(Files.exists(normalDataDirectory));
    }

    @Test
    void initializesAndReopensTheSameTemporaryDatabase() {
        RecordingOperations operations = new RecordingOperations(temporaryDirectory, true);

        assertEquals(PackagedInstallVerifier.SUCCESS,
                new PackagedInstallVerifier(operations).verify());

        assertEquals(2, operations.persistenceInitializations);
        assertFalse(Files.exists(operations.createdDirectory));
    }

    @Test
    void returnsNonzeroWhenTheSqliteDriverIsUnavailable() {
        RecordingOperations operations = new RecordingOperations(temporaryDirectory, false);
        operations.driverFailure = new IllegalStateException("driver unavailable");

        assertEquals(PackagedInstallVerifier.FAILURE,
                new PackagedInstallVerifier(operations).verify());
        assertEquals(0, operations.persistenceInitializations);
        assertFalse(operations.migrationChecked);
    }

    @Test
    void returnsNonzeroWhenTheMigrationResourceIsUnavailable() {
        RecordingOperations operations = new RecordingOperations(temporaryDirectory, false);
        operations.migrationFailure = new IllegalStateException("migration unavailable");

        assertEquals(PackagedInstallVerifier.FAILURE,
                new PackagedInstallVerifier(operations).verify());
        assertEquals(0, operations.persistenceInitializations);
        assertTrue(operations.migrationChecked);
        assertFalse(operations.uiResourcesChecked);
    }

    @Test
    void returnsNonzeroWhenARequiredUiResourceIsUnavailable() {
        RecordingOperations operations = new RecordingOperations(temporaryDirectory, false);
        operations.uiResourceFailure = new IllegalStateException("resource unavailable");

        assertEquals(PackagedInstallVerifier.FAILURE,
                new PackagedInstallVerifier(operations).verify());
        assertEquals(0, operations.persistenceInitializations);
        assertTrue(operations.uiResourcesChecked);
    }

    private static void restoreDataDirectory(String originalDataDirectory) {
        if (originalDataDirectory == null) {
            System.clearProperty(DATA_DIRECTORY_PROPERTY);
        } else {
            System.setProperty(DATA_DIRECTORY_PROPERTY, originalDataDirectory);
        }
    }

    private static final class RecordingOperations implements PackagedInstallVerifier.Operations {
        private final Path parentDirectory;
        private final boolean useRealPersistence;
        private Path createdDirectory;
        private int persistenceInitializations;
        private boolean migrationChecked;
        private boolean uiResourcesChecked;
        private Exception driverFailure;
        private Exception migrationFailure;
        private Exception uiResourceFailure;

        private RecordingOperations(Path parentDirectory, boolean useRealPersistence) {
            this.parentDirectory = parentDirectory;
            this.useRealPersistence = useRealPersistence;
        }

        @Override
        public Path createTemporaryDirectory() throws IOException {
            createdDirectory = Files.createTempDirectory(parentDirectory, "packaged-verifier-test-");
            return createdDirectory;
        }

        @Override
        public void verifySqliteDriver() throws Exception {
            if (driverFailure != null) {
                throw driverFailure;
            }
        }

        @Override
        public void verifyMigrationResource() throws Exception {
            migrationChecked = true;
            if (migrationFailure != null) {
                throw migrationFailure;
            }
        }

        @Override
        public void verifyUiResources() throws Exception {
            uiResourcesChecked = true;
            if (uiResourceFailure != null) {
                throw uiResourceFailure;
            }
        }

        @Override
        public void initializePersistence(Path dataDirectory) {
            persistenceInitializations++;
            if (useRealPersistence) {
                ApplicationContext.create(dataDirectory);
            }
        }

        @Override
        public void deleteTemporaryDirectory(Path dataDirectory) throws IOException {
            try (var paths = Files.walk(dataDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(RecordingOperations::delete);
            }
        }

        private static void delete(Path path) {
            try {
                Files.delete(path);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
