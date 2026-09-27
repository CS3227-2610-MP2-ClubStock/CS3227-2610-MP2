package clubstock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import clubstock.ui.navigation.Route;

/**
 * Verifies that the packaged application can start its required non-visual dependencies.
 */
final class PackagedInstallVerifier {
    static final int SUCCESS = 0;
    static final int FAILURE = 1;
    private static final String MIGRATION_RESOURCE =
            "/clubstock/infrastructure/sqlite/migration/V001__initial_schema.sql";
    private static final String STYLESHEET_RESOURCE = "/clubstock/ui/clubstock.css";
    private final Operations operations;

    PackagedInstallVerifier() {
        this(new ProductionOperations());
    }

    PackagedInstallVerifier(Operations operations) {
        if (operations == null) {
            throw new IllegalArgumentException("Verification operations cannot be null.");
        }
        this.operations = operations;
    }

    /**
     * Runs the noninteractive packaged-install checks.
     *
     * @return Zero when all checks pass; otherwise a nonzero process exit status.
     */
    int verify() {
        Path temporaryDirectory = null;
        int result = FAILURE;
        try {
            temporaryDirectory = operations.createTemporaryDirectory();
            operations.verifySqliteDriver();
            operations.verifyMigrationResource();
            operations.verifyUiResources();
            operations.initializePersistence(temporaryDirectory);
            operations.initializePersistence(temporaryDirectory);
            result = SUCCESS;
        } catch (Exception exception) {
            System.err.println("ClubStock installation verification failed: "
                    + safeMessage(exception));
        } finally {
            if (temporaryDirectory != null) {
                try {
                    operations.deleteTemporaryDirectory(temporaryDirectory);
                } catch (Exception exception) {
                    System.err.println("ClubStock installation verification cleanup failed: "
                            + safeMessage(exception));
                    result = FAILURE;
                }
            }
        }
        return result;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    interface Operations {
        Path createTemporaryDirectory() throws Exception;

        void verifySqliteDriver() throws Exception;

        void verifyMigrationResource() throws Exception;

        void verifyUiResources() throws Exception;

        void initializePersistence(Path dataDirectory) throws Exception;

        void deleteTemporaryDirectory(Path dataDirectory) throws Exception;
    }

    private static final class ProductionOperations implements Operations {
        @Override
        public Path createTemporaryDirectory() throws IOException {
            return Files.createTempDirectory("clubstock-verify-install-");
        }

        @Override
        public void verifySqliteDriver() throws ClassNotFoundException {
            Class.forName("org.sqlite.JDBC");
        }

        @Override
        public void verifyMigrationResource() throws IOException {
            requireResource(MIGRATION_RESOURCE);
        }

        @Override
        public void verifyUiResources() throws IOException {
            requireResource(STYLESHEET_RESOURCE);
            for (Route route : Route.values()) {
                requireResource(route.resourcePath());
            }
        }

        @Override
        public void initializePersistence(Path dataDirectory) {
            ApplicationContext.create(dataDirectory);
        }

        @Override
        public void deleteTemporaryDirectory(Path dataDirectory) throws IOException {
            try (var paths = Files.walk(dataDirectory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(PackagedInstallVerifier::delete);
            }
        }

        private static void requireResource(String resourcePath) throws IOException {
            try (InputStream stream = PackagedInstallVerifier.class.getResourceAsStream(resourcePath)) {
                if (stream == null || stream.read() == -1) {
                    throw new IOException("Required packaged resource is unavailable: " + resourcePath);
                }
            }
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Temporary verification data could not be removed.", exception);
        }
    }
}
