package clubstock.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.EquipmentItemAvailabilityPolicy;
import clubstock.application.inventory.InventoryService;
import clubstock.application.member.MemberAccountService;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWorkOperation;
import clubstock.domain.account.PasswordHash;
import clubstock.infrastructure.sqlite.SqliteDatabase;
import clubstock.ui.JavaFxTestSupport;
import clubstock.ui.navigation.NavigationService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;

/**
 * Exercises the administration status boundary when a command commits but its follow-up query
 * cannot produce a current table snapshot.
 */
@Tag("display")
class AdministrationRefreshFailureControllerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T00:00:00Z"),
            ZoneOffset.UTC);

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        JavaFxTestSupport.start();
    }

    @Test
    void memberChangeReportsAStaleListAfterTheCommittedWrite(@TempDir Path temporaryDirectory)
            throws Exception {
        runOnJavaFxThread(() -> {
            SqliteDatabase database = initializedDatabase(temporaryDirectory);
            SessionManager sessionManager = signedInExco(database);
            TransactionManager transactions = failReadNumber(database, 2);
            MemberAccountService service = new MemberAccountService(transactions, sessionManager,
                    new TestPasswordHasher(), CLOCK);
            service.createMember("member-01", "Before", "member-password".toCharArray());
            MemberAdministrationController controller = new MemberAdministrationController(service,
                    unusedNavigation());
            loadMemberAdministrationView(controller);

            Label feedback = new Label();
            assertTrue(invokeExecute(controller,
                    () -> service.renameMember("member-01", "After"), "Member name updated.",
                    feedback));

            assertEquals("After", database.read(unit -> unit.members()
                    .findById(new clubstock.domain.account.MemberId("member-01"))
                    .orElseThrow().name()));
            assertStaleRefreshMessage(statusLabel(controller), feedback,
                    "The Member account change was saved");
        });
    }

    @Test
    void inventoryChangeReportsAStaleListAfterTheCommittedWrite(@TempDir Path temporaryDirectory)
            throws Exception {
        runOnJavaFxThread(() -> {
            SqliteDatabase database = initializedDatabase(temporaryDirectory);
            SessionManager sessionManager = signedInExco(database);
            TransactionManager transactions = failReadNumber(database, 3);
            InventoryService service = new InventoryService(transactions, sessionManager,
                    () -> "type-after", new EquipmentItemAvailabilityPolicy(), CLOCK);
            InventoryAdministrationController controller = new InventoryAdministrationController(service,
                    unusedNavigation());
            loadInventoryAdministrationView(controller);

            Label feedback = new Label();
            assertTrue(invokeExecute(controller, () -> service.createType("After"),
                    "Equipment type created as unoffered.", feedback));

            assertEquals("After", database.read(unit -> unit.equipmentTypes().findAll().getFirst()
                    .name().value()));
            assertStaleRefreshMessage(statusLabel(controller), feedback,
                    "The inventory change was saved");
        });
    }

    private static void loadMemberAdministrationView(MemberAdministrationController controller)
            throws Exception {
        FXMLLoader loader = new FXMLLoader(AdministrationRefreshFailureControllerTest.class
                .getResource("/clubstock/ui/view/member-administration.fxml"));
        loader.setControllerFactory(type -> type == MemberAdministrationController.class
                ? controller : unexpectedController(type));
        loader.load();
    }

    private static void loadInventoryAdministrationView(InventoryAdministrationController controller)
            throws Exception {
        FXMLLoader loader = new FXMLLoader(AdministrationRefreshFailureControllerTest.class
                .getResource("/clubstock/ui/view/inventory-administration.fxml"));
        loader.setControllerFactory(type -> type == InventoryAdministrationController.class
                ? controller : unexpectedController(type));
        loader.load();
    }

    private static Object unexpectedController(Class<?> type) {
        throw new IllegalArgumentException("Unexpected FXML controller: " + type.getName());
    }

    private static boolean invokeExecute(Object controller, Runnable operation, String successMessage,
            Label feedback) throws Exception {
        Method execute = controller.getClass().getDeclaredMethod("execute", Runnable.class,
                String.class, Label.class);
        execute.setAccessible(true);
        return (boolean) execute.invoke(controller, operation, successMessage, feedback);
    }

    private static Label statusLabel(Object controller) throws Exception {
        Field statusLabel = controller.getClass().getDeclaredField("statusLabel");
        statusLabel.setAccessible(true);
        return (Label) statusLabel.get(controller);
    }

    private static void assertStaleRefreshMessage(Label status, Label feedback,
            String expectedPrefix) {
        assertTrue(status.getText().startsWith(expectedPrefix));
        assertTrue(status.getText().contains("Use Refresh"));
        assertEquals(status.getText(), feedback.getText());
        assertFalse(status.getText().contains("updated."));
        assertTrue(status.getStyleClass().contains("error-text"));
        assertFalse(status.getStyleClass().contains("success-text"));
    }

    private static void runOnJavaFxThread(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Exception exception) {
                failure.set(exception);
            } catch (AssertionError error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(20, TimeUnit.SECONDS), "JavaFX test did not complete.");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX controller test failed.", failure.get());
        }
    }

    private static SqliteDatabase initializedDatabase(Path temporaryDirectory) {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clubstock.db"));
        database.initialize();
        return database;
    }

    private static SessionManager signedInExco(SqliteDatabase database) {
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        authentication.completeExcoSetup("exco-password".toCharArray(),
                "exco-password".toCharArray());
        return sessionManager;
    }

    private static NavigationService unusedNavigation() {
        return route -> {
            // Navigation is not part of the status-boundary scenario.
        };
    }

    private static TransactionManager failReadNumber(SqliteDatabase delegate, int failingRead) {
        AtomicInteger reads = new AtomicInteger();
        return new TransactionManager() {
            @Override
            public <T> T read(UnitOfWorkOperation<T> operation) {
                if (reads.incrementAndGet() == failingRead) {
                    throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                            "The test reload failed.", null);
                }
                return delegate.read(operation);
            }

            @Override
            public <T> T write(UnitOfWorkOperation<T> operation) {
                return delegate.write(operation);
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] password) {
            try {
                return new PasswordHash("test$" + new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }

        @Override
        public boolean matches(char[] password, PasswordHash passwordHash) {
            try {
                return passwordHash.encodedHash().equals("test$" + new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }
}
