package clubstock.ui.auth;

import java.util.Optional;

import clubstock.application.auth.AccountRole;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.Principal;
import clubstock.domain.account.MemberId;

/**
 * Adapts shared application authentication and session state for JavaFX navigation.
 */
public final class AuthenticationGatewayAdapter implements AuthenticationGateway {
    private final AuthenticationService authenticationService;

    /**
     * Creates the JavaFX adapter over the shared authentication services.
     *
     * @param authenticationService Shared authentication service.
     */
    public AuthenticationGatewayAdapter(AuthenticationService authenticationService) {
        if (authenticationService == null) {
            throw new IllegalArgumentException("Authentication service cannot be null.");
        }
        this.authenticationService = authenticationService;
    }

    @Override
    public boolean isExcoSetupRequired() {
        return authenticationService.requiresExcoSetup();
    }

    @Override
    public void completeExcoSetup(char[] password, char[] confirmation) {
        authenticationService.completeExcoSetup(password, confirmation);
    }

    @Override
    public void authenticateExco(char[] password) {
        authenticationService.authenticateExco(password);
    }

    @Override
    public void authenticateMember(String memberId, char[] password) {
        authenticationService.authenticateMember(memberId, password);
    }

    @Override
    public Optional<AuthenticatedPrincipal> currentPrincipal() {
        return authenticationService.currentPrincipal()
                .map(AuthenticationGatewayAdapter::toUiPrincipal);
    }

    @Override
    public void logout() {
        authenticationService.logout();
    }

    /**
     * Converts the application principal to the role information used by JavaFX navigation.
     *
     * @param principal Application principal.
     * @return JavaFX navigation principal.
     */
    private static AuthenticatedPrincipal toUiPrincipal(Principal principal) {
        if (principal.role() == AccountRole.EXCO) {
            return AuthenticatedPrincipal.exco();
        }
        MemberId memberId = principal.memberId().orElseThrow();
        return AuthenticatedPrincipal.member(memberId.value());
    }
}
