package clubstock.ui.auth;

import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

/**
 * Clears the session before returning to role selection.
 */
public final class LogoutAction {
    private final AuthenticationGateway authentication;
    private final NavigationService navigation;

    /**
     * Creates the logout action.
     *
     * @param authentication Authentication/session boundary.
     * @param navigation Navigation boundary.
     */
    public LogoutAction(AuthenticationGateway authentication, NavigationService navigation) {
        if (authentication == null || navigation == null) {
            throw new IllegalArgumentException("Logout dependencies cannot be null.");
        }
        this.authentication = authentication;
        this.navigation = navigation;
    }

    /**
     * Clears the session, then shows role selection.
     */
    public void execute() {
        authentication.logout();
        navigation.show(Route.ROLE_SELECTION);
    }
}
