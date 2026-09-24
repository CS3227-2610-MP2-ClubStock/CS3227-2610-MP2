package clubstock.application.member;

/**
 * Safe Member account data for Exco administration views.
 *
 * @param memberId Immutable Member login identity.
 * @param name Member display name.
 * @param active Whether the account can authenticate.
 */
public record MemberSummary(String memberId, String name, boolean active) {
    /**
     * Validates data emitted from the Member account service.
     */
    public MemberSummary {
        if (memberId == null || memberId.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Member summary details cannot be blank.");
        }
    }
}
