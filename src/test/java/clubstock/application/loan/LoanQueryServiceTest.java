package clubstock.application.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.Principal;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.EquipmentItemAvailabilityPolicy;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWorkOperation;
import clubstock.application.request.ApprovalSelection;
import clubstock.application.request.ApprovalService;
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
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.id.UuidIdGenerator;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class LoanQueryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"),
            ZoneOffset.UTC);
    private static final ZoneOffset ZONE = ZoneOffset.UTC;
    private static final MemberId FIRST_MEMBER = new MemberId("member-1");
    private static final MemberId SECOND_MEMBER = new MemberId("member-2");
    private static final EquipmentTypeId TYPE_ID = new EquipmentTypeId("type-1");

    @Test
    void listActiveForExco_filtersUnresolvedLoansAndComputesStrictOverdueWithoutMutation(
            @TempDir Path temp) {
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
        assertTrue(findExco(loans, "on-loan-overdue").overdue());
        assertFalse(findExco(loans, "on-loan-today").overdue());
        assertFalse(findExco(loans, "return-pending").overdue());
        assertFalse(findExco(loans, "loss-pending").overdue());

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
    void listActiveForMember_filtersByOwnerAndUnresolvedStatus(@TempDir Path temp) {
        SqliteDatabase database = database(temp);
        addMember(database, SECOND_MEMBER, "Member Two");
        addLoan(database, "member-one-loan", "member-one-item", FIRST_MEMBER, TYPE_ID,
                LoanStatus.ON_LOAN, LocalDate.of(2026, 9, 24));
        addLoan(database, "member-one-return", "member-one-return-item", FIRST_MEMBER, TYPE_ID,
                LoanStatus.RETURN_PENDING, LocalDate.of(2026, 9, 24));
        addLoan(database, "member-one-loss", "member-one-loss-item", FIRST_MEMBER, TYPE_ID,
                LoanStatus.LOST_PENDING, LocalDate.of(2026, 9, 24));
        addLoan(database, "member-one-completed", "member-one-completed-item", FIRST_MEMBER, TYPE_ID,
                LoanStatus.COMPLETED, LocalDate.of(2026, 9, 20));
        addLoan(database, "member-two-loan", "member-two-item", SECOND_MEMBER, TYPE_ID,
                LoanStatus.ON_LOAN, LocalDate.of(2026, 9, 24));

        List<MemberActiveLoan> memberLoans = service(database, memberSession(FIRST_MEMBER))
                .listActiveForMember();
        List<ExcoActiveLoan> excoLoans = service(database, excoSession()).listActiveForExco();

        assertEquals(List.of("member-one-loan", "member-one-loss", "member-one-return"),
                memberLoans.stream().map(MemberActiveLoan::loanId).sorted().toList());
        assertEquals(List.of("member-one-item", "member-one-loss-item", "member-one-return-item"),
                memberLoans.stream().map(MemberActiveLoan::equipmentId).sorted().toList());
        assertEquals(LoanStatus.ON_LOAN, findMember(memberLoans, "member-one-loan").status());
        assertEquals("Rackets", findMember(memberLoans, "member-one-loan").equipmentTypeName());
        assertEquals(CLOCK.instant(), findMember(memberLoans, "member-one-loan").startedAt());
        assertEquals(LocalDate.of(2026, 9, 24), findMember(memberLoans, "member-one-loan").endDate());
        assertTrue(findMember(memberLoans, "member-one-loan").overdue());
        assertFalse(memberLoans.stream().anyMatch(loan -> loan.equipmentId().equals("member-two-item")));
        assertEquals(4, excoLoans.size());
        assertEquals("member-2", findExco(excoLoans, "member-two-loan").memberId());
    }

    @Test
    void listActiveForMember_rejectsExcoAndUnauthenticatedSessions(@TempDir Path temp) {
        SqliteDatabase database = database(temp);

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, new SessionManager()).listActiveForMember());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, excoSession()).listActiveForMember());
    }

    @Test
    void listActiveForMember_rejectsOwnerChangeInsideRead(@TempDir Path temp) {
        SqliteDatabase database = database(temp);
        addMember(database, SECOND_MEMBER, "Member Two");
        addLoan(database, "first-member-loan", "first-member-item", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 28));
        SessionManager session = memberSession(FIRST_MEMBER);
        TransactionManager changingSession = new TransactionManager() {
            @Override
            public <T> T read(UnitOfWorkOperation<T> operation) {
                return database.read(unit -> {
                    session.logout();
                    establish(session, Principal.member(SECOND_MEMBER));
                    return operation.execute(unit);
                });
            }

            @Override
            public <T> T write(UnitOfWorkOperation<T> operation) {
                return database.write(operation);
            }
        };

        LoanQueryService loanQueryService = new LoanQueryService(changingSession, session,
                CLOCK, ZONE);

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                loanQueryService::listActiveForMember);
        assertEquals(SECOND_MEMBER, session.requireMember());
    }

    @Test
    void listActiveForMember_usesConfiguredZoneAndStrictEndDateBoundary(@TempDir Path temp) {
        SqliteDatabase database = database(temp);
        addLoan(database, "end-yesterday", "item-yesterday", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 24));
        addLoan(database, "end-today", "item-today", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 25));
        addLoan(database, "end-tomorrow", "item-tomorrow", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 26));
        Clock boundaryClock = Clock.fixed(Instant.parse("2026-09-25T00:30:00Z"), ZoneOffset.UTC);

        List<MemberActiveLoan> losAngelesLoans = service(database, memberSession(FIRST_MEMBER),
                boundaryClock, ZoneId.of("America/Los_Angeles")).listActiveForMember();
        List<MemberActiveLoan> tokyoLoans = service(database, memberSession(FIRST_MEMBER),
                boundaryClock, ZoneId.of("Asia/Tokyo")).listActiveForMember();

        assertFalse(findMember(losAngelesLoans, "end-yesterday").overdue());
        assertFalse(findMember(losAngelesLoans, "end-today").overdue());
        assertFalse(findMember(losAngelesLoans, "end-tomorrow").overdue());
        assertTrue(findMember(tokyoLoans, "end-yesterday").overdue());
        assertFalse(findMember(tokyoLoans, "end-today").overdue());
        assertFalse(findMember(tokyoLoans, "end-tomorrow").overdue());
    }

    @Test
    void listActiveForMember_doesNotJoinAnotherMembersBrokenReferences(@TempDir Path temp) {
        Path databasePath = temp.resolve("clubstock.db");
        SqliteDatabase database = databaseAt(databasePath);
        addMember(database, SECOND_MEMBER, "Member Two");
        addLoan(database, "member-two-broken-loan", "member-two-missing-item", SECOND_MEMBER,
                TYPE_ID, LoanStatus.ON_LOAN, LocalDate.of(2026, 9, 28));
        deleteRow(databasePath, "equipment_items", "equipment_id", "member-two-missing-item");

        List<MemberActiveLoan> memberLoans = service(database, memberSession(FIRST_MEMBER))
                .listActiveForMember();

        assertTrue(memberLoans.isEmpty());
    }

    @Test
    void listActiveForMember_ignoresCompletedLoansBeforeResolvingReferences(@TempDir Path temp) {
        Path databasePath = temp.resolve("clubstock.db");
        SqliteDatabase database = databaseAt(databasePath);
        addLoan(database, "completed-broken-loan", "completed-missing-item", LoanStatus.COMPLETED,
                LocalDate.of(2026, 9, 20));
        deleteRow(databasePath, "equipment_items", "equipment_id", "completed-missing-item");

        assertTrue(service(database, memberSession(FIRST_MEMBER)).listActiveForMember().isEmpty());
        assertTrue(service(database, excoSession()).listActiveForExco().isEmpty());
    }

    @Test
    void listActiveForMember_failsWhenItsEquipmentReferenceIsMissing(@TempDir Path temp) {
        Path databasePath = temp.resolve("clubstock.db");
        SqliteDatabase database = databaseAt(databasePath);
        addLoan(database, "broken-equipment-loan", "missing-item", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 28));
        deleteRow(databasePath, "equipment_items", "equipment_id", "missing-item");

        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, memberSession(FIRST_MEMBER)).listActiveForMember());
        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, excoSession()).listActiveForExco());
    }

    @Test
    void listActiveForMember_failsWhenItsEquipmentTypeReferenceIsMissing(@TempDir Path temp) {
        Path databasePath = temp.resolve("clubstock.db");
        SqliteDatabase database = databaseAt(databasePath);
        addLoan(database, "broken-type-loan", "item-with-missing-type", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 28));
        deleteRow(databasePath, "equipment_types", "equipment_type_id", TYPE_ID.value());

        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, memberSession(FIRST_MEMBER)).listActiveForMember());
        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, excoSession()).listActiveForExco());
    }

    @Test
    void listActiveForMember_failsWhenItsMemberReferenceIsMissing(@TempDir Path temp) {
        Path databasePath = temp.resolve("clubstock.db");
        SqliteDatabase database = databaseAt(databasePath);
        addLoan(database, "broken-member-loan", "item-with-missing-member", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 28));
        deleteRow(databasePath, "members", "member_id", FIRST_MEMBER.value());

        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, memberSession(FIRST_MEMBER)).listActiveForMember());
        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service(database, excoSession()).listActiveForExco());
    }

    @Test
    void partialApproval_appearsAsSeparateRowsInBothRoleQueries(@TempDir Path temp) {
        SqliteDatabase database = database(temp);
        database.write(unit -> {
            for (String equipmentId : List.of("partial-item-1", "partial-item-2", "partial-item-3")) {
                EquipmentItem item = EquipmentItem.create(new EquipmentId(equipmentId), TYPE_ID);
                item.release();
                unit.equipmentItems().insert(item);
            }
            unit.loanRequests().insert(LoanRequest.submit(new LoanRequestId("partial-request"),
                    FIRST_MEMBER, TYPE_ID, 3, LocalDate.of(2026, 9, 20),
                    LocalDate.of(2026, 9, 28), null, CLOCK));
            return null;
        });
        SessionManager excoSession = excoSession();
        ApprovalService approvalService = new ApprovalService(database, excoSession,
                new EquipmentItemAvailabilityPolicy(), new UuidIdGenerator(), CLOCK);

        approvalService.approve(new ApprovalSelection("partial-request",
                List.of("partial-item-1", "partial-item-2")));

        List<MemberActiveLoan> memberLoans = service(database, memberSession(FIRST_MEMBER))
                .listActiveForMember();
        List<ExcoActiveLoan> excoLoans = service(database, excoSession).listActiveForExco();
        database.read(unit -> {
            LoanRequest request = unit.loanRequests().findById(new LoanRequestId("partial-request"))
                    .orElseThrow();
            assertEquals(LoanRequestStatus.APPROVED, request.status());
            assertEquals(2, request.approvedQuantity().orElseThrow());
            assertEquals(2, unit.loans().findAll().size());
            return null;
        });

        assertEquals(2, memberLoans.size());
        assertEquals(List.of("partial-item-1", "partial-item-2"),
                memberLoans.stream().map(MemberActiveLoan::equipmentId).sorted().toList());
        assertEquals(2, memberLoans.stream().map(MemberActiveLoan::loanId).distinct().count());
        assertEquals(List.of("partial-item-1", "partial-item-2"),
                excoLoans.stream().map(ExcoActiveLoan::equipmentId).sorted().toList());
        assertEquals(2, excoLoans.stream().map(ExcoActiveLoan::loanId).distinct().count());
    }

    @Test
    void listActiveForExco_rejectsMemberAndUnauthenticatedSessions(@TempDir Path temp) {
        SqliteDatabase database = database(temp);
        addLoan(database, "on-loan", "item-on-loan", LoanStatus.ON_LOAN,
                LocalDate.of(2026, 9, 25));

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, new SessionManager()).listActiveForExco());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, memberSession(FIRST_MEMBER)).listActiveForExco());
    }

    private static SqliteDatabase database(Path temp) {
        return databaseAt(temp.resolve("clubstock.db"));
    }

    private static SqliteDatabase databaseAt(Path databasePath) {
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        EquipmentType type = EquipmentType.create(TYPE_ID, new EquipmentTypeName("Rackets"));
        type.offer();
        database.write(unit -> {
            unit.members().insert(Member.create(FIRST_MEMBER, "Member One", new PasswordHash("test")));
            unit.equipmentTypes().insert(type);
            return null;
        });
        return database;
    }

    private static void addMember(SqliteDatabase database, MemberId memberId, String name) {
        database.write(unit -> {
            unit.members().insert(Member.create(memberId, name, new PasswordHash("test")));
            return null;
        });
    }

    private static void addLoan(SqliteDatabase database, String loanId, String itemId,
            LoanStatus status, LocalDate endDate) {
        addLoan(database, loanId, itemId, FIRST_MEMBER, TYPE_ID, status, endDate);
    }

    private static void addLoan(SqliteDatabase database, String loanId, String itemId,
            MemberId memberId, EquipmentTypeId typeId, LoanStatus status, LocalDate endDate) {
        database.write(unit -> {
            EquipmentItem item = item(itemId, typeId, status);
            Loan loan = Loan.restore(new LoanId(loanId), new LoanRequestId("request-" + loanId),
                    memberId, new EquipmentId(itemId), CLOCK.instant(), endDate, status,
                    status == LoanStatus.RETURN_PENDING || status == LoanStatus.COMPLETED
                            ? ReportedReturnCondition.GOOD : null);
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(approvedRequest(loanId, memberId, typeId, endDate));
            unit.loans().insert(loan);
            if (status == LoanStatus.LOST_PENDING) {
                unit.lossReports().insert(LossReport.create(loan.loanId(), "Lost item"));
            }
            return null;
        });
    }

    private static EquipmentItem item(String itemId, EquipmentTypeId typeId, LoanStatus status) {
        EquipmentAvailability availability = switch (status) {
        case ON_LOAN -> EquipmentAvailability.ON_LOAN;
        case RETURN_PENDING, LOST_PENDING -> EquipmentAvailability.UNAVAILABLE;
        case COMPLETED -> EquipmentAvailability.AVAILABLE;
        };
        boolean verificationPending = status == LoanStatus.RETURN_PENDING
                || status == LoanStatus.LOST_PENDING;
        return EquipmentItem.restore(new EquipmentId(itemId), typeId, EquipmentCondition.GOOD,
                availability, verificationPending, false, null);
    }

    private static LoanRequest approvedRequest(String loanId, MemberId memberId,
            EquipmentTypeId typeId, LocalDate endDate) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-" + loanId), memberId,
                typeId, 1, LocalDate.of(2026, 9, 20), endDate, null, CLOCK);
        request.approve(1);
        return request;
    }

    private static MemberActiveLoan findMember(List<MemberActiveLoan> loans, String loanId) {
        return loans.stream().filter(loan -> loan.loanId().equals(loanId)).findFirst().orElseThrow();
    }

    private static ExcoActiveLoan findExco(List<ExcoActiveLoan> loans, String loanId) {
        return loans.stream().filter(loan -> loan.loanId().equals(loanId)).findFirst().orElseThrow();
    }

    private static LoanQueryService service(SqliteDatabase database, SessionManager session) {
        return service(database, session, CLOCK, ZONE);
    }

    private static LoanQueryService service(SqliteDatabase database, SessionManager session,
            Clock clock, ZoneId zoneId) {
        return new LoanQueryService(database, session, clock, zoneId);
    }

    private static SessionManager excoSession() {
        return session(Principal.exco());
    }

    private static SessionManager memberSession(MemberId memberId) {
        return session(Principal.member(memberId));
    }

    private static SessionManager session(Principal principal) {
        SessionManager session = new SessionManager();
        establish(session, principal);
        return session;
    }

    private static void establish(SessionManager session, Principal principal) {
        try {
            var establish = SessionManager.class.getDeclaredMethod("establish", Principal.class);
            establish.setAccessible(true);
            establish.invoke(session, principal);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void deleteRow(Path databasePath, String table, String column, String value) {
        String sql = "DELETE FROM " + table + " WHERE " + column + " = ?";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertError(ApplicationErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(ApplicationException.class, action::run).errorCode());
    }
}
