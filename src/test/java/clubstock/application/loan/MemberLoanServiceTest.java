package clubstock.application.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.auth.Principal;
import clubstock.application.auth.SessionManager;
import clubstock.application.verification.PendingVerification;
import clubstock.application.verification.VerificationService;
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

class MemberLoanServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"),
            ZoneOffset.UTC);
    private static final MemberId MEMBER_ID = new MemberId("member-one");
    private static final MemberId OTHER_MEMBER_ID = new MemberId("member-two");
    private static final EquipmentTypeId TYPE_ID = new EquipmentTypeId("type-one");

    @Test
    void submitGoodReturnUpdatesOnlySelectedLoanAndIsVisibleToExco(@TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-good", "item-good", MEMBER_ID);
        addLoan(fixture.database(), "loan-sibling", "item-sibling", MEMBER_ID);

        fixture.service().submitGoodReturn("loan-good");

        assertLoanState(fixture.database(), "loan-good", "item-good", LoanStatus.RETURN_PENDING,
                ReportedReturnCondition.GOOD, EquipmentAvailability.UNAVAILABLE,
                EquipmentCondition.GOOD, true);
        assertLoanState(fixture.database(), "loan-sibling", "item-sibling", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        List<PendingVerification> pending = verificationService(fixture.database(), excoSession())
                .listPending();
        assertEquals(1, pending.size());
        assertEquals("loan-good", pending.getFirst().loanId());
        assertEquals("RETURN", pending.getFirst().reportKind());
        assertEquals("GOOD", pending.getFirst().reportedCondition());
        assertFalse(pending.getFirst().hasDamageImage());
    }

    @Test
    void submitLostStoresTrimmedDescriptionAndLeavesAuthoritativeConditionUnchanged(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-lost", "item-lost", MEMBER_ID);
        addLoan(fixture.database(), "loan-sibling", "item-sibling", MEMBER_ID);

        fixture.service().submitLost("loan-lost", "  Bag strap is missing.  ");
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitLost("loan-lost", "Repeated report"));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitGoodReturn("loan-lost"));

        assertLoanState(fixture.database(), "loan-lost", "item-lost", LoanStatus.LOST_PENDING,
                null, EquipmentAvailability.UNAVAILABLE, EquipmentCondition.GOOD, true);
        assertLoanState(fixture.database(), "loan-sibling", "item-sibling", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        fixture.database().read(unit -> {
            LossReport report = unit.lossReports().findByLoanId(new LoanId("loan-lost"))
                    .orElseThrow();
            assertEquals("Bag strap is missing.", report.description());
            return null;
        });

        List<PendingVerification> pending = verificationService(fixture.database(), excoSession())
                .listPending();
        assertEquals(1, pending.size());
        assertEquals("loan-lost", pending.getFirst().loanId());
        assertEquals("LOSS", pending.getFirst().reportKind());
        assertEquals("Bag strap is missing.", pending.getFirst().description());
    }

    @Test
    void invalidDescriptionOwnerRoleAndStatusLeaveRecordsUnchanged(@TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-one", "item-one", MEMBER_ID);

        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitLost("loan-one", null));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitLost("loan-one", "   "));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), memberSession(OTHER_MEMBER_ID))
                        .submitLost("loan-one", "Missing item"));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), excoSession())
                        .submitGoodReturn("loan-one"));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), new SessionManager())
                        .submitGoodReturn("loan-one"));

        fixture.service().submitGoodReturn("loan-one");
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitGoodReturn("loan-one"));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitLost("loan-one", "Missing item"));
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> fixture.service().submitGoodReturn("missing-loan"));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitGoodReturn("  "));

        assertLoanState(fixture.database(), "loan-one", "item-one", LoanStatus.RETURN_PENDING,
                ReportedReturnCondition.GOOD, EquipmentAvailability.UNAVAILABLE,
                EquipmentCondition.GOOD, true);
        fixture.database().read(unit -> {
            assertTrue(unit.lossReports().findAll().isEmpty());
            return null;
        });
    }

    @Test
    void submitRejectsAnItemWhoseCurrentStateIsNoLongerOnLoan(@TempDir Path temporaryDirectory)
            throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-stale-item", "item-stale-item", MEMBER_ID);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + temporaryDirectory.resolve("clubstock.db"));
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE equipment_items SET availability = 'UNAVAILABLE'"
                    + " WHERE equipment_id = 'item-stale-item'");
        }

        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitGoodReturn("loan-stale-item"));
        fixture.database().read(unit -> {
            assertEquals(LoanStatus.ON_LOAN, unit.loans()
                    .findById(new LoanId("loan-stale-item")).orElseThrow().status());
            assertEquals(EquipmentAvailability.UNAVAILABLE, unit.equipmentItems()
                    .findById(new EquipmentId("item-stale-item")).orElseThrow().availability());
            return null;
        });
    }

    @Test
    void lossReportInsertFailureRollsBackLoanAndItemUpdates(@TempDir Path temporaryDirectory)
            throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-rollback", "item-rollback", MEMBER_ID);
        createFailingLossReportTrigger(temporaryDirectory.resolve("clubstock.db"));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitLost("loan-rollback", "Missing item"));

        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                exception.transactionOutcome().orElseThrow());
        assertLoanState(fixture.database(), "loan-rollback", "item-rollback", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        fixture.database().read(unit -> {
            assertTrue(unit.lossReports().findAll().isEmpty());
            return null;
        });
    }

    private static Fixture createFixture(Path temporaryDirectory) {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentType type = EquipmentType.create(TYPE_ID, new EquipmentTypeName("Rackets"));
        type.offer();
        database.write(unit -> {
            unit.members().insert(Member.create(MEMBER_ID, "Member One",
                    new PasswordHash("member-one-hash")));
            unit.members().insert(Member.create(OTHER_MEMBER_ID, "Member Two",
                    new PasswordHash("member-two-hash")));
            unit.equipmentTypes().insert(type);
            return null;
        });
        return new Fixture(database, service(database, memberSession(MEMBER_ID)));
    }

    private static void addLoan(SqliteDatabase database, String loanId, String itemId,
            MemberId memberId) {
        EquipmentId equipmentId = new EquipmentId(itemId);
        LoanId validatedLoanId = new LoanId(loanId);
        LoanRequestId requestId = new LoanRequestId("request-" + loanId);
        EquipmentItem item = EquipmentItem.create(equipmentId, TYPE_ID);
        item.release();
        item.allocate();
        LoanRequest request = LoanRequest.submit(requestId, memberId, TYPE_ID, 1,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, CLOCK);
        request.approve(1);
        Loan loan = Loan.start(validatedLoanId, requestId, memberId, equipmentId,
                LocalDate.of(2026, 10, 1), CLOCK);

        database.write(unit -> {
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(request);
            unit.loans().insert(loan);
            return null;
        });
    }

    private static void assertLoanState(SqliteDatabase database, String loanId, String itemId,
            LoanStatus expectedLoanStatus, ReportedReturnCondition expectedCondition,
            EquipmentAvailability expectedAvailability, EquipmentCondition expectedItemCondition,
            boolean isVerificationPending) {
        database.read(unit -> {
            Loan loan = unit.loans().findById(new LoanId(loanId)).orElseThrow();
            EquipmentItem item = unit.equipmentItems().findById(new EquipmentId(itemId))
                    .orElseThrow();
            assertEquals(expectedLoanStatus, loan.status());
            assertEquals(expectedCondition, loan.reportedReturnCondition().orElse(null));
            assertEquals(expectedAvailability, item.availability());
            assertEquals(expectedItemCondition, item.condition());
            assertEquals(isVerificationPending, item.isVerificationPending());
            return null;
        });
    }

    private static void createFailingLossReportTrigger(Path databasePath) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_loss_report_insert BEFORE INSERT ON loss_reports"
                    + " BEGIN SELECT RAISE(ABORT, 'simulated report insert failure'); END");
        }
    }

    private static VerificationService verificationService(SqliteDatabase database,
            SessionManager sessions) {
        return new VerificationService(database, sessions, reference -> java.util.Optional.empty());
    }

    private static MemberLoanService service(SqliteDatabase database, SessionManager sessions) {
        return new MemberLoanService(database, sessions);
    }

    private static SessionManager memberSession(MemberId memberId) {
        return session(Principal.member(memberId));
    }

    private static SessionManager excoSession() {
        return session(Principal.exco());
    }

    private static SessionManager session(Principal principal) {
        SessionManager sessions = new SessionManager();
        try {
            var establish = SessionManager.class.getDeclaredMethod("establish", Principal.class);
            establish.setAccessible(true);
            establish.invoke(sessions, principal);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        return sessions;
    }

    private static void assertError(ApplicationErrorCode expectedCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(expectedCode, exception.errorCode());
    }

    private record Fixture(SqliteDatabase database, MemberLoanService service) {
    }
}
