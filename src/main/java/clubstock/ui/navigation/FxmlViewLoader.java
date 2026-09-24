package clubstock.ui.navigation;

import java.io.IOException;
import java.net.URL;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * Loads route FXML using the production dependency-injection factory.
 */
public final class FxmlViewLoader {
    private final ControllerFactory controllerFactory;

    /**
     * Creates the loader.
     *
     * @param controllerFactory Controller injection registry.
     */
    public FxmlViewLoader(ControllerFactory controllerFactory) {
        if (controllerFactory == null) {
            throw new IllegalArgumentException("Controller factory cannot be null.");
        }
        this.controllerFactory = controllerFactory;
    }

    /**
     * Loads one route root.
     *
     * @param route Route to load.
     * @return Loaded JavaFX root.
     */
    public Parent load(Route route) {
        if (route == null) {
            throw new IllegalArgumentException("Route cannot be null.");
        }
        URL resource = FxmlViewLoader.class.getResource(route.resourcePath());
        if (resource == null) {
            throw resourceFailure();
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(controllerFactory::create);
        try {
            return loader.load();
        } catch (IOException | RuntimeException exception) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "The requested screen could not be opened.", exception);
        }
    }

    private static ApplicationException resourceFailure() {
        return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                "The requested screen could not be opened.", null);
    }
}
