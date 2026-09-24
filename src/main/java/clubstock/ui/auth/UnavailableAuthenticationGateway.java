package clubstock.ui.auth;

import java.util.Optional;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;

/**
 * Safe incremental-build fallback used until the shared authentication provider is present.
 */
public final class UnavailableAuthenticationGateway implements AuthenticationGateway {
    private static final String MESSAGE =
            "Authentication is not available in this build. Please try again after integration.";

    /**
     * Creates the safe incremental-build fallback.
     */
    public UnavailableAuthenticationGateway() {
    }

    @Override
    public boolean isExcoSetupRequired() {
        throw unavailable();
    }

    @Override
    public void completeExcoSetup(char[] password, char[] confirmation) {
        throw unavailable();
    }

    @Override
    public void authenticateExco(char[] password) {
        throw unavailable();
    }

    @Override
    public Optional<AuthenticatedPrincipal> currentPrincipal() {
        return Optional.empty();
    }

    @Override
    public void logout() {
        // No session exists in the incremental-build fallback.
    }

    private static ApplicationException unavailable() {
        return new ApplicationException(ApplicationErrorCode.AUTHENTICATION_FAILED, MESSAGE, null);
    }
}
