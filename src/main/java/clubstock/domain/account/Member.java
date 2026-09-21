package clubstock.domain.account;

/**
 * Owns a Member's immutable identity, display name, and current credential hash.
 */
public final class Member {
    private final MemberId memberId;
    private String name;
    private PasswordHash passwordHash;

    private Member(MemberId memberId, String name, PasswordHash passwordHash) {
        this.memberId = requireMemberId(memberId);
        this.name = AccountValidation.requireTrimmedNonBlank(name, "Member name");
        this.passwordHash = requirePasswordHash(passwordHash);
    }

    /**
     * Creates a Member with validated identity, name, and credential state.
     *
     * @param memberId Immutable Member identity.
     * @param name Member display name.
     * @param passwordHash Opaque encoded password hash.
     * @return Newly created Member.
     * @throws IllegalArgumentException If the Member ID or password hash is null, or if the
     *         name is null or blank.
     */
    public static Member create(MemberId memberId, String name, PasswordHash passwordHash) {
        return new Member(memberId, name, passwordHash);
    }

    /**
     * Returns this Member's immutable identity.
     *
     * @return Member identity.
     */
    public MemberId memberId() {
        return memberId;
    }

    /**
     * Returns this Member's current display name.
     *
     * @return Trimmed Member name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns this Member's opaque credential hash.
     *
     * @return Current password hash.
     */
    public PasswordHash passwordHash() {
        return passwordHash;
    }

    /**
     * Replaces this Member's display name after validating it.
     *
     * @param name Replacement Member name.
     * @throws IllegalArgumentException If the name is null or blank.
     */
    public void updateName(String name) throws IllegalArgumentException {
        String validatedName = AccountValidation.requireTrimmedNonBlank(name, "Member name");
        this.name = validatedName;
    }

    /**
     * Replaces this Member's credential hash.
     *
     * @param passwordHash Replacement opaque password hash.
     * @throws IllegalArgumentException If the password hash is null.
     */
    public void replacePasswordHash(PasswordHash passwordHash) throws IllegalArgumentException {
        this.passwordHash = requirePasswordHash(passwordHash);
    }

    /**
     * Returns a validated Member ID for account construction.
     *
     * @param memberId Member ID to validate.
     * @return Validated Member ID.
     * @throws IllegalArgumentException If the Member ID is null.
     */
    private static MemberId requireMemberId(MemberId memberId) throws IllegalArgumentException {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID cannot be null.");
        }

        return memberId;
    }

    /**
     * Returns a validated password hash for account construction or editing.
     *
     * @param passwordHash Password hash to validate.
     * @return Validated password hash.
     * @throws IllegalArgumentException If the password hash is null.
     */
    private static PasswordHash requirePasswordHash(PasswordHash passwordHash)
            throws IllegalArgumentException {
        if (passwordHash == null) {
            throw new IllegalArgumentException("Password hash cannot be null.");
        }

        return passwordHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof Member member)) {
            return false;
        }

        return memberId.equals(member.memberId);
    }

    @Override
    public int hashCode() {
        return memberId.hashCode();
    }
}
