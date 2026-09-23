package clubstock.infrastructure.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sqlite.SQLiteConfig;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentAvailability;
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
import clubstock.domain.request.LoanRequestStatus;

class SqliteFoundationTest {

    @Test
    void initialize_emptyDirectory_createsVersionOneAndOneUnconfiguredExcoAccount(
            @TempDir Path tempDirectory) throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);

        database.initialize();

        Boolean requiresSetup = database.read(unitOfWork ->
                unitOfWork.excoAccounts().get().requiresPasswordSetup());
        assertTrue(requiresSetup);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM exco_account")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(1, version.getInt(1));
            }
        }
    }

    @Test
    void initialize_existingDatabaseWithoutExcoAccount_failsWithoutReseeding(
            @TempDir Path tempDirectory) throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        database.write(unitOfWork -> {
            var account = unitOfWork.excoAccounts().get();
            account.completeInitialPasswordSetup(new PasswordHash("configured-hash"));
            unitOfWork.excoAccounts().save(account);
            return null;
        });

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM exco_account");
        }

        ApplicationException failure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement();
                var resultSet = statement.executeQuery("SELECT COUNT(*) FROM exco_account")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

    @Test
    void repositories_roundTripLifecycleStateWithoutChangingIdentityOrTime(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();

        Instant requestedAt = Instant.parse("2026-09-22T10:15:30Z");
        Clock clock = Clock.fixed(requestedAt, ZoneOffset.UTC);
        Member member = Member.create(new MemberId("member-1"), "Member One",
                new PasswordHash("hash-1"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-1"),
                new EquipmentTypeName("Hockey Stick"));
        type.offer();
        EquipmentItem item = EquipmentItem.create(new EquipmentId("item-1"),
                type.equipmentTypeId());
        item.release();
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-1"), member.memberId(),
                type.equipmentTypeId(), 1, LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 30), "  Needed for training  ", clock);
        request.approve(1);
        item.allocate();
        Loan loan = Loan.start(new LoanId("loan-1"), request.loanRequestId(), member.memberId(),
                item.equipmentId(), request.requestedEndDate(), clock);
        loan.submitReturn(ReportedReturnCondition.DAMAGED);
        item.holdForVerification();
        DamageReport report = DamageReport.create(loan.loanId(),
                new DamageImageReference("damage/image.png", DamageImageFormat.PNG, 12),
                "  Torn grip  ");

        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.equipmentItems().insert(item);
            unitOfWork.loanRequests().insert(request);
            unitOfWork.loans().insert(loan);
            unitOfWork.damageReports().insert(report);
            return null;
        });
        database.initialize();

        Member restoredMember = database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow());
        Loan restoredLoan = database.read(unitOfWork ->
                unitOfWork.loans().findById(loan.loanId()).orElseThrow());

        assertEquals(member, restoredMember);
        assertEquals(requestedAt, restoredLoan.startedAt());
        assertEquals(ReportedReturnCondition.DAMAGED,
                restoredLoan.reportedReturnCondition().orElseThrow());
        assertEquals("Torn grip", database.read(unitOfWork ->
                unitOfWork.damageReports().findByLoanId(loan.loanId()).orElseThrow().description()));
    }

    @ParameterizedTest
    @CsvSource({
        "9999-12-31, +10000-01-01",
        "-0002-12-31, -0001-01-01",
        "-999999999-01-01, +999999999-12-31",
        "+10000-01-01, +10000-01-01"
    })
    void loanRequests_validDateRange_roundTripsAcrossRestart(String start, String end,
            @TempDir Path tempDirectory) {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = Member.create(new MemberId("member-dates"), "Date Member",
                new PasswordHash("hash-dates"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-dates"),
                new EquipmentTypeName("Date Equipment"));
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-dates"),
                member.memberId(), type.equipmentTypeId(), 1, LocalDate.parse(start),
                LocalDate.parse(end), null, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.loanRequests().insert(request);
            return null;
        });
        request.cancelBy(member.memberId());
        database.write(unitOfWork -> {
            unitOfWork.loanRequests().update(request);
            return null;
        });

        SqliteDatabase reopenedDatabase = new SqliteDatabase(databasePath);
        reopenedDatabase.initialize();
        LoanRequest restored = reopenedDatabase.read(unitOfWork ->
                unitOfWork.loanRequests().findById(request.loanRequestId()).orElseThrow());
        assertEquals(request.loanRequestId(), restored.loanRequestId());
        assertEquals(request.requestedStartDate(), restored.requestedStartDate());
        assertEquals(request.requestedEndDate(), restored.requestedEndDate());
        assertEquals(request.requestedAt(), restored.requestedAt());
        assertEquals(LoanRequestStatus.CANCELLED, restored.status());
    }

    @Test
    void loanRequests_storedEndBeforeStart_failsLoadingWithoutReplacement(
            @TempDir Path tempDirectory) throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = Member.create(new MemberId("member-dates"), "Date Member",
                new PasswordHash("hash-dates"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-dates"),
                new EquipmentTypeName("Date Equipment"));
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-dates"),
                member.memberId(), type.equipmentTypeId(), 1, LocalDate.of(10000, 1, 1),
                LocalDate.of(10000, 1, 2), null, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.loanRequests().insert(request);
            return null;
        });
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE loan_requests SET requested_end_date = '9999-12-31'");
        }

        ApplicationException readFailure = assertThrows(ApplicationException.class, () ->
                database.read(unitOfWork -> unitOfWork.loanRequests().findAll()));
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, readFailure.errorCode());
        byte[] originalBytes = Files.readAllBytes(databasePath);
        ApplicationException startupFailure = assertThrows(ApplicationException.class,
                database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, startupFailure.errorCode());
        assertArrayEquals(originalBytes, Files.readAllBytes(databasePath));
    }

    @Test
    void read_callbackCannotCommitRepositoryWrites(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = Member.create(new MemberId("member-read-only"), "Read Only",
                new PasswordHash("hash-read-only"));

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.read(unitOfWork -> {
                    unitOfWork.members().insert(member);
                    return null;
                }));

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).isPresent()));
    }

    @Test
    void equipmentTypeDelete_offeredTypeConflictsAndUnofferedTypeCanBeDeleted(
            @TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-delete"),
                new EquipmentTypeName("Skates"));
        type.offer();
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().insert(type);
            return null;
        });

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.equipmentTypes().delete(type.equipmentTypeId());
                    return null;
                }));

        assertEquals(ApplicationErrorCode.CONFLICT, failure.errorCode());
        assertEquals(Boolean.TRUE, database.read(unitOfWork ->
                unitOfWork.equipmentTypes().findById(type.equipmentTypeId()).isPresent()));

        type.unoffer();
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().update(type);
            unitOfWork.equipmentTypes().delete(type.equipmentTypeId());
            return null;
        });
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.equipmentTypes().findById(type.equipmentTypeId()).isPresent()));
    }

    @Test
    void equipmentTypeInsert_foldedDuplicateNameConflicts(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentType first = EquipmentType.create(new EquipmentTypeId("type-folded-1"),
                new EquipmentTypeName("Straße"));
        EquipmentType duplicate = EquipmentType.create(new EquipmentTypeId("type-folded-2"),
                new EquipmentTypeName("STRASSE"));
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().insert(first);
            return null;
        });

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.equipmentTypes().insert(duplicate);
                    return null;
                }));

        assertEquals(ApplicationErrorCode.CONFLICT, failure.errorCode());
        assertEquals(Integer.valueOf(1),
                database.read(unitOfWork -> unitOfWork.equipmentTypes().findAll().size()));
    }

    @Test
    void equipmentItemInsert_missingTypeConflictsWithoutPersistingItem(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentItem item = EquipmentItem.create(new EquipmentId("item-missing-type"),
                new EquipmentTypeId("type-missing"));

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.equipmentItems().insert(item);
                    return null;
                }));

        assertEquals(ApplicationErrorCode.CONFLICT, failure.errorCode());
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(item.equipmentId()).isPresent()));
    }

    @Test
    void reportRepositories_rejectDuplicateAndOppositeBranchReports(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = Member.create(new MemberId("member-report"), "Report Member",
                new PasswordHash("hash-report"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-report"),
                new EquipmentTypeName("Report Equipment"));
        LoanFixture fixture = createLoanFixture("report", FixtureState.RETURN_PENDING_DAMAGED,
                member.memberId(), type.equipmentTypeId(), Instant.parse("2026-09-22T10:15:30Z"));
        DamageReport report = fixture.damageReport();
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.equipmentItems().insert(fixture.item());
            unitOfWork.loanRequests().insert(fixture.request());
            unitOfWork.loans().insert(fixture.loan());
            unitOfWork.damageReports().insert(report);
            return null;
        });

        ApplicationException duplicateFailure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.damageReports().insert(report);
                    return null;
                }));
        ApplicationException oppositeBranchFailure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.lossReports().insert(
                            LossReport.create(fixture.loan().loanId(), "Reported missing"));
                    return null;
                }));

        assertEquals(ApplicationErrorCode.CONFLICT, duplicateFailure.errorCode());
        assertEquals(ApplicationErrorCode.CONFLICT, oppositeBranchFailure.errorCode());
        assertEquals(Boolean.TRUE, database.read(unitOfWork ->
                unitOfWork.damageReports().findByLoanId(fixture.loan().loanId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.lossReports().findByLoanId(fixture.loan().loanId()).isPresent()));
    }

    @Test
    void initialize_malformedAndReadOnlyDatabasesFailSafely(@TempDir Path tempDirectory)
            throws Exception {
        Path malformedPath = tempDirectory.resolve("malformed.db");
        Files.writeString(malformedPath, "not a SQLite database");

        ApplicationException malformedFailure = assertThrows(ApplicationException.class,
                () -> new SqliteDatabase(malformedPath).initialize());

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, malformedFailure.errorCode());

        Path readOnlyPath = tempDirectory.resolve("read-only.db");
        SqliteDatabase writableDatabase = new SqliteDatabase(readOnlyPath);
        writableDatabase.initialize();
        SqliteDatabase readOnlyDatabase = new SqliteDatabase(readOnlyPath, isWrite -> {
            SQLiteConfig config = new SQLiteConfig();
            config.setReadOnly(true);
            return DriverManager.getConnection("jdbc:sqlite:" + readOnlyPath,
                    config.toProperties());
        });

        ApplicationException readOnlyFailure = assertThrows(ApplicationException.class,
                readOnlyDatabase::initialize);

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, readOnlyFailure.errorCode());
        assertEquals(Boolean.TRUE, writableDatabase.read(unitOfWork ->
                unitOfWork.excoAccounts().get().requiresPasswordSetup()));
    }

    @Test
    void repositories_roundTripEveryLifecycleState(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Instant baseInstant = Instant.parse("2026-09-22T10:15:30Z");
        Clock clock = Clock.fixed(baseInstant, ZoneOffset.UTC);
        Member activeMember = Member.create(new MemberId("member-states-active"), "Active Member",
                new PasswordHash("hash-active"));
        Member inactiveMember = Member.create(new MemberId("member-states-inactive"),
                "Inactive Member", new PasswordHash("hash-inactive"));
        inactiveMember.deactivate(baseInstant.plusSeconds(1));
        EquipmentType offeredType = EquipmentType.create(new EquipmentTypeId("type-states-offered"),
                new EquipmentTypeName("Offered Equipment"));
        offeredType.offer();
        EquipmentType unofferedType = EquipmentType.create(
                new EquipmentTypeId("type-states-unoffered"),
                new EquipmentTypeName("Unoffered Equipment"));
        List<LoanFixture> loanFixtures = List.of(
                createLoanFixture("on-loan", FixtureState.ON_LOAN, activeMember.memberId(),
                        offeredType.equipmentTypeId(), baseInstant),
                createLoanFixture("return-good", FixtureState.RETURN_PENDING_GOOD,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(1)),
                createLoanFixture("return-damaged", FixtureState.RETURN_PENDING_DAMAGED,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(2)),
                createLoanFixture("loss-pending", FixtureState.LOST_PENDING,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(3)),
                createLoanFixture("completed-good", FixtureState.COMPLETED_GOOD,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(4)),
                createLoanFixture("completed-damaged", FixtureState.COMPLETED_DAMAGED,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(5)),
                createLoanFixture("completed-loss", FixtureState.COMPLETED_LOST,
                        activeMember.memberId(), offeredType.equipmentTypeId(),
                        baseInstant.plusSeconds(6)));
        EquipmentItem availableItem = EquipmentItem.create(new EquipmentId("item-states-available"),
                offeredType.equipmentTypeId());
        availableItem.release();
        EquipmentItem unavailableItem = EquipmentItem.create(
                new EquipmentId("item-states-unavailable"), offeredType.equipmentTypeId());
        EquipmentItem retiredItem = EquipmentItem.create(new EquipmentId("item-states-retired"),
                offeredType.equipmentTypeId());
        retiredItem.retire(baseInstant.plusSeconds(7));
        LoanRequest pendingRequest = createRequest("pending", activeMember.memberId(),
                offeredType.equipmentTypeId(), LoanRequestStatus.PENDING, clock);
        LoanRequest rejectedRequest = createRequest("rejected", activeMember.memberId(),
                offeredType.equipmentTypeId(), LoanRequestStatus.REJECTED, clock);
        LoanRequest cancelledRequest = createRequest("cancelled", activeMember.memberId(),
                offeredType.equipmentTypeId(), LoanRequestStatus.CANCELLED, clock);

        database.write(unitOfWork -> {
            unitOfWork.members().insert(activeMember);
            unitOfWork.members().insert(inactiveMember);
            unitOfWork.equipmentTypes().insert(offeredType);
            unitOfWork.equipmentTypes().insert(unofferedType);
            unitOfWork.equipmentItems().insert(availableItem);
            unitOfWork.equipmentItems().insert(unavailableItem);
            unitOfWork.equipmentItems().insert(retiredItem);
            for (LoanFixture fixture : loanFixtures) {
                unitOfWork.equipmentItems().insert(fixture.item());
                unitOfWork.loanRequests().insert(fixture.request());
                unitOfWork.loans().insert(fixture.loan());
                if (fixture.damageReport() != null) {
                    unitOfWork.damageReports().insert(fixture.damageReport());
                }
                if (fixture.lossReport() != null) {
                    unitOfWork.lossReports().insert(fixture.lossReport());
                }
            }
            unitOfWork.loanRequests().insert(pendingRequest);
            unitOfWork.loanRequests().insert(rejectedRequest);
            unitOfWork.loanRequests().insert(cancelledRequest);
            return null;
        });
        database.initialize();

        assertEquals(Boolean.TRUE, database.read(unitOfWork ->
                unitOfWork.members().findById(activeMember.memberId()).orElseThrow().isActive()));
        Member restoredInactiveMember = database.read(unitOfWork ->
                unitOfWork.members().findById(inactiveMember.memberId()).orElseThrow());
        assertEquals(false, restoredInactiveMember.isActive());
        assertEquals(inactiveMember.removedAt(), restoredInactiveMember.removedAt());
        assertEquals(Boolean.TRUE, database.read(unitOfWork -> unitOfWork.equipmentTypes()
                .findById(offeredType.equipmentTypeId()).orElseThrow().isOffered()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork -> unitOfWork.equipmentTypes()
                .findById(unofferedType.equipmentTypeId()).orElseThrow().isOffered()));
        assertEquals(EquipmentAvailability.AVAILABLE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(availableItem.equipmentId()).orElseThrow()
                        .availability()));
        assertEquals(EquipmentAvailability.UNAVAILABLE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(unavailableItem.equipmentId()).orElseThrow()
                        .availability()));
        EquipmentItem restoredRetiredItem = database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(retiredItem.equipmentId()).orElseThrow());
        assertTrue(restoredRetiredItem.isRetired());
        assertEquals(retiredItem.retiredAt(), restoredRetiredItem.retiredAt());

        for (LoanFixture fixture : loanFixtures) {
            Loan restoredLoan = database.read(unitOfWork ->
                    unitOfWork.loans().findById(fixture.loan().loanId()).orElseThrow());
            assertEquals(fixture.loan().status(), restoredLoan.status());
            assertEquals(fixture.loan().startedAt(), restoredLoan.startedAt());
            assertEquals(fixture.request().status(), database.read(unitOfWork ->
                    unitOfWork.loanRequests().findById(fixture.request().loanRequestId())
                            .orElseThrow().status()));
            EquipmentItem restoredItem = database.read(unitOfWork ->
                    unitOfWork.equipmentItems().findById(fixture.item().equipmentId()).orElseThrow());
            assertEquals(fixture.item().availability(), restoredItem.availability());
            assertEquals(fixture.item().isVerificationPending(),
                    restoredItem.isVerificationPending());
            assertEquals(fixture.item().condition(), restoredItem.condition());
            assertEquals(fixture.damageReport() != null, database.read(unitOfWork ->
                    unitOfWork.damageReports().findByLoanId(fixture.loan().loanId()).isPresent()));
            assertEquals(fixture.lossReport() != null, database.read(unitOfWork ->
                    unitOfWork.lossReports().findByLoanId(fixture.loan().loanId()).isPresent()));
        }
        assertEquals(LoanRequestStatus.PENDING, database.read(unitOfWork ->
                unitOfWork.loanRequests().findById(pendingRequest.loanRequestId()).orElseThrow()
                        .status()));
        assertEquals(LoanRequestStatus.REJECTED, database.read(unitOfWork ->
                unitOfWork.loanRequests().findById(rejectedRequest.loanRequestId()).orElseThrow()
                        .status()));
        assertEquals(LoanRequestStatus.CANCELLED, database.read(unitOfWork ->
                unitOfWork.loanRequests().findById(cancelledRequest.loanRequestId()).orElseThrow()
                        .status()));
    }

    @Test
    void write_callbackFailure_rollsBackAllEarlierWrites(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = Member.create(new MemberId("member-rollback"), "Rollback Member",
                new PasswordHash("hash-rollback"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-rollback"),
                new EquipmentTypeName("Rollback Equipment"));
        type.offer();
        LoanFixture fixture = createLoanFixture("rollback", FixtureState.RETURN_PENDING_DAMAGED,
                member.memberId(), type.equipmentTypeId(),
                Instant.parse("2026-09-22T10:15:30Z"));

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.members().insert(member);
                    unitOfWork.equipmentTypes().insert(type);
                    unitOfWork.equipmentItems().insert(fixture.item());
                    unitOfWork.loanRequests().insert(fixture.request());
                    unitOfWork.loans().insert(fixture.loan());
                    unitOfWork.damageReports().insert(fixture.damageReport());
                    throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                            "Injected failure.", null);
                }));

        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.equipmentTypes().findById(type.equipmentTypeId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(fixture.item().equipmentId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.loanRequests().findById(fixture.request().loanRequestId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.loans().findById(fixture.loan().loanId()).isPresent()));
        assertEquals(Boolean.FALSE, database.read(unitOfWork ->
                unitOfWork.damageReports().findByLoanId(fixture.loan().loanId()).isPresent()));
    }

    @Test
    void duplicateIdentity_mapsToConflictWithoutChangingOriginal(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = Member.create(new MemberId("member-duplicate"), "Original",
                new PasswordHash("hash-1"));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            return null;
        });

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.members().insert(Member.create(member.memberId(), "Replacement",
                            new PasswordHash("hash-2")));
                    return null;
                }));

        assertEquals(ApplicationErrorCode.CONFLICT, failure.errorCode());
        assertEquals("Original", database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow().name()));
    }

    @Test
    void initialize_futureSchemaVersion_failsWithoutReplacingDatabase(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();

        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 99");
        }

        ApplicationException failure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
    }

    @Test
    void commitFailureAfterDurableCommit_reportsUnknownOutcome(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = Member.create(new MemberId("member-unknown"), "Unknown Outcome",
                new PasswordHash("hash-unknown"));

        SqliteDatabase uncertainDatabase = new SqliteDatabase(databasePath, isWrite -> {
            Connection delegate = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            if (!isWrite) {
                return delegate;
            }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("commit")) {
                            delegate.commit();
                            throw new SQLException("Injected post-commit failure.");
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        });

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                uncertainDatabase.write(unitOfWork -> {
                    unitOfWork.members().insert(member);
                    return null;
                }));

        assertEquals(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN,
                failure.transactionOutcome().orElseThrow());
        Boolean present = database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).isPresent());
        assertTrue(present);
    }

    @Test
    void closeFailureAfterCommit_reportsCommittedAndPreservesWrites(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        SqliteDatabase failingDatabase = new SqliteDatabase(databasePath, isWrite -> {
            Connection delegate = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("close")) {
                            delegate.close();
                            throw new SQLException("Injected close failure.");
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        });
        Member member = Member.create(new MemberId("member-close"), "Committed Member",
                new PasswordHash("hash-close"));

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                failingDatabase.write(unitOfWork -> {
                    unitOfWork.members().insert(member);
                    return null;
                }));

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        assertEquals(TransactionOutcome.COMMITTED, failure.transactionOutcome().orElseThrow());
        assertEquals(Boolean.TRUE, database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).isPresent()));

        ApplicationException rollbackFailure = assertThrows(ApplicationException.class, () ->
                failingDatabase.write(unitOfWork -> {
                    Member changed = unitOfWork.members().findById(member.memberId()).orElseThrow();
                    changed.updateName("Rolled back name");
                    unitOfWork.members().update(changed);
                    throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                            "Injected callback failure.", null);
                }));
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                rollbackFailure.transactionOutcome().orElseThrow());
        assertEquals("Committed Member", database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow().name()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE loan_requests SET requested_quantity = 1.5",
        "UPDATE loan_requests SET requested_quantity = 4294967297",
        "UPDATE loan_requests SET requested_quantity = 'invalid'",
        "UPDATE loan_requests SET requested_quantity = 2, approved_quantity = 1.5",
        "UPDATE damage_reports SET size_bytes = 12.5"
    })
    void numericRows_invalidValues_failLoadingWithoutReplacement(String mutation,
            @TempDir Path tempDirectory) throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = Member.create(new MemberId("member-numeric"), "Numeric Member",
                new PasswordHash("hash-numeric"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-numeric"),
                new EquipmentTypeName("Numeric Equipment"));
        LoanFixture fixture = createLoanFixture("numeric", FixtureState.RETURN_PENDING_DAMAGED,
                member.memberId(), type.equipmentTypeId(), Instant.parse("2026-09-22T10:15:30Z"));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.equipmentItems().insert(fixture.item());
            unitOfWork.loanRequests().insert(fixture.request());
            unitOfWork.loans().insert(fixture.loan());
            unitOfWork.damageReports().insert(fixture.damageReport());
            return null;
        });
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(mutation);
        }

        ApplicationException readFailure = assertThrows(ApplicationException.class, () ->
                database.read(unitOfWork -> {
                    unitOfWork.loanRequests().findAll();
                    unitOfWork.damageReports().findAll();
                    return null;
                }));
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, readFailure.errorCode());
        byte[] originalBytes = Files.readAllBytes(databasePath);
        ApplicationException startupFailure = assertThrows(ApplicationException.class,
                database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, startupFailure.errorCode());
        assertArrayEquals(originalBytes, Files.readAllBytes(databasePath));
    }

    @Test
    void retire_availableItem_persistsUnavailableStateAcrossRestart(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-retire"),
                new EquipmentTypeName("Retirement Equipment"));
        EquipmentItem item = EquipmentItem.create(new EquipmentId("item-retire"), type.equipmentTypeId());
        item.release();
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().insert(type);
            unitOfWork.equipmentItems().insert(item);
            return null;
        });
        Instant retiredAt = Instant.parse("2026-09-23T10:15:30Z");
        database.write(unitOfWork -> {
            EquipmentItem stored = unitOfWork.equipmentItems().findById(item.equipmentId()).orElseThrow();
            stored.retire(retiredAt);
            unitOfWork.equipmentItems().update(stored);
            return null;
        });
        database.initialize();

        EquipmentItem restored = database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(item.equipmentId()).orElseThrow());
        assertTrue(restored.isRetired());
        assertEquals(EquipmentAvailability.UNAVAILABLE, restored.availability());
        assertEquals(retiredAt, restored.retiredAt().orElseThrow());
    }

    @Test
    void callbackFailureWithFailedRollback_reportsUnknownOutcome(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        new SqliteDatabase(databasePath).initialize();
        SqliteDatabase uncertainDatabase = new SqliteDatabase(databasePath, isWrite -> {
            Connection delegate = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("rollback")) {
                            throw new SQLException("Injected rollback failure.");
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        });
        IllegalStateException callbackFailure = new IllegalStateException("Injected callback failure.");
        Member member = Member.create(new MemberId("member-rollback-unknown"),
                "Rollback Unknown", new PasswordHash("hash-rollback-unknown"));

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                uncertainDatabase.write(unitOfWork -> {
                    unitOfWork.members().insert(member);
                    throw callbackFailure;
                }));

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        assertEquals(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN,
                failure.transactionOutcome().orElseThrow());
        assertSame(callbackFailure, failure.getCause());
    }

    @Test
    void initialize_invalidPersistedState_failsExplicitly(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA ignore_check_constraints = ON");
            statement.execute("INSERT INTO equipment_types"
                    + "(equipment_type_id, name, comparison_key, is_offered)"
                    + " VALUES ('type-invalid', 'Invalid', 'invalid', 0)");
            statement.execute("INSERT INTO equipment_items"
                    + "(equipment_id, equipment_type_id, condition, availability,"
                    + " verification_pending, is_retired, retired_at)"
                    + " VALUES ('item-invalid', 'type-invalid', 'LOST', 'AVAILABLE', 0, 0, NULL)");
        }

        ApplicationException failure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
    }

    @Test
    void completedReturnWithLostItem_rejectsCommitAndRollsBack(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        seedMemberReference(database, "RETURN_PENDING_GOOD");
        Loan originalLoan = database.read(unitOfWork -> unitOfWork.loans().findAll().getFirst());
        EquipmentItem originalItem = database.read(unitOfWork -> unitOfWork.equipmentItems()
                .findById(originalLoan.equipmentId()).orElseThrow());

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    Loan loan = unitOfWork.loans().findById(originalLoan.loanId()).orElseThrow();
                    EquipmentItem item = unitOfWork.equipmentItems()
                            .findById(loan.equipmentId()).orElseThrow();
                    loan.completeReturn();
                    item.confirmLost();
                    unitOfWork.loans().update(loan);
                    unitOfWork.equipmentItems().update(item);
                    return null;
                }));

        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        Loan restoredLoan = database.read(unitOfWork ->
                unitOfWork.loans().findById(originalLoan.loanId()).orElseThrow());
        EquipmentItem restoredItem = database.read(unitOfWork -> unitOfWork.equipmentItems()
                .findById(originalItem.equipmentId()).orElseThrow());
        assertEquals(originalLoan.status(), restoredLoan.status());
        assertEquals(originalLoan.reportedReturnCondition(), restoredLoan.reportedReturnCondition());
        assertEquals(originalItem.condition(), restoredItem.condition());
        assertEquals(originalItem.availability(), restoredItem.availability());
        assertEquals(originalItem.isVerificationPending(), restoredItem.isVerificationPending());
        database.initialize();
    }

    @Test
    void initialize_lostItemWithoutCompletedLoss_fails(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        seedMemberReference(database, "COMPLETED_GOOD");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE equipment_items SET condition = 'LOST',"
                    + " availability = 'UNAVAILABLE'");
        }

        ApplicationException failure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, failure.errorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "ON_LOAN", "RETURN_PENDING_GOOD",
        "RETURN_PENDING_DAMAGED", "LOST_PENDING"})
    void memberRemoval_unresolvedReferences_rejectsCommitAndStartup(String state,
            @TempDir Path tempDirectory) throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = seedMemberReference(database, state);
        Instant removedAt = Instant.parse("2026-09-23T00:00:00Z");

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    Member loaded = unitOfWork.members().findById(member.memberId()).orElseThrow();
                    loaded.updateName("Must roll back");
                    loaded.deactivate(removedAt);
                    unitOfWork.members().update(loaded);
                    return null;
                }));

        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        Member restored = database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow());
        assertTrue(restored.isActive());
        assertTrue(restored.removedAt().isEmpty());
        assertEquals(member.name(), restored.name());
        database.initialize();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE members SET is_active = 0,"
                    + " removed_at = '2026-09-23T00:00:00Z'");
        }
        ApplicationException startupFailure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, startupFailure.errorCode());
        assertEquals(removedAt, database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow().removedAt().orElseThrow()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REJECTED", "CANCELLED", "COMPLETED_GOOD",
        "COMPLETED_DAMAGED", "COMPLETED_LOST"})
    void memberRemoval_resolvedReferences_survivesRestart(String state,
            @TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = seedMemberReference(database, state);
        Instant removedAt = Instant.parse("2026-09-23T00:00:00Z");

        database.write(unitOfWork -> {
            Member loaded = unitOfWork.members().findById(member.memberId()).orElseThrow();
            loaded.deactivate(removedAt);
            unitOfWork.members().update(loaded);
            return null;
        });
        database.initialize();

        assertEquals(removedAt, database.read(unitOfWork ->
                unitOfWork.members().findById(member.memberId()).orElseThrow().removedAt().orElseThrow()));
        assertEquals(1, database.read(unitOfWork -> unitOfWork.loanRequests().findAll().size()).intValue());
    }

    @Test
    void loans_duplicateItemWithinRequest_rejectsCommitAndStartup(@TempDir Path tempDirectory)
            throws Exception {
        Path databasePath = tempDirectory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        Member member = seedMemberReference(database, "COMPLETED_GOOD");
        Loan original = database.read(unitOfWork -> unitOfWork.loans().findAll().getFirst());
        LoanRequest request = database.read(unitOfWork -> unitOfWork.loanRequests().findAll().getFirst());
        EquipmentItem secondItem = EquipmentItem.create(new EquipmentId("second-item"),
                request.equipmentTypeId());
        Loan secondLoan = Loan.restore(new LoanId("second-loan"), request.loanRequestId(),
                member.memberId(), secondItem.equipmentId(), original.startedAt(), original.endDate(),
                LoanStatus.COMPLETED, ReportedReturnCondition.GOOD);
        database.write(unitOfWork -> {
            unitOfWork.loanRequests().update(LoanRequest.restore(request.loanRequestId(),
                    member.memberId(), request.equipmentTypeId(), 2, request.requestedStartDate(),
                    request.requestedEndDate(), null, request.requestedAt(), LoanRequestStatus.APPROVED, 2));
            unitOfWork.equipmentItems().insert(secondItem);
            unitOfWork.loans().insert(secondLoan);
            return null;
        });
        database.initialize();

        ApplicationException failure = assertThrows(ApplicationException.class, () ->
                database.write(unitOfWork -> {
                    unitOfWork.loans().update(Loan.restore(secondLoan.loanId(), request.loanRequestId(),
                            member.memberId(), original.equipmentId(), original.startedAt(), original.endDate(),
                            LoanStatus.COMPLETED, ReportedReturnCondition.GOOD));
                    return null;
                }));
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                failure.transactionOutcome().orElseThrow());
        assertEquals(secondItem.equipmentId(), database.read(unitOfWork ->
                unitOfWork.loans().findById(secondLoan.loanId()).orElseThrow().equipmentId()));

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                var statement = connection.prepareStatement(
                        "UPDATE loans SET equipment_id = ? WHERE loan_id = ?")) {
            statement.setString(1, original.equipmentId().value());
            statement.setString(2, secondLoan.loanId().value());
            statement.executeUpdate();
        }
        ApplicationException startupFailure = assertThrows(ApplicationException.class, database::initialize);
        assertEquals(ApplicationErrorCode.PERSISTENCE_FAILURE, startupFailure.errorCode());
        assertEquals(2, database.read(unitOfWork -> unitOfWork.loans().findAll().size()).intValue());
    }

    @Test
    void loans_sameItemAcrossDifferentRequests_survivesRestart(@TempDir Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        Member member = seedMemberReference(database, "COMPLETED_GOOD");
        Loan original = database.read(unitOfWork -> unitOfWork.loans().findAll().getFirst());
        LoanRequest request = database.read(unitOfWork -> unitOfWork.loanRequests().findAll().getFirst());
        LoanRequest nextRequest = createRequest("next", member.memberId(), request.equipmentTypeId(),
                LoanRequestStatus.PENDING, Clock.fixed(original.startedAt().plusSeconds(1), ZoneOffset.UTC));
        nextRequest.approve(1);
        database.write(unitOfWork -> {
            EquipmentItem item = unitOfWork.equipmentItems().findById(original.equipmentId()).orElseThrow();
            item.allocate();
            unitOfWork.equipmentItems().update(item);
            unitOfWork.loanRequests().insert(nextRequest);
            unitOfWork.loans().insert(Loan.start(new LoanId("next-loan"), nextRequest.loanRequestId(),
                    member.memberId(), item.equipmentId(), nextRequest.requestedEndDate(),
                    Clock.fixed(original.startedAt().plusSeconds(1), ZoneOffset.UTC)));
            return null;
        });
        database.initialize();
        assertEquals(2, database.read(unitOfWork -> unitOfWork.loans().findAll().size()).intValue());
    }

    /**
     * Seeds an active Member with a request or Loan in the selected lifecycle state.
     *
     * @param database Initialized temporary database.
     * @param state Request status or Loan fixture state.
     * @return Member owning the seeded records.
     */
    private static Member seedMemberReference(SqliteDatabase database, String state) {
        Instant instant = Instant.parse("2026-09-22T00:00:00Z");
        Member member = Member.create(new MemberId("member-reference"), "Member",
                new PasswordHash("hash"));
        EquipmentType type = EquipmentType.create(new EquipmentTypeId("type-reference"),
                new EquipmentTypeName("Reference Type"));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(type);
            if (List.of("PENDING", "REJECTED", "CANCELLED").contains(state)) {
                unitOfWork.loanRequests().insert(createRequest("reference", member.memberId(),
                        type.equipmentTypeId(), LoanRequestStatus.valueOf(state),
                        Clock.fixed(instant, ZoneOffset.UTC)));
            } else {
                LoanFixture fixture = createLoanFixture("reference", FixtureState.valueOf(state),
                        member.memberId(), type.equipmentTypeId(), instant);
                unitOfWork.equipmentItems().insert(fixture.item());
                unitOfWork.loanRequests().insert(fixture.request());
                unitOfWork.loans().insert(fixture.loan());
                if (fixture.damageReport() != null) {
                    unitOfWork.damageReports().insert(fixture.damageReport());
                }
                if (fixture.lossReport() != null) {
                    unitOfWork.lossReports().insert(fixture.lossReport());
                }
            }
            return null;
        });
        return member;
    }

    /**
     * Creates a loan fixture in the requested persisted lifecycle state.
     *
     * @param suffix Unique fixture suffix.
     * @param state Target loan state.
     * @param memberId Member identity.
     * @param equipmentTypeId Equipment type identity.
     * @param startedAt Fixed request and Loan timestamp.
     * @return A valid connected request, item, Loan, and optional report set.
     */
    private static LoanFixture createLoanFixture(String suffix, FixtureState state,
            MemberId memberId, EquipmentTypeId equipmentTypeId, Instant startedAt) {
        Clock clock = Clock.fixed(startedAt, ZoneOffset.UTC);
        EquipmentItem item = EquipmentItem.create(new EquipmentId("item-" + suffix),
                equipmentTypeId);
        item.release();
        item.allocate();
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-" + suffix), memberId,
                equipmentTypeId, 1, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 30), null,
                clock);
        request.approve(1);
        Loan loan = Loan.start(new LoanId("loan-" + suffix), request.loanRequestId(), memberId,
                item.equipmentId(), request.requestedEndDate(), clock);
        DamageReport damageReport = null;
        LossReport lossReport = null;

        switch (state) {
        case ON_LOAN -> { }
        case RETURN_PENDING_GOOD -> {
            loan.submitReturn(ReportedReturnCondition.GOOD);
            item.holdForVerification();
        }
        case RETURN_PENDING_DAMAGED -> {
            loan.submitReturn(ReportedReturnCondition.DAMAGED);
            item.holdForVerification();
            damageReport = createDamageReport(loan.loanId(), suffix);
        }
        case LOST_PENDING -> {
            loan.submitLost();
            item.holdForVerification();
            lossReport = LossReport.create(loan.loanId(), "Reported lost " + suffix);
        }
        case COMPLETED_GOOD -> {
            loan.submitReturn(ReportedReturnCondition.GOOD);
            item.holdForVerification();
            item.verifyGood();
            loan.completeReturn();
        }
        case COMPLETED_DAMAGED -> {
            loan.submitReturn(ReportedReturnCondition.DAMAGED);
            item.holdForVerification();
            damageReport = createDamageReport(loan.loanId(), suffix);
            item.verifyDamaged(false);
            loan.completeReturn();
        }
        case COMPLETED_LOST -> {
            loan.submitLost();
            item.holdForVerification();
            lossReport = LossReport.create(loan.loanId(), "Reported lost " + suffix);
            item.confirmLost();
            loan.completeLoss();
        }
        default -> throw new AssertionError("Unhandled fixture state: " + state);
        }

        return new LoanFixture(request, item, loan, damageReport, lossReport);
    }

    /**
     * Creates a damage report with a unique storage key.
     *
     * @param loanId Loan identity.
     * @param suffix Unique fixture suffix.
     * @return Damage report.
     */
    private static DamageReport createDamageReport(LoanId loanId, String suffix) {
        return DamageReport.create(loanId,
                new DamageImageReference("damage/" + suffix + ".png", DamageImageFormat.PNG, 12),
                "Reported damage " + suffix);
    }

    /**
     * Creates a request in the requested non-approved state.
     *
     * @param suffix Unique request suffix.
     * @param memberId Member identity.
     * @param equipmentTypeId Equipment type identity.
     * @param status Target request status.
     * @param clock Fixed request clock.
     * @return Loan request in the requested state.
     */
    private static LoanRequest createRequest(String suffix, MemberId memberId,
            EquipmentTypeId equipmentTypeId, LoanRequestStatus status, Clock clock) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId("request-" + suffix), memberId,
                equipmentTypeId, 1, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 30), null,
                clock);
        if (status == LoanRequestStatus.REJECTED) {
            request.reject();
        } else if (status == LoanRequestStatus.CANCELLED) {
            request.cancelBy(memberId);
        } else if (status != LoanRequestStatus.PENDING) {
            throw new IllegalArgumentException("Unsupported non-approved request state.");
        }
        return request;
    }

    private enum FixtureState {
        ON_LOAN,
        RETURN_PENDING_GOOD,
        RETURN_PENDING_DAMAGED,
        LOST_PENDING,
        COMPLETED_GOOD,
        COMPLETED_DAMAGED,
        COMPLETED_LOST
    }

    private record LoanFixture(LoanRequest request, EquipmentItem item, Loan loan,
            DamageReport damageReport, LossReport lossReport) {
    }
}
