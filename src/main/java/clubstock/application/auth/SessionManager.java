package clubstock.application.auth;

import java.util.Optional;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.domain.account.MemberId;

/**
 * Holds the single authenticated principal for this application process.
 */
public final class SessionManager {
    private Principal principal;

    /**
     * Creates an unauthenticated session manager.
     */
    public SessionManager() {
    }

    /**
     * Returns the current principal when a user is authenticated.
     *
     * @return Current principal, if present.
     */
    public synchronized Optional<Principal> currentPrincipal() {
        return Optional.ofNullable(principal);
    }

    /**
     * Starts a session for the supplied principal when no session is active.
     *
     * @param authenticatedPrincipal Principal to authenticate.
     */
    synchronized void establish(Principal authenticatedPrincipal) {
        if (authenticatedPrincipal == null) {
            throw new IllegalArgumentException("Principal cannot be null.");
        }
        if (principal != null) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                    "Log out before starting another session.", null);
        }
        principal = authenticatedPrincipal;
    }

    /**
     * Returns the current Exco principal or rejects the operation.
     *
     * @return Authenticated Exco principal.
     */
    public synchronized Principal requireExco() {
        if (principal == null || principal.role() != AccountRole.EXCO) {
            throw authorizationFailure();
        }
        return principal;
    }

    /**
     * Returns the current Member identity or rejects the operation.
     *
     * @return Authenticated Member ID.
     */
    public synchronized MemberId requireMember() {
        if (principal == null || principal.role() != AccountRole.MEMBER) {
            throw authorizationFailure();
        }
        return principal.memberId().orElseThrow();
    }

    /**
     * Requires the authenticated Member to match the requested owner.
     *
     * @param requestedMemberId Owner identity required by the operation.
     * @return Authenticated Member ID.
     */
    public synchronized MemberId requireMember(MemberId requestedMemberId) {
        if (requestedMemberId == null || !requireMember().equals(requestedMemberId)) {
            throw authorizationFailure();
        }
        return requestedMemberId;
    }

    /**
     * Clears the current principal. Repeated calls are safe.
     */
    public synchronized void logout() {
        principal = null;
    }

    /**
     * Creates a stable authorization failure for guarded operations.
     *
     * @return Authorization failure.
     */
    private static ApplicationException authorizationFailure() {
        return new ApplicationException(ApplicationErrorCode.AUTHORIZATION_DENIED,
                "This operation is not available for the current session.", null);
    }
}
