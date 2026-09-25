package clubstock.application.verification;

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
import clubstock.domain.report.DamageImageFormat;
import clubstock.domain.report.DamageImageReference;
import clubstock.domain.report.DamageReport;
import clubstock.domain.report.LossReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class VerificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void listPending_exposesOnlySafePendingReturnAndLossEvidence(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addReturn(database, "return-good", "item-good", ReportedReturnCondition.GOOD, false);
        addReturn(database, "return-damaged", "item-damaged", ReportedReturnCondition.DAMAGED, true);
        addLoss(database, "loss-pending", "item-lost");
        addActiveLoan(database, "active-loan", "item-active");

        List<PendingVerification> reports = service(database, excoSession()).listPending();

        assertEquals(List.of("loss-pending", "return-damaged", "return-good"),
                reports.stream().map(PendingVerification::loanId).sorted().toList());
        PendingVerification damaged = reports.stream()
                .filter(report -> report.loanId().equals("return-damaged")).findFirst().orElseThrow();
        assertEquals("RETURN", damaged.reportKind());
        assertEquals("DAMAGED", damaged.reportedCondition());
        assertEquals("damage-return-damaged.jpg", damaged.imageReference());
        assertEquals("Damaged item return-damaged", damaged.description());
        PendingVerification lost = reports.stream()
                .filter(report -> report.loanId().equals("loss-pending")).findFirst().orElseThrow();
        assertEquals("LOSS", lost.reportKind());
        assertEquals("Lost item loss-pending", lost.description());
        assertEquals("", lost.imageReference());
    }

    @Test
    void verifiesGoodAndDamagedReturnsWithExcoAvailabilityDecision(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addReturn(database, "return-good", "item-good", ReportedReturnCondition.GOOD, false);
        addReturn(database, "return-available", "item-available", ReportedReturnCondition.DAMAGED, true);
        addReturn(database, "return-unavailable", "item-unavailable", ReportedReturnCondition.DAMAGED, true);
        VerificationService service = service(database, excoSession());

        service.verifyGood("return-good");
        service.verifyDamaged("return-available", true);
        service.verifyDamaged("return-unavailable", false);

        assertResolved(database, "return-good", "item-good", EquipmentCondition.GOOD,
                EquipmentAvailability.AVAILABLE);
        assertResolved(database, "return-available", "item-available", EquipmentCondition.DAMAGED,
                EquipmentAvailability.AVAILABLE);
        assertResolved(database, "return-unavailable", "item-unavailable",
                EquipmentCondition.DAMAGED, EquipmentAvailability.UNAVAILABLE);
    }

    @Test
    void confirmLost_completesOnlySelectedPendingLoss(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addLoss(database, "loss-one", "item-one");
        addLoss(database, "loss-two", "item-two");

        service(database, excoSession()).confirmLost("loss-one");

        assertResolved(database, "loss-one", "item-one", EquipmentCondition.LOST,
                EquipmentAvailability.UNAVAILABLE);
        database.read(unit -> {
            assertEquals(LoanStatus.LOST_PENDING, unit.loans().findById(new LoanId("loss-two"))
                    .orElseThrow().status());
            assertTrue(unit.equipmentItems().findById(new EquipmentId("item-two")).orElseThrow()
                    .isVerificationPending());
            return null;
        });
    }

    @Test
    void authorizationAndInvalidOrRepeatedResolutionLeaveStateUnchanged(
            @TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addReturn(database, "return-one", "item-one", ReportedReturnCondition.GOOD, false);

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(database, memberSession()).verifyGood("return-one"));
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> service(database, excoSession()).verifyGood("missing-loan"));
        service(database, excoSession()).verifyGood("return-one");
        assertError(ApplicationErrorCode.CONFLICT,
                () -> service(database, excoSession()).verifyGood("return-one"));

        database.read(unit -> {
            assertEquals(LoanStatus.COMPLETED, unit.loans().findById(new LoanId("return-one"))
                    .orElseThrow().status());
            assertEquals(EquipmentAvailability.AVAILABLE, unit.equipmentItems()
                    .findById(new EquipmentId("item-one")).orElseThrow().availability());
            return null;
        });
    }

    @Test
    void invalidResolutionLeavesLoanAndItemUnchanged(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = database(temp);
        addLoss(database, "loss-pending", "item-lost");

        assertError(ApplicationErrorCode.CONFLICT,
                () -> service(database, excoSession()).verifyGood("loss-pending"));

        database.read(unit -> {
            assertEquals(LoanStatus.LOST_PENDING, unit.loans()
                    .findById(new LoanId("loss-pending")).orElseThrow().status());
            var item = unit.equipmentItems().findById(new EquipmentId("item-lost")).orElseThrow();
            assertEquals(EquipmentAvailability.UNAVAILABLE, item.availability());
            assertTrue(item.isVerificationPending());
            return null;
        });
    }

    private static SqliteDatabase database(java.nio.file.Path temp) {
        SqliteDatabase database = new SqliteDatabase(temp.resolve("clubstock.db"));
        database.initialize();
        EquipmentTypeId typeId = new EquipmentTypeId("type-1");
        EquipmentType type = EquipmentType.create(typeId, new EquipmentTypeName("Rackets"));
        type.offer();
        database.write(unit -> {
            unit.members().insert(Member.create(new MemberId("member-1"), "Member One",
                    new PasswordHash("test")));
            unit.equipmentTypes().insert(type);
            return null;
        });
        return database;
    }

    private static void addReturn(SqliteDatabase database, String loanId, String itemId,
            ReportedReturnCondition condition, boolean addDamageReport) {
        database.write(unit -> {
            EquipmentItem item = EquipmentItem.create(new EquipmentId(itemId),
                    new EquipmentTypeId("type-1"));
            item.release();
            item.allocate();
            Loan loan = loan(loanId, itemId);
            loan.submitReturn(condition);
            item.holdForVerification();
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(approvedRequest(loanId));
            unit.loans().insert(loan);
            if (addDamageReport) {
                unit.damageReports().insert(DamageReport.create(loan.loanId(),
                        new DamageImageReference("damage-" + loanId + ".jpg",
                                DamageImageFormat.JPEG, 1),
                        "Damaged item " + loanId));
            }
            return null;
        });
    }

    private static void addLoss(SqliteDatabase database, String loanId, String itemId) {
        database.write(unit -> {
            EquipmentItem item = EquipmentItem.create(new EquipmentId(itemId),
                    new EquipmentTypeId("type-1"));
            item.release();
            item.allocate();
            Loan loan = loan(loanId, itemId);
            loan.submitLost();
            item.holdForVerification();
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(approvedRequest(loanId));
            unit.loans().insert(loan);
            unit.lossReports().insert(LossReport.create(loan.loanId(), "Lost item " + loanId));
            return null;
        });
    }

    private static void addActiveLoan(SqliteDatabase database, String loanId, String itemId) {
        database.write(unit -> {
            EquipmentItem item = EquipmentItem.create(new EquipmentId(itemId),
                    new EquipmentTypeId("type-1"));
            item.release();
            item.allocate();
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(approvedRequest(loanId));
            unit.loans().insert(loan(loanId, itemId));
            return null;
        });
    }

    private static Loan loan(String loanId, String itemId) {
        return Loan.start(new LoanId(loanId), new LoanRequestId("request-" + loanId),
                new MemberId("member-1"), new EquipmentId(itemId), LocalDate.of(2026, 10, 1), CLOCK);
    }

    private static LoanRequest approvedRequest(String loanId) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-" + loanId),
                new MemberId("member-1"), new EquipmentTypeId("type-1"), 1,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, CLOCK);
        request.approve(1);
        return request;
    }

    private static VerificationService service(SqliteDatabase database, SessionManager session) {
        return new VerificationService(database, session);
    }

    private static void assertResolved(SqliteDatabase database, String loanId, String itemId,
            EquipmentCondition condition, EquipmentAvailability availability) {
        database.read(unit -> {
            assertEquals(LoanStatus.COMPLETED, unit.loans().findById(new LoanId(loanId))
                    .orElseThrow().status());
            EquipmentItem item = unit.equipmentItems().findById(new EquipmentId(itemId)).orElseThrow();
            assertEquals(condition, item.condition());
            assertEquals(availability, item.availability());
            assertFalse(item.isVerificationPending());
            return null;
        });
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

    private static void assertError(ApplicationErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(ApplicationException.class, action::run).errorCode());
    }
}
