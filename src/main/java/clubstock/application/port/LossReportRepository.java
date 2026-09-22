package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.loan.LoanId;
import clubstock.domain.report.LossReport;

/**
 * Persists immutable loss reports.
 */
public interface LossReportRepository {
    /**
     * Finds a loss report by Loan identity.
     *
     * @param loanId Loan identity.
     * @return Matching report, when present.
     */
    Optional<LossReport> findByLoanId(LoanId loanId);
    /**
     * Returns all loss reports ordered by Loan identity.
     *
     * @return All loss reports.
     */
    List<LossReport> findAll();
    /**
     * Inserts a loss report.
     *
     * @param report Report to insert.
     */
    void insert(LossReport report);
}
