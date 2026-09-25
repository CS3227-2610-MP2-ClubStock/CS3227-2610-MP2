package clubstock;

import clubstock.ui.UiComposition;
import clubstock.ui.navigation.JavaFxNavigator;
import clubstock.ui.navigation.Route;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

/**
 * Owns ClubStock's JavaFX lifecycle and one application context.
 */
public final class ClubStockApplication extends Application {
    private static final String STARTUP_ERROR =
            "ClubStock could not start. Check that its data location is available and try again.";
    private ApplicationContext applicationContext;

    /**
     * Creates the JavaFX application instance.
     */
    public ClubStockApplication() {
    }

    @Override
    public void start(Stage stage) {
        try {
            applicationContext = ApplicationContext.createProduction();
            JavaFxNavigator navigator = UiComposition.createNavigator(stage,
                    applicationContext.authentication(), applicationContext.memberAccountService(),
                    applicationContext.inventoryService(), applicationContext.memberCatalogService(),
                    applicationContext.excoRequestService(),
                    applicationContext.memberRequestService(),
                    applicationContext.approvalService(),
                    applicationContext.loanQueryService(), applicationContext.verificationService());
            navigator.show(Route.ROLE_SELECTION);
            stage.show();
        } catch (RuntimeException exception) {
            showStartupFailure();
        }
    }

    private void showStartupFailure() {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("ClubStock startup error");
        alert.setHeaderText("ClubStock could not start");
        alert.setContentText(STARTUP_ERROR);
        alert.showAndWait();
        Platform.exit();
    }
}
