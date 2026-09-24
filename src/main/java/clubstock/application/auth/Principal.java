package clubstock.application.auth;

import java.util.Optional;

import clubstock.domain.account.MemberId;

/**
 * Identifies the authenticated actor for the current application session.
 *
 * @param role Authenticated role.
 * @param memberId Member identity for a Member principal, or empty for Exco.
 */
public record Principal(AccountRole role, Optional<MemberId> memberId) {

    /**
     * Validates the role-specific identity state.
     */
    public Principal {
        if (role == null || memberId == null) {
            throw new IllegalArgumentException("Principal details cannot be null.");
        }
        if (role == AccountRole.MEMBER && memberId.isEmpty()) {
            throw new IllegalArgumentException("A Member principal requires a Member ID.");
        }
        if (role == AccountRole.EXCO && memberId.isPresent()) {
            throw new IllegalArgumentException("An Exco principal cannot carry a Member ID.");
        }
    }

    /**
     * Creates the Exco principal.
     *
     * @return Exco principal.
     */
    public static Principal exco() {
        return new Principal(AccountRole.EXCO, Optional.empty());
    }

    /**
     * Creates a Member principal.
     *
     * @param memberId Authenticated Member identity.
     * @return Member principal.
     */
    public static Principal member(MemberId memberId) {
        return new Principal(AccountRole.MEMBER, Optional.of(memberId));
    }
}
