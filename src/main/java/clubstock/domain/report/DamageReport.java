package clubstock.domain.report;

import clubstock.domain.loan.LoanId;

/**
 * Represents immutable advisory damage evidence submitted for one loan.
 */
public final class DamageReport {
    private final LoanId loanId;
    private final DamageImageReference imageReference;
    private final String description;

    private DamageReport(LoanId loanId, DamageImageReference imageReference, String description) {
        this.loanId = loanId;
        this.imageReference = imageReference;
        this.description = description;
    }

    /**
     * Creates a damage report with required image evidence and description.
     *
     * @param loanId Loan identity being reported.
     * @param imageReference Validated damage image metadata.
     * @param description Member's damage description.
     * @return Newly created immutable damage report.
     * @throws IllegalArgumentException If an argument is missing or the description is blank.
     */
    public static DamageReport create(LoanId loanId, DamageImageReference imageReference,
            String description) throws IllegalArgumentException {
        ReportValidation.requireNonNull(loanId, "Loan ID");
        ReportValidation.requireNonNull(imageReference, "Damage image reference");
        String normalizedDescription = ReportValidation.requireTrimmedNonBlank(description,
                "Damage description");
        return new DamageReport(loanId, imageReference, normalizedDescription);
    }

    /**
     * Returns the loan identity associated with this report.
     *
     * @return Reported loan identity.
     */
    public LoanId loanId() {
        return loanId;
    }

    /**
     * Returns the managed damage image metadata.
     *
     * @return Damage image reference.
     */
    public DamageImageReference imageReference() {
        return imageReference;
    }

    /**
     * Returns the stripped damage description.
     *
     * @return Damage description.
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof DamageReport damageReport)) {
            return false;
        }

        return loanId.equals(damageReport.loanId);
    }

    @Override
    public int hashCode() {
        return loanId.hashCode();
    }
}
