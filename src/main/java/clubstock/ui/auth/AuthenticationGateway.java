package clubstock.ui.auth;

import java.util.Optional;

/**
 * UI-facing seam for Keith's authentication and in-memory session contract.
 *
 * <p>This interface owns no authentication policy. A production adapter delegates to the shared
 * authentication service and session manager.</p>
 */
public interface AuthenticationGateway {
    /**
     * Returns whether the singleton Exco account still needs first-run setup.
     *
     * @return True when setup is required.
     */
    boolean isExcoSetupRequired();

    /**
     * Completes one-time Exco setup and establishes the Exco principal.
     *
     * @param password Password characters; the caller clears its array after the call.
     * @param confirmation Confirmation characters; the caller clears its array after the call.
     */
    void completeExcoSetup(char[] password, char[] confirmation);

    /**
     * Authenticates Exco and establishes the Exco principal.
     *
     * @param password Password characters; the caller clears its array after the call.
     */
    void authenticateExco(char[] password);

    /**
     * Returns the current in-memory principal.
     *
     * @return Current principal, when authenticated.
     */
    Optional<AuthenticatedPrincipal> currentPrincipal();

    /**
     * Clears the current principal. Repeated calls must be safe.
     */
    void logout();
}
