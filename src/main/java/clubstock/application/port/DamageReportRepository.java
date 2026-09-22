package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.loan.LoanId;
import clubstock.domain.report.DamageReport;

/**
 * Persists immutable damage reports.
 */
public interface DamageReportRepository {
    /**
     * Finds a damage report by Loan identity.
     *
     * @param loanId Loan identity.
     * @return Matching report, when present.
     */
    Optional<DamageReport> findByLoanId(LoanId loanId);
    /**
     * Returns all damage reports ordered by Loan identity.
     *
     * @return All damage reports.
     */
    List<DamageReport> findAll();
    /**
     * Inserts a damage report.
     *
     * @param report Report to insert.
     */
    void insert(DamageReport report);
}
