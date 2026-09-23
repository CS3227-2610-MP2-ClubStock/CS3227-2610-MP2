package clubstock.ui.auth;

import java.util.Optional;

/**
 * Immutable identity information required by role-aware navigation.
 *
 * @param role Authenticated role.
 * @param memberId Member identity for a Member principal, or {@code null} for Exco.
 */
public record AuthenticatedPrincipal(UserRole role, String memberId) {
    /**
     * Validates role-specific identity state.
     */
    public AuthenticatedPrincipal {
        if (role == null) {
            throw new IllegalArgumentException("Principal role cannot be null.");
        }
        if (role == UserRole.MEMBER && (memberId == null || memberId.isBlank())) {
            throw new IllegalArgumentException("A Member principal requires a Member ID.");
        }
        if (role == UserRole.EXCO && memberId != null) {
            throw new IllegalArgumentException("An Exco principal cannot carry a Member ID.");
        }
    }

    /**
     * Creates the singleton Exco principal.
     *
     * @return Exco principal.
     */
    public static AuthenticatedPrincipal exco() {
        return new AuthenticatedPrincipal(UserRole.EXCO, null);
    }

    /**
     * Creates a Member principal.
     *
     * @param memberId Authenticated Member ID.
     * @return Member principal.
     */
    public static AuthenticatedPrincipal member(String memberId) {
        return new AuthenticatedPrincipal(UserRole.MEMBER, memberId);
    }

    /**
     * Returns the Member identity when this is a Member principal.
     *
     * @return Optional Member ID.
     */
    public Optional<String> optionalMemberId() {
        return Optional.ofNullable(memberId);
    }
}
