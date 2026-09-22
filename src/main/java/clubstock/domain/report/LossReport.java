package clubstock.domain.report;

import clubstock.domain.loan.LoanId;

/**
 * Represents immutable advisory loss evidence submitted for one loan.
 */
public final class LossReport {
    private final LoanId loanId;
    private final String description;

    private LossReport(LoanId loanId, String description) {
        this.loanId = loanId;
        this.description = description;
    }

    /**
     * Creates a loss report with a required description.
     *
     * @param loanId Loan identity being reported.
     * @param description Member's loss description.
     * @return Newly created immutable loss report.
     * @throws IllegalArgumentException If the loan ID is null or the description is blank.
     */
    public static LossReport create(LoanId loanId, String description)
            throws IllegalArgumentException {
        ReportValidation.requireNonNull(loanId, "Loan ID");
        String normalizedDescription = ReportValidation.requireTrimmedNonBlank(description,
                "Loss description");
        return new LossReport(loanId, normalizedDescription);
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
     * Returns the stripped loss description.
     *
     * @return Loss description.
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof LossReport lossReport)) {
            return false;
        }

        return loanId.equals(lossReport.loanId);
    }

    @Override
    public int hashCode() {
        return loanId.hashCode();
    }
}
