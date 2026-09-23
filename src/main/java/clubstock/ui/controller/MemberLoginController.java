package clubstock.ui.controller;

import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;

/**
 * Temporary route endpoint replaced by Keith's Member-login controller during integration.
 *
 * <p>It deliberately contains no credential form or authentication behavior.</p>
 */
public final class MemberLoginController {
    private final NavigationService navigation;

    /**
     * Creates the integration placeholder.
     *
     * @param navigation Navigation boundary.
     */
    public MemberLoginController(NavigationService navigation) {
        if (navigation == null) {
            throw new IllegalArgumentException("Navigation cannot be null.");
        }
        this.navigation = navigation;
    }

    @FXML
    private void goBack() {
        navigation.show(Route.ROLE_SELECTION);
    }
}
