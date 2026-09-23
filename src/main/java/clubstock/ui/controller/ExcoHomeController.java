package clubstock.ui.controller;

import clubstock.ui.auth.LogoutAction;
import javafx.fxml.FXML;

/**
 * Handles the initial Exco role host.
 */
public final class ExcoHomeController {
    private final LogoutAction logoutAction;

    /**
     * Creates the controller.
     *
     * @param logoutAction Shared logout action.
     */
    public ExcoHomeController(LogoutAction logoutAction) {
        if (logoutAction == null) {
            throw new IllegalArgumentException("Logout action cannot be null.");
        }
        this.logoutAction = logoutAction;
    }

    @FXML
    private void logout() {
        logoutAction.execute();
    }
}
