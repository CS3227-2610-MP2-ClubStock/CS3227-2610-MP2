package clubstock.application.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.IdGenerator;
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
import clubstock.domain.report.LossReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class InventoryServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void excoManagesInventoryAndPublishesSharedAvailableCounts(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        SessionManager sessionManager = signedInExco(database);
        AvailabilityPolicy availabilityPolicy = new EquipmentItemAvailabilityPolicy();
        InventoryService inventory = inventory(database, sessionManager, () -> "type-uuid-01",
                availabilityPolicy);

        inventory.createType(" Hockey stick ");

        EquipmentTypeSummary created = inventory.listTypes().getFirst();
        assertEquals("type-uuid-01", created.equipmentTypeId());
        assertEquals("Hockey stick", created.name());
        assertFalse(created.offered());
        assertEquals(0, created.availableQuantity());

        inventory.renameType("type-uuid-01", " Training hockey stick ");
        inventory.offerType("type-uuid-01");
        inventory.addItem(" item-01 ", "type-uuid-01");
        EquipmentItemSummary addedItem = inventory.listItems().getFirst();
        assertEquals("item-01", addedItem.equipmentId());
        assertEquals("Training hockey stick", addedItem.equipmentTypeName());
        assertEquals("GOOD", addedItem.condition());
        assertEquals("UNAVAILABLE", addedItem.availability());
        assertEquals(0, inventory.listTypes().getFirst().availableQuantity());

        inventory.releaseItem("item-01");

        assertEquals("AVAILABLE", inventory.listItems().getFirst().availability());
        assertEquals(1, database.read(unitOfWork -> availabilityPolicy.countAvailable(unitOfWork,
                new EquipmentTypeId("type-uuid-01"))).intValue());
        assertEquals(1, inventory.listTypes().getFirst().availableQuantity());

        inventory.retireItem("item-01");

        EquipmentItemSummary retired = inventory.listItems().getFirst();
        assertTrue(retired.retired());
        assertEquals("UNAVAILABLE", retired.availability());
        assertEquals(0, inventory.listTypes().getFirst().availableQuantity());
    }

    @Test
    void duplicateAndProtectedOperationsDoNotPartiallyChangeInventory(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        SessionManager sessionManager = signedInExco(database);
        InventoryService inventory = inventory(database, sessionManager, new SequenceIdGenerator(
                "type-01", "unused-duplicate-id", "type-02", "type-03"),
                new EquipmentItemAvailabilityPolicy());

        inventory.createType("Hockey stick");
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.createType("HOCKEY STICK"));
        assertEquals(1, inventory.listTypes().size());

        inventory.offerType("type-01");
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.deleteType("type-01"));
        assertTrue(inventory.listTypes().getFirst().offered());

        inventory.unofferType("type-01");
        inventory.addItem("item-01", "type-01");
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.addItem("item-01", "type-01"));
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.deleteType("type-01"));
        assertEquals(1, inventory.listItems().size());
        assertEquals(1, inventory.listTypes().size());

        inventory.createType("Loaned equipment");
        seedUnresolvedLoan(database, "type-02", "item-02", "member-01", "request-01");
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.retireItem("item-02"));
        assertFalse(database.read(unitOfWork -> unitOfWork.equipmentItems()
                .findById(new EquipmentId("item-02")).orElseThrow().isRetired()).booleanValue());

        inventory.createType("Lost equipment");
        database.write(unitOfWork -> {
            unitOfWork.equipmentItems().insert(EquipmentItem.restore(new EquipmentId("item-lost"),
                    new EquipmentTypeId("type-03"), EquipmentCondition.LOST,
                    EquipmentAvailability.UNAVAILABLE, false, false, null));
            unitOfWork.loanRequests().insert(LoanRequest.restore(new LoanRequestId("request-lost"),
                    new MemberId("member-01"), new EquipmentTypeId("type-03"), 1,
                    LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, CLOCK.instant(),
                    LoanRequestStatus.APPROVED, 1));
            unitOfWork.loans().insert(Loan.restore(new LoanId("loan-lost"),
                    new LoanRequestId("request-lost"), new MemberId("member-01"),
                    new EquipmentId("item-lost"), CLOCK.instant(), LocalDate.of(2026, 10, 1),
                    LoanStatus.COMPLETED, null));
            unitOfWork.lossReports().insert(LossReport.create(new LoanId("loan-lost"),
                    "Item was lost."));
            return null;
        });
        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.releaseItem("item-lost"));
        assertEquals(EquipmentAvailability.UNAVAILABLE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(new EquipmentId("item-lost"))
                        .orElseThrow().availability()));
    }

    @Test
    void authorizationAndReferencedTypeProtectionAreEnforced(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        SessionManager sessionManager = signedInExco(database);
        InventoryService inventory = inventory(database, sessionManager, () -> "type-01",
                new EquipmentItemAvailabilityPolicy());
        inventory.createType("Requestable type");
        database.write(unitOfWork -> {
            unitOfWork.members().insert(Member.create(new MemberId("member-01"), "Member",
                    new PasswordHash("test$member-password")));
            unitOfWork.loanRequests().insert(LoanRequest.restore(new LoanRequestId("request-01"),
                    new MemberId("member-01"), new EquipmentTypeId("type-01"), 1,
                    LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, CLOCK.instant(),
                    LoanRequestStatus.CANCELLED, null));
            return null;
        });

        assertError(ApplicationErrorCode.CONFLICT, () -> inventory.deleteType("type-01"));
        assertEquals(1, inventory.listTypes().size());

        sessionManager.logout();
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED, inventory::listTypes);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> inventory.addItem("item-01", "type-01"));
        assertEquals(0, database.read(unitOfWork -> unitOfWork.equipmentItems().findAll().size())
                .intValue());
    }

    private static InventoryService inventory(SqliteDatabase database, SessionManager sessionManager,
            IdGenerator idGenerator, AvailabilityPolicy availabilityPolicy) {
        return new InventoryService(database, sessionManager, idGenerator, availabilityPolicy, CLOCK);
    }

    private static SessionManager signedInExco(SqliteDatabase database) {
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database, new TestPasswordHasher(),
                sessionManager);
        authentication.completeExcoSetup("exco-password".toCharArray(),
                "exco-password".toCharArray());
        return sessionManager;
    }

    private static SqliteDatabase initializedDatabase(java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clubstock.db"));
        database.initialize();
        return database;
    }

    private static void seedUnresolvedLoan(SqliteDatabase database, String typeId, String itemId,
            String memberId, String requestId) {
        database.write(unitOfWork -> {
            unitOfWork.members().insert(Member.create(new MemberId(memberId), "Member",
                    new PasswordHash("test$member-password")));
            unitOfWork.equipmentItems().insert(EquipmentItem.restore(new EquipmentId(itemId),
                    new EquipmentTypeId(typeId), EquipmentCondition.GOOD, EquipmentAvailability.ON_LOAN,
                    false, false, null));
            unitOfWork.loanRequests().insert(LoanRequest.restore(new LoanRequestId(requestId),
                    new MemberId(memberId), new EquipmentTypeId(typeId), 1,
                    LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, CLOCK.instant(),
                    LoanRequestStatus.APPROVED, 1));
            unitOfWork.loans().insert(Loan.restore(new LoanId("loan-01"), new LoanRequestId(requestId),
                    new MemberId(memberId), new EquipmentId(itemId), CLOCK.instant(),
                    LocalDate.of(2026, 10, 1), LoanStatus.ON_LOAN, null));
            return null;
        });
    }

    private static void assertError(ApplicationErrorCode errorCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(errorCode, exception.errorCode());
    }

    private static final class SequenceIdGenerator implements IdGenerator {
        private final String[] identifiers;
        private int next;

        SequenceIdGenerator(String... identifiers) {
            this.identifiers = identifiers;
        }

        @Override
        public String generateId() {
            return identifiers[next++];
        }
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] password) {
            try {
                return new PasswordHash("test$" + new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }

        @Override
        public boolean matches(char[] password, PasswordHash passwordHash) {
            try {
                return passwordHash.encodedHash().equals("test$" + new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }
}
