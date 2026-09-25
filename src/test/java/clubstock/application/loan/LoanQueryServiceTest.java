package clubstock.application.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.Principal;
import clubstock.application.auth.SessionManager;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.loan.ReportedReturnCondition;
import clubstock.domain.report.LossReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class LoanQueryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"),
            ZoneOffset.UTC);
    private static final ZoneOffset ZONE = ZoneOffset.UTC;

    @Test
    void listActiveForExco_filtersUnresolvedLoansAndComputesStrictOverdueWithoutMutation(
            @TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addLoan(database, "on-loan-overdue", "item-overdue", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 24));
        addLoan(database, "on-loan-today", "item-today", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 25));
        addLoan(database, "return-pending", "item-return", LoanStatus.RETURN_PENDING,
                LocalDate.of(2026, 9, 24));
        addLoan(database, "loss-pending", "item-loss", LoanStatus.LOST_PENDING,
                LocalDate.of(2026, 9, 24));
        addLoan(database, "completed", "item-completed", LoanStatus.COMPLETED,
                LocalDate.of(2026, 9, 20));

        List<ExcoActiveLoan> loans = service(database, excoSession()).listActiveForExco();

        assertEquals(List.of("loss-pending", "on-loan-overdue", "on-loan-today", "return-pending"),
                loans.stream().map(ExcoActiveLoan::loanId).sorted().toList());
        assertTrue(find(loans, "on-loan-overdue").overdue());
        assertFalse(find(loans, "on-loan-today").overdue());
        assertFalse(find(loans, "return-pending").overdue());
        assertFalse(find(loans, "loss-pending").overdue());

        database.read(unit -> {
            assertEquals(LoanStatus.ON_LOAN, unit.loans().findById(new LoanId("on-loan-overdue"))
                    .orElseThrow().status());
            assertEquals(LoanStatus.RETURN_PENDING,
                    unit.loans().findById(new LoanId("return-pending")).orElseThrow().status());
            assertEquals(LoanStatus.LOST_PENDING,
                    unit.loans().findById(new LoanId("loss-pending")).orElseThrow().status());
            return null;
        });
    }

    @Test
    void listActiveForExco_rejectsMemberAndUnauthenticatedSessions(
            @TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addLoan(database, "on-loan", "item-on-loan", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 25));

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, new SessionManager()).listActiveForExco());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, memberSession()).listActiveForExco());
    }

    private static SqliteDatabase database(java.nio.file.Path temp) {
        SqliteDatabase database = new SqliteDatabase(temp.resolve("clubstock.db"));
        database.initialize();
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-1"),
                new EquipmentTypeName("Rackets"));
        type.offer();
        database.write(unit -> {
            unit.members().insert(Member.create(new MemberId("member-1"), "Member One",
                    new PasswordHash("test")));
            unit.equipmentTypes().insert(type);
            return null;
        });
        return database;
    }

    private static void addLoan(SqliteDatabase database, String loanId, String itemId,
            LoanStatus status, LocalDate endDate) {
        database.write(unit -> {
            EquipmentItem item = item(itemId, status);
            Loan loan = Loan.restore(new LoanId(loanId), new LoanRequestId("request-" + loanId),
                    new MemberId("member-1"), new EquipmentId(itemId), CLOCK.instant(), endDate,
                    status, status == LoanStatus.RETURN_PENDING || status == LoanStatus.COMPLETED
                            ? ReportedReturnCondition.GOOD : null);
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(approvedRequest(loanId, endDate));
            unit.loans().insert(loan);
            if (status == LoanStatus.LOST_PENDING) {
                unit.lossReports().insert(LossReport.create(loan.loanId(), "Lost item"));
            }
            return null;
        });
    }

    private static EquipmentItem item(String itemId, LoanStatus status) {
        EquipmentAvailability availability = switch (status) {
        case ON_LOAN -> EquipmentAvailability.ON_LOAN;
        case RETURN_PENDING, LOST_PENDING -> EquipmentAvailability.UNAVAILABLE;
        case COMPLETED -> EquipmentAvailability.AVAILABLE;
        };
        boolean verificationPending = status == LoanStatus.RETURN_PENDING
                || status == LoanStatus.LOST_PENDING;
        return EquipmentItem.restore(new EquipmentId(itemId), new EquipmentTypeId("type-1"),
                EquipmentCondition.GOOD, availability, verificationPending, false, null);
    }

    private static LoanRequest approvedRequest(String loanId, LocalDate endDate) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-" + loanId),
                new MemberId("member-1"), new EquipmentTypeId("type-1"), 1,
                LocalDate.of(2026, 9, 20), endDate, null, CLOCK);
        request.approve(1);
        return request;
    }

    private static ExcoActiveLoan find(List<ExcoActiveLoan> loans, String loanId) {
        return loans.stream().filter(loan -> loan.loanId().equals(loanId)).findFirst().orElseThrow();
    }

    private static LoanQueryService service(SqliteDatabase database, SessionManager session) {
        return new LoanQueryService(database, session, CLOCK, ZONE);
    }

    private static SessionManager excoSession() {
        return session(Principal.exco());
    }

    private static SessionManager memberSession() {
        return session(Principal.member(new MemberId("member-1")));
    }

    private static SessionManager session(Principal principal) {
        SessionManager session = new SessionManager();
        try {
            var establish = SessionManager.class.getDeclaredMethod("establish", Principal.class);
            establish.setAccessible(true);
            establish.invoke(session, principal);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return session;
    }

    private static void assertError(ApplicationErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(ApplicationException.class, action::run).errorCode());
    }
}
