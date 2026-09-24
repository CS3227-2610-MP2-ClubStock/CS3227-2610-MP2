package clubstock.ui.auth;

import java.util.Arrays;
import java.util.Optional;

import clubstock.application.ApplicationException;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

/**
 * Executes Exco authentication operations independently of JavaFX controls.
 */
public final class ExcoAuthenticationAction {
    private static final String SESSION_ERROR =
            "Sign in could not be completed. Please try again.";
    private final AuthenticationGateway authentication;
    private final NavigationService navigation;

    /**
     * Creates an action using the shared authentication and navigation seams.
     *
     * @param authentication Authentication boundary.
     * @param navigation Navigation boundary.
     */
    public ExcoAuthenticationAction(AuthenticationGateway authentication,
            NavigationService navigation) {
        if (authentication == null || navigation == null) {
            throw new IllegalArgumentException("Authentication action dependencies cannot be null.");
        }
        this.authentication = authentication;
        this.navigation = navigation;
    }

    /**
     * Completes initial setup and routes to Exco home on success.
     *
     * @param password Password characters.
     * @param confirmation Confirmation characters.
     * @return Safe expected-error text, when setup failed.
     */
    public Optional<String> completeSetup(char[] password, char[] confirmation) {
        requireCredentials(password, confirmation);
        try {
            Optional<String> error = completeSetupOperation(password, confirmation);
            if (error.isPresent()) {
                return error;
            }
            return openExcoHome();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    /**
     * Authenticates Exco and routes to Exco home on success.
     *
     * @param password Password characters.
     * @return Safe expected-error text, when authentication failed.
     */
    public Optional<String> login(char[] password) {
        requireCredentials(password);
        try {
            Optional<String> error = loginOperation(password);
            if (error.isPresent()) {
                return error;
            }
            return openExcoHome();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private Optional<String> completeSetupOperation(char[] password, char[] confirmation) {
        try {
            authentication.completeExcoSetup(password, confirmation);
            return Optional.empty();
        } catch (ApplicationException exception) {
            return Optional.of(exception.displayMessage());
        }
    }

    private Optional<String> loginOperation(char[] password) {
        try {
            authentication.authenticateExco(password);
            return Optional.empty();
        } catch (ApplicationException exception) {
            return Optional.of(exception.displayMessage());
        }
    }

    private Optional<String> openExcoHome() {
        boolean hasExcoPrincipal = authentication.currentPrincipal()
                .map(principal -> principal.role() == UserRole.EXCO)
                .orElse(false);
        if (!hasExcoPrincipal) {
            authentication.logout();
            return Optional.of(SESSION_ERROR);
        }
        try {
            navigation.show(Route.EXCO_HOME);
            return Optional.empty();
        } catch (RuntimeException exception) {
            authentication.logout();
            throw exception;
        }
    }

    private static void requireCredentials(char[]... values) {
        for (char[] value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Credential arrays cannot be null.");
            }
        }
    }
}
