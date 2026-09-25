package clubstock.application.loan;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanStatus;

/**
 * Queries role-authorized unresolved Loans without changing stored lifecycle state.
 */
public final class LoanQueryService {
    private final TransactionManager transactions;
    private final SessionManager sessions;
    private final Clock clock;
    private final ZoneId zoneId;

    /**
     * Creates a role-authorized Loan query service.
     *
     * @param transactions Shared transaction boundary.
     * @param sessions Authenticated session source.
     * @param clock Clock used to determine the current instant.
     * @param zoneId Application timezone used to determine the current date.
     */
    public LoanQueryService(TransactionManager transactions, SessionManager sessions, Clock clock,
            ZoneId zoneId) {
        if (transactions == null || sessions == null || clock == null || zoneId == null) {
            throw new IllegalArgumentException("Loan query dependencies cannot be null.");
        }
        this.transactions = transactions;
        this.sessions = sessions;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    /**
     * Returns every Exco-visible unresolved Loan with computed overdue state.
     *
     * @return All unresolved Loan summaries visible to Exco.
     */
    public List<ExcoActiveLoan> listActiveForExco() {
        sessions.requireExco();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return transactions.read(unit -> unit.loans().findAll().stream()
                .filter(LoanQueryService::active)
                .map(loan -> mapForExco(unit, loan, today))
                .toList());
    }

    /**
     * Returns the authenticated Member's unresolved Loans and assigned item IDs.
     *
     * @return Individual active Loan summaries for the current Member.
     */
    public List<MemberActiveLoan> listActiveForMember() {
        MemberId memberId = sessions.requireMember();
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return transactions.read(unit -> {
            sessions.requireMember(memberId);
            return unit.loans().findAll().stream()
                    .filter(LoanQueryService::active)
                    .filter(loan -> loan.memberId().equals(memberId))
                    .map(loan -> mapForMember(unit, loan, today))
                    .toList();
        });
    }

    /**
     * Returns whether a Loan has an unresolved lifecycle status.
     *
     * @param loan Loan whose lifecycle status is checked.
     * @return True when the Loan is unresolved.
     */
    private static boolean active(Loan loan) {
        return loan.status() == LoanStatus.ON_LOAN || loan.status() == LoanStatus.RETURN_PENDING
                || loan.status() == LoanStatus.LOST_PENDING;
    }

    /**
     * Maps an unresolved Loan to its Exco-facing summary.
     *
     * @param unit Read unit containing shared repositories.
     * @param loan Unresolved Loan to map.
     * @param today Current date in the configured application timezone.
     * @return Exco-facing Loan summary.
     */
    private static ExcoActiveLoan mapForExco(UnitOfWork unit, Loan loan, LocalDate today) {
        Member member = member(unit, loan);
        EquipmentType type = equipmentType(unit, loan);
        return new ExcoActiveLoan(loan.loanId().value(), loan.memberId().value(), member.name(),
                type.equipmentTypeId().value(), type.name().value(), loan.equipmentId().value(),
                loan.status(), loan.startedAt(), loan.endDate(), loan.isOverdue(today));
    }

    /**
     * Maps an unresolved Loan to its owning Member's summary.
     *
     * @param unit Read unit containing shared repositories.
     * @param loan Unresolved Loan to map.
     * @param today Current date in the configured application timezone.
     * @return Member-facing Loan summary.
     */
    private static MemberActiveLoan mapForMember(UnitOfWork unit, Loan loan, LocalDate today) {
        member(unit, loan);
        EquipmentType type = equipmentType(unit, loan);
        return new MemberActiveLoan(loan.loanId().value(), type.equipmentTypeId().value(),
                type.name().value(), loan.equipmentId().value(), loan.status(), loan.startedAt(),
                loan.endDate(), loan.isOverdue(today));
    }

    /**
     * Returns the Member referenced by a Loan.
     *
     * @param unit Read unit containing shared repositories.
     * @param loan Loan whose owner is resolved.
     * @return Referenced Member.
     * @throws ApplicationException If the Loan references a missing Member.
     */
    private static Member member(UnitOfWork unit, Loan loan) {
        return unit.members().findById(loan.memberId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "An active Loan references a missing Member.", null));
    }

    /**
     * Returns the equipment type referenced by a Loan's assigned item.
     *
     * @param unit Read unit containing shared repositories.
     * @param loan Loan whose assigned item's type is resolved.
     * @return Referenced equipment type.
     * @throws ApplicationException If the Loan references missing equipment or a missing type.
     */
    private static EquipmentType equipmentType(UnitOfWork unit, Loan loan) {
        EquipmentItem item = unit.equipmentItems().findById(loan.equipmentId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "An active Loan references missing equipment.", null));
        return unit.equipmentTypes().findById(item.equipmentTypeId()).orElseThrow(() ->
                new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "An active Loan references a missing equipment type.", null));
    }
}
