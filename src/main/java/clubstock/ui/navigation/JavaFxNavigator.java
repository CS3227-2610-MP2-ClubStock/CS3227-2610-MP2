package clubstock.ui.navigation;

import java.lang.ref.WeakReference;
import java.net.URL;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.ui.auth.AuthenticationGateway;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Loads FXML into the primary stage and applies role guards before view creation.
 */
public final class JavaFxNavigator implements NavigationService {
    private static final String STYLESHEET = "/clubstock/ui/clubstock.css";
    private static final String ACCESS_DENIED =
            "That screen requires a matching signed-in role. Please sign in again.";
    private final WeakReference<Stage> stageReference;
    private final AuthenticationGateway authentication;
    private final NavigationPolicy policy;
    private final FxmlViewLoader viewLoader;
    private final ErrorPresenter errorPresenter;

    /**
     * Creates the production navigator.
     *
     * @param stage Primary stage.
     * @param authentication Authentication/session boundary.
     * @param viewLoader FXML loader.
     * @param errorPresenter Safe error presenter.
     */
    public JavaFxNavigator(Stage stage, AuthenticationGateway authentication,
            FxmlViewLoader viewLoader, ErrorPresenter errorPresenter) {
        if (stage == null || authentication == null || viewLoader == null
                || errorPresenter == null) {
            throw new IllegalArgumentException("Navigator dependencies cannot be null.");
        }
        stageReference = new WeakReference<>(stage);
        this.authentication = authentication;
        this.policy = new NavigationPolicy();
        this.viewLoader = viewLoader;
        this.errorPresenter = errorPresenter;
        stage.setMinWidth(720);
        stage.setMinHeight(520);
    }

    @Override
    public void show(Route route) {
        if (route == null) {
            throw new IllegalArgumentException("Route cannot be null.");
        }
        if (!policy.permits(route, authentication.currentPrincipal())) {
            errorPresenter.showError("Sign in required", ACCESS_DENIED);
            install(Route.ROLE_SELECTION);
            return;
        }
        install(route);
    }

    private void install(Route route) {
        try {
            Stage stage = primaryStage();
            Parent root = viewLoader.load(route);
            Scene scene = stage.getScene();
            if (scene == null) {
                scene = new Scene(root, 960, 640);
                scene.getStylesheets().add(requireStylesheet());
                stage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            stage.setTitle("ClubStock — " + route.windowTitle());
        } catch (ApplicationException exception) {
            errorPresenter.showError("Screen unavailable", exception.displayMessage());
            throw exception;
        }
    }

    private static String requireStylesheet() {
        URL resource = JavaFxNavigator.class.getResource(STYLESHEET);
        if (resource == null) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "The application style could not be loaded.", null);
        }
        return resource.toExternalForm();
    }

    private Stage primaryStage() {
        Stage stage = stageReference.get();
        if (stage == null) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "The application window is not available.", null);
        }
        return stage;
    }
}
