package clubstock.ui.controller;

import clubstock.ui.auth.LogoutAction;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;

/**
 * Handles the initial Exco role host.
 */
public final class ExcoHomeController {
    private final LogoutAction logoutAction;
    private final NavigationService navigation;

    /**
     * Creates the controller.
     *
     * @param logoutAction Shared logout action.
     */
    public ExcoHomeController(LogoutAction logoutAction, NavigationService navigation) {
        if (logoutAction == null || navigation == null) {
            throw new IllegalArgumentException("Exco home dependencies cannot be null.");
        }
        this.logoutAction = logoutAction;
        this.navigation = navigation;
    }

    @FXML
    private void logout() {
        logoutAction.execute();
    }

    /**
     * Opens Exco Member-account administration.
     */
    @FXML
    private void manageMembers() {
        navigation.show(Route.MEMBER_ADMINISTRATION);
    }

    /**
     * Opens Exco equipment type and item administration.
     */
    @FXML
    private void manageInventory() {
        navigation.show(Route.INVENTORY_ADMINISTRATION);
    }

    /**
     * Opens the Exco pending-request queue.
     */
    @FXML
    private void manageRequests() {
        navigation.show(Route.EXCO_REQUEST_QUEUE);
    }

    @FXML private void manageLoans() { navigation.show(Route.EXCO_ACTIVE_LOANS); }
}
