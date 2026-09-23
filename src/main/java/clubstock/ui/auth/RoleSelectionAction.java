package clubstock.ui.auth;

import java.util.Optional;

import clubstock.application.ApplicationException;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

/**
 * Resolves the authentication route for a selected role.
 */
public final class RoleSelectionAction {
    private final AuthenticationGateway authentication;
    private final NavigationService navigation;

    /**
     * Creates the role-selection action.
     *
     * @param authentication Authentication boundary.
     * @param navigation Navigation boundary.
     */
    public RoleSelectionAction(AuthenticationGateway authentication,
            NavigationService navigation) {
        if (authentication == null || navigation == null) {
            throw new IllegalArgumentException("Role-selection dependencies cannot be null.");
        }
        this.authentication = authentication;
        this.navigation = navigation;
    }

    /**
     * Opens Exco setup or login based on persisted setup state.
     *
     * @return Safe expected-error text, when the state could not be queried.
     */
    public Optional<String> selectExco() {
        try {
            Route route = authentication.isExcoSetupRequired()
                    ? Route.EXCO_SETUP : Route.EXCO_LOGIN;
            navigation.show(route);
            return Optional.empty();
        } catch (ApplicationException exception) {
            return Optional.of(exception.displayMessage());
        }
    }

    /**
     * Opens the Member-login integration route.
     */
    public void selectMember() {
        navigation.show(Route.MEMBER_LOGIN);
    }
}
