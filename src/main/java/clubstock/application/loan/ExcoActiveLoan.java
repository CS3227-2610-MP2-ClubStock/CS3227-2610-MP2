package clubstock.application.loan;

import java.time.Instant;
import java.time.LocalDate;

import clubstock.domain.loan.LoanStatus;

/** Safe Exco-facing summary of one unresolved individual Loan. */
public record ExcoActiveLoan(String loanId, String memberId, String memberName,
        String equipmentTypeId, String equipmentTypeName, String equipmentId, LoanStatus status,
        Instant startedAt, LocalDate endDate, boolean overdue) {
}
