package clubstock;

import clubstock.ui.UiComposition;
import clubstock.ui.navigation.JavaFxNavigator;
import clubstock.ui.navigation.Route;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Exercises packaged startup without opening interactive error dialogs or production storage.
 */
public final class InstallVerificationApplication extends Application {
    private static volatile int exitCode = 1;

    /**
     * Creates the verification application for the JavaFX launcher.
     */
    public InstallVerificationApplication() {
    }

    static int exitCode() {
        return exitCode;
    }

    @Override
    public void start(Stage stage) {
        String phase = "temporary storage";
        InstallVerifier verifier = null;
        try {
            verifier = new InstallVerifier();
            System.out.println("Verification storage: " + verifier.directory());
            phase = "SQLite persistence";
            System.out.println("Checking " + phase);
            ApplicationContext context = verifier.verifyPersistence();
            phase = "packaged resources";
            System.out.println("Checking " + phase);
            verifier.verifyResources();
            phase = "JavaFX startup rendering";
            System.out.println("Checking " + phase);
            JavaFxNavigator navigator = UiComposition.createNavigator(stage, context.authentication(),
                    context.memberAccountService(), context.inventoryService(), context.memberCatalogService(),
                    context.excoRequestService(), context.memberRequestService(), context.approvalService(),
                    context.loanQueryService(), context.memberLoanService(), context.verificationService(),
                    (title, message) -> {
                        throw new IllegalStateException(title + ": " + message);
                    });
            navigator.show(Route.ROLE_SELECTION);
            stage.show();
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            stage.getScene().snapshot(null);
            exitCode = 0;
        } catch (Exception | LinkageError exception) {
            System.err.println("Installation verification failed during " + phase);
            exception.printStackTrace(System.err);
        } finally {
            try {
                stage.close();
            } finally {
                if (verifier != null) {
                    try {
                        verifier.close();
                    } catch (Exception exception) {
                        exitCode = 1;
                        System.err.println("Verification cleanup failed; storage remains at " + verifier.directory());
                        exception.printStackTrace(System.err);
                    }
                }
                Platform.exit();
            }
        }
    }
}
