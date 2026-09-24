package clubstock.ui.auth;

import java.util.Arrays;
import java.util.Optional;

import clubstock.application.ApplicationException;
import clubstock.domain.account.MemberId;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

/**
 * Authenticates a Member and opens the protected Member home route.
 */
public final class MemberAuthenticationAction {
    private static final String SESSION_ERROR =
            "Sign in could not be completed. Please try again.";
    private final AuthenticationGateway authentication;
    private final NavigationService navigation;

    /**
     * Creates the Member authentication action.
     *
     * @param authentication Shared authentication boundary.
     * @param navigation Role-aware navigation boundary.
     */
    public MemberAuthenticationAction(AuthenticationGateway authentication,
            NavigationService navigation) {
        if (authentication == null || navigation == null) {
            throw new IllegalArgumentException("Authentication action dependencies cannot be null.");
        }
        this.authentication = authentication;
        this.navigation = navigation;
    }

    /**
     * Authenticates the supplied Member credentials and routes on success.
     *
     * @param memberId Supplied Member identity.
     * @param password Supplied password characters.
     * @return Safe expected-error text, when authentication failed.
     */
    public Optional<String> login(String memberId, char[] password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        try {
            try {
                authentication.authenticateMember(memberId, password);
            } catch (ApplicationException exception) {
                return Optional.of(exception.displayMessage());
            }
            if (!hasMatchingMemberPrincipal(memberId)) {
                authentication.logout();
                return Optional.of(SESSION_ERROR);
            }
            try {
                navigation.show(Route.MEMBER_HOME);
                return Optional.empty();
            } catch (RuntimeException exception) {
                authentication.logout();
                throw exception;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Verifies the gateway established the expected Member principal.
     *
     * @param memberId Supplied Member identity.
     * @return True when the current principal identifies this Member.
     */
    private boolean hasMatchingMemberPrincipal(String memberId) {
        String normalizedMemberId;
        try {
            normalizedMemberId = new MemberId(memberId).value();
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return authentication.currentPrincipal()
                .filter(principal -> principal.role() == UserRole.MEMBER)
                .flatMap(AuthenticatedPrincipal::optionalMemberId)
                .filter(normalizedMemberId::equals)
                .isPresent();
    }
}
