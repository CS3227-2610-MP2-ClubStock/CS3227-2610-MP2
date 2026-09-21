package clubstock.domain.account;

/**
 * Represents an immutable, case-sensitive Member identity.
 *
 * @param value Trimmed Member identifier value.
 */
public record MemberId(String value) {

    /**
     * Creates a Member ID after validating and normalizing its value.
     *
     * @param value Member identifier to validate.
     * @throws IllegalArgumentException If the value is null or blank.
     */
    public MemberId {
        value = AccountValidation.requireTrimmedNonBlank(value, "Member ID");
    }
}
