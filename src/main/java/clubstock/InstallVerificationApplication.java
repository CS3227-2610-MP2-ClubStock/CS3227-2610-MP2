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
            phase = "screen loading";
            System.out.println("Checking " + phase);
            navigator.show(Route.ROLE_SELECTION);
            System.out.println("Completed " + phase);
            phase = "stage show";
            System.out.println("Checking " + phase);
            stage.show();
            System.out.println("Completed " + phase);
            phase = "CSS application";
            System.out.println("Checking " + phase);
            stage.getScene().getRoot().applyCss();
            System.out.println("Completed " + phase);
            phase = "layout";
            System.out.println("Checking " + phase);
            stage.getScene().getRoot().layout();
            System.out.println("Completed " + phase);
            phase = "scene snapshot";
            System.out.println("Checking " + phase);
            stage.getScene().snapshot(null);
            System.out.println("Completed " + phase);
            exitCode = 0;
        } catch (Exception | LinkageError exception) {
            System.err.println("Installation verification failed during " + phase);
            exception.printStackTrace(System.err);
        } finally {
            try {
                System.out.println("Checking stage close");
                stage.close();
                System.out.println("Completed stage close");
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
                System.out.println("Checking Platform.exit");
                Platform.exit();
                System.out.println("Completed Platform.exit request");
            }
        }
    }
}
