package clubstock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallVerifierTest {
    @TempDir
    Path unrelated;

    @Test
    void verifyPersistence_reopensCommittedStateAndLeavesConfiguredStorageUntouched() throws Exception {
        Path sentinel = Files.writeString(unrelated.resolve("sentinel"), "unchanged");
        String previous = System.getProperty("clubstock.dataDir");
        Path directory;
        try {
            System.setProperty("clubstock.dataDir", unrelated.toString());
            try (InstallVerifier verifier = new InstallVerifier()) {
                directory = verifier.directory();
                ApplicationContext context = verifier.verifyPersistence();
                assertFalse(context.authentication().isExcoSetupRequired());
                assertTrue(context.authentication().currentPrincipal().isEmpty());
                assertTrue(Files.isRegularFile(directory.resolve("clubstock.db")));
                verifier.verifyResources();
            }
        } finally {
            if (previous == null) {
                System.clearProperty("clubstock.dataDir");
            } else {
                System.setProperty("clubstock.dataDir", previous);
            }
        }
        assertFalse(Files.exists(directory));
        assertEquals("unchanged", Files.readString(sentinel));
        assertFalse(Files.exists(unrelated.resolve("clubstock.db")));
    }

    @Test
    void verifyResources_missingStylesheetReportsPathAndCleansStorage() throws Exception {
        Path directory;
        try (InstallVerifier verifier = new InstallVerifier(path -> path.endsWith(".css")
                ? null : InstallVerifier.class.getResource(path))) {
            directory = verifier.directory();
            IOException failure = assertThrows(IOException.class, verifier::verifyResources);
            assertTrue(failure.getMessage().contains("/clubstock/ui/clubstock.css"));
        }
        assertFalse(Files.exists(directory));
    }

    @Test
    void verifyResources_unreadableResourceReportsPath() throws Exception {
        try (InstallVerifier verifier = new InstallVerifier(path -> {
            try {
                return unrelated.resolve("missing.fxml").toUri().toURL();
            } catch (MalformedURLException exception) {
                throw new AssertionError(exception);
            }
        })) {
            IOException failure = assertThrows(IOException.class, verifier::verifyResources);
            assertTrue(failure.getMessage().contains("/clubstock/ui/view/role-selection.fxml"));
        }
    }

    @Test
    void close_doesNotFollowSymbolicLinks() throws Exception {
        Path sentinel = Files.writeString(unrelated.resolve("sentinel"), "unchanged");
        try (InstallVerifier verifier = new InstallVerifier()) {
            try {
                Files.createSymbolicLink(verifier.directory().resolve("external"), unrelated);
            } catch (IOException | UnsupportedOperationException exception) {
                Assumptions.abort("Symbolic links unavailable: " + exception);
            }
        }
        assertEquals("unchanged", Files.readString(sentinel));
    }
}
