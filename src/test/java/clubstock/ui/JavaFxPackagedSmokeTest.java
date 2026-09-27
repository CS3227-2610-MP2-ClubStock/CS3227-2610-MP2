package clubstock.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.ui.navigation.JavaFxNavigator;
import clubstock.ui.navigation.Route;
import javafx.application.Platform;
import javafx.stage.Stage;

/** A minimal display-backed startup check intended for CI runners with a virtual display. */
@Tag("display")
class JavaFxPackagedSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException exception) {
            Platform.runLater(ready::countDown);
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start.");
    }

    @AfterAll
    static void stopJavaFxToolkit() {
        Platform.exit();
    }

    @Test
    void opensTheRoleSelectionFxmlWithRealComposition() throws Exception {
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<StageState> stageState = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                ApplicationContext context = ApplicationContext.create(temporaryDirectory);
                JavaFxNavigator navigator = UiComposition.createNavigator(stage,
                        context.authentication(), context.memberAccountService(),
                        context.inventoryService(), context.memberCatalogService(),
                        context.excoRequestService(), context.memberRequestService(),
                        context.approvalService(), context.loanQueryService(),
                        context.memberLoanService(), context.verificationService());
                navigator.show(Route.ROLE_SELECTION);
                stage.show();

                stageState.set(new StageState(stage.getScene() != null, stage.getTitle(),
                        stage.isShowing()));
            } catch (Exception exception) {
                failure.set(exception);
            } finally {
                stage.close();
                completed.countDown();
            }
        });

        assertTrue(completed.await(20, TimeUnit.SECONDS), "JavaFX smoke test did not complete.");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX/FXML smoke test failed.", failure.get());
        }
        StageState observedState = stageState.get();
        assertNotNull(observedState, "JavaFX callback did not report stage state.");
        assertTrue(observedState.hasScene());
        assertEquals("ClubStock — Choose role", observedState.title());
        assertTrue(observedState.showing());
    }

    private record StageState(boolean hasScene, String title, boolean showing) {
    }
}
