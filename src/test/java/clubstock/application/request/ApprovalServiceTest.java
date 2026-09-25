package clubstock.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import clubstock.application.inventory.EquipmentItemAvailabilityPolicy;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class ApprovalServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void partialApproval_createsIndependentLoansAndClosesRequest(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = fixture(temp, 2, 3);
        ApprovalService service = service(database, excoSession());

        service.approve(new ApprovalSelection("request-main", List.of("item-1", "item-2")));

        database.read(unit -> {
            LoanRequest request = unit.loanRequests().findById(new LoanRequestId("request-main")).orElseThrow();
            assertEquals(LoanRequestStatus.APPROVED, request.status());
            assertEquals(2, request.approvedQuantity().orElseThrow());
            assertEquals(2, unit.loans().findAll().size());
            assertEquals(EquipmentAvailability.ON_LOAN, unit.equipmentItems()
                    .findById(new EquipmentId("item-1")).orElseThrow().availability());
            return null;
        });
    }

    @Test
    void invalidDuplicateAndWrongTypeSelections_leaveEverythingUnchanged(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = fixture(temp, 1, 2);
        ApprovalService service = service(database, excoSession());

        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> service.approve(
                new ApprovalSelection("request-main", List.of("item-1", "item-1"))));
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> service.approve(
                new ApprovalSelection("request-main", List.of("other-item"))));

        database.read(unit -> {
            assertEquals(LoanRequestStatus.PENDING, unit.loanRequests()
                    .findById(new LoanRequestId("request-main")).orElseThrow().status());
            assertEquals(0, unit.loans().findAll().size());
            assertEquals(EquipmentAvailability.AVAILABLE, unit.equipmentItems()
                    .findById(new EquipmentId("item-1")).orElseThrow().availability());
            return null;
        });
    }

    @Test
    void exhaustingType_rejectsOnlyOtherPendingSameTypeRequests(@TempDir java.nio.file.Path temp) {
        SqliteDatabase database = fixture(temp, 1, 1);
        ApprovalService service = service(database, excoSession());

        service.approve(new ApprovalSelection("request-main", List.of("item-1")));

        database.read(unit -> {
            assertEquals(LoanRequestStatus.REJECTED, unit.loanRequests()
                    .findById(new LoanRequestId("request-other")).orElseThrow().status());
            assertEquals(LoanRequestStatus.PENDING, unit.loanRequests()
                    .findById(new LoanRequestId("request-other-type")).orElseThrow().status());
            return null;
        });
    }

    @Test
    void staleAvailabilityAndMemberAuthorization_leaveStateUnchanged(
            @TempDir java.nio.file.Path temp) {
        SqliteDatabase database = fixture(temp, 1, 1);
        ApprovalService memberService = service(database, memberSession());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED, () -> memberService.approve(
                new ApprovalSelection("request-main", List.of("item-1"))));
        database.write(unit -> {
            EquipmentItem item = unit.equipmentItems().findById(new EquipmentId("item-1")).orElseThrow();
            item.retire(CLOCK.instant()); unit.equipmentItems().update(item); return null;
        });
        assertError(ApplicationErrorCode.CONFLICT, () -> service(database, excoSession()).approve(
                new ApprovalSelection("request-main", List.of("item-1"))));
        database.read(unit -> {
            assertEquals(LoanRequestStatus.PENDING, unit.loanRequests()
                    .findById(new LoanRequestId("request-main")).orElseThrow().status());
            assertEquals(0, unit.loans().findAll().size()); return null;
        });
    }

    private static SqliteDatabase fixture(java.nio.file.Path temp, int requestedQuantity, int items) {
        SqliteDatabase database = new SqliteDatabase(temp.resolve("clubstock.db"));
        database.initialize();
        MemberId memberId = new MemberId("member-1");
        EquipmentTypeId typeId = new EquipmentTypeId("type-1");
        EquipmentTypeId otherTypeId = new EquipmentTypeId("type-2");
        EquipmentType type = EquipmentType.create(typeId, new EquipmentTypeName("Balls")); type.offer();
        EquipmentType otherType = EquipmentType.create(otherTypeId, new EquipmentTypeName("Cones")); otherType.offer();
        database.write(unit -> {
            unit.members().insert(Member.create(memberId, "Member", new PasswordHash("test")));
            unit.equipmentTypes().insert(type); unit.equipmentTypes().insert(otherType);
            for (int index = 1; index <= items; index++) {
                EquipmentItem item = EquipmentItem.create(new EquipmentId("item-" + index), typeId);
                item.release(); unit.equipmentItems().insert(item);
            }
            EquipmentItem other = EquipmentItem.create(new EquipmentId("other-item"), otherTypeId);
            other.release(); unit.equipmentItems().insert(other);
            unit.loanRequests().insert(request("request-main", memberId, typeId, requestedQuantity));
            unit.loanRequests().insert(request("request-other", memberId, typeId, 1));
            unit.loanRequests().insert(request("request-other-type", memberId, otherTypeId, 1));
            return null;
        });
        return database;
    }

    private static LoanRequest request(String id, MemberId memberId, EquipmentTypeId typeId, int quantity) {
        return LoanRequest.submit(new LoanRequestId(id), memberId, typeId, quantity,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28), null, CLOCK);
    }

    private static ApprovalService service(SqliteDatabase database, SessionManager session) {
        return new ApprovalService(database, session, new EquipmentItemAvailabilityPolicy(),
                new SequentialIdGenerator(), CLOCK);
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
            establish.setAccessible(true); establish.invoke(session, principal);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        return session;
    }

    private static void assertError(ApplicationErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(ApplicationException.class, action::run).errorCode());
    }

    private static final class SequentialIdGenerator implements clubstock.application.port.IdGenerator {
        private int value;
        @Override public String generateId() { value++; return "loan-" + value; }
    }
}
