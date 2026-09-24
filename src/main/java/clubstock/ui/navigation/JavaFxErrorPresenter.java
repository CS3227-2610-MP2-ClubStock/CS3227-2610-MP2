package clubstock.ui.navigation;

import java.lang.ref.WeakReference;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Window;

/**
 * Displays safe navigation errors in JavaFX dialogs.
 */
public final class JavaFxErrorPresenter implements ErrorPresenter {
    private final WeakReference<Window> ownerReference;

    /**
     * Creates a presenter owned by the primary window.
     *
     * @param owner Primary window.
     */
    public JavaFxErrorPresenter(Window owner) {
        if (owner == null) {
            throw new IllegalArgumentException("Error dialog owner cannot be null.");
        }
        ownerReference = new WeakReference<>(owner);
    }

    @Override
    public void showError(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        Window owner = ownerReference.get();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
