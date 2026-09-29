package clubstock.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.ui.auth.UserRole;
import clubstock.ui.navigation.JavaFxNavigator;
import clubstock.ui.navigation.Route;
import javafx.application.Platform;
import javafx.stage.Stage;

/** A display-backed real-composition check intended for CI runners with a virtual display. */
@Tag("display")
class JavaFxPackagedSmokeTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void startJavaFxToolkit() throws InterruptedException {
        JavaFxTestSupport.start();
    }

    @Test
    void opensEveryRouteWithItsAuthorizedRealComposition() throws Exception {
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
                showAndAssertTitle(navigator, stage, Route.ROLE_SELECTION);
                stage.show();
                showAndAssertTitle(navigator, stage, Route.EXCO_SETUP);
                showAndAssertTitle(navigator, stage, Route.EXCO_LOGIN);
                showAndAssertTitle(navigator, stage, Route.MEMBER_LOGIN);

                context.authentication().completeExcoSetup("smoke-exco-password".toCharArray(),
                        "smoke-exco-password".toCharArray());
                context.memberAccountService().createMember("smoke-member", "Smoke Member",
                        "smoke-member-password".toCharArray());
                for (Route route : Route.values()) {
                    if (route.requiredRole().filter(role -> role == UserRole.EXCO).isPresent()) {
                        showAndAssertTitle(navigator, stage, route);
                    }
                }

                context.authentication().logout();
                context.authentication().authenticateMember("smoke-member",
                        "smoke-member-password".toCharArray());
                for (Route route : Route.values()) {
                    if (route.requiredRole().filter(role -> role == UserRole.MEMBER).isPresent()) {
                        showAndAssertTitle(navigator, stage, route);
                    }
                }
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
        assertEquals("ClubStock — My loans", observedState.title());
        assertTrue(observedState.showing());
    }

    private static void showAndAssertTitle(JavaFxNavigator navigator, Stage stage, Route route) {
        navigator.show(route);
        assertNotNull(stage.getScene(), route.name());
        assertEquals("ClubStock — " + route.windowTitle(), stage.getTitle(), route.name());
    }

    private record StageState(boolean hasScene, String title, boolean showing) {
    }
}
