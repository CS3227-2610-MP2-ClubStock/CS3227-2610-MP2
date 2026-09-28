package clubstock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.function.Function;

import clubstock.ui.navigation.Route;

/**
 * Owns disposable storage and backend/resource checks for installation verification.
 */
final class InstallVerifier implements AutoCloseable {
    private final Path directory;
    private final Function<String, URL> resources;

    InstallVerifier() throws IOException {
        this(InstallVerifier.class::getResource);
    }

    InstallVerifier(Function<String, URL> resources) throws IOException {
        this.resources = resources;
        directory = Files.createTempDirectory("clubstock-verify-");
    }

    Path directory() {
        return directory;
    }

    /**
     * Verifies a real committed write survives rebuilding the application context.
     */
    ApplicationContext verifyPersistence() {
        ApplicationContext first = ApplicationContext.create(directory);
        if (!first.authentication().isExcoSetupRequired()) {
            throw new IllegalStateException("Fresh verification database already has an Exco credential.");
        }
        char[] password = "installation-verification-only".toCharArray();
        char[] confirmation = password.clone();
        try {
            first.authentication().completeExcoSetup(password, confirmation);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            first.authentication().logout();
        }
        ApplicationContext reopened = ApplicationContext.create(directory);
        if (reopened.authentication().isExcoSetupRequired()) {
            throw new IllegalStateException("Exco setup was not persisted across context recreation.");
        }
        return reopened;
    }

    /**
     * Reads all routed FXML and the shared stylesheet from the runtime classpath.
     */
    void verifyResources() throws IOException {
        for (Route route : Route.values()) {
            readResource(route.resourcePath());
        }
        readResource("/clubstock/ui/clubstock.css");
    }

    /**
     * Reports the specific resource when resolution or reading fails.
     */
    private void readResource(String path) throws IOException {
        URL resource = resources.apply(path);
        if (resource == null) {
            throw new IOException("Missing required resource: " + path);
        }
        try (InputStream stream = resource.openStream()) {
            stream.transferTo(OutputStream.nullOutputStream());
        } catch (IOException exception) {
            throw new IOException("Cannot read required resource: " + path, exception);
        }
    }

    @Override
    public void close() throws IOException {
        // walkFileTree does not follow symbolic links by default.
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
