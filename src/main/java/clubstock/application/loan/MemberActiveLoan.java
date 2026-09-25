package clubstock.application.loan;

import java.time.Instant;
import java.time.LocalDate;

import clubstock.domain.loan.LoanStatus;

/**
 * Provides a safe Member-facing summary of one unresolved individual Loan.
 */
public record MemberActiveLoan(String loanId, String equipmentTypeId, String equipmentTypeName,
        String equipmentId, LoanStatus status, Instant startedAt, LocalDate endDate,
        boolean overdue) {
}
