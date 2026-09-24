package clubstock.application.loan;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanStatus;

/** Queries role-authorized unresolved Loans without changing stored lifecycle state. */
public final class LoanQueryService {
    private final TransactionManager transactions;
    private final SessionManager sessions;
    private final Clock clock;
    private final ZoneId zoneId;

    public LoanQueryService(TransactionManager transactions, SessionManager sessions, Clock clock,
            ZoneId zoneId) {
        if (transactions == null || sessions == null || clock == null || zoneId == null) {
            throw new IllegalArgumentException("Loan query dependencies cannot be null.");
        }
        this.transactions = transactions; this.sessions = sessions; this.clock = clock; this.zoneId = zoneId;
    }

    /** Returns every Exco-visible unresolved Loan with computed overdue state. */
    public List<ExcoActiveLoan> listActiveForExco() {
        sessions.requireExco();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return transactions.read(unit -> unit.loans().findAll().stream()
                .filter(LoanQueryService::active).map(loan -> map(unit, loan, today)).toList());
    }

    private static boolean active(Loan loan) {
        return loan.status() == LoanStatus.ON_LOAN || loan.status() == LoanStatus.RETURN_PENDING
                || loan.status() == LoanStatus.LOST_PENDING;
    }

    private static ExcoActiveLoan map(clubstock.application.port.UnitOfWork unit, Loan loan,
            LocalDate today) {
        EquipmentItem item = unit.equipmentItems().findById(loan.equipmentId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "An active Loan references missing equipment.", null));
        var member = unit.members().findById(loan.memberId()).orElseThrow(() -> new ApplicationException(
                ApplicationErrorCode.PERSISTENCE_FAILURE, "An active Loan references a missing Member.", null));
        var type = unit.equipmentTypes().findById(item.equipmentTypeId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "An active Loan references a missing equipment type.", null));
        boolean overdue = loan.status() == LoanStatus.ON_LOAN && today.isAfter(loan.endDate());
        return new ExcoActiveLoan(loan.loanId().value(), loan.memberId().value(), member.name(),
                item.equipmentTypeId().value(), type.name().value(), loan.equipmentId().value(),
                loan.status(), loan.startedAt(), loan.endDate(), overdue);
    }
}
