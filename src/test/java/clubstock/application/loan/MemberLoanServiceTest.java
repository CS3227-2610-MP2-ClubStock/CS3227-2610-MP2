package clubstock.application.loan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.auth.Principal;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.ManagedDamageImageStore;
import clubstock.application.port.StagedDamageImage;
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWorkOperation;
import clubstock.application.verification.DamageEvidence;
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
import clubstock.domain.report.DamageImageReference;
import clubstock.domain.report.DamageReport;
import clubstock.domain.report.LossReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.infrastructure.file.FileDamageEvidenceStore;
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
                () -> service(fixture.database(), memberSession(OTHER_MEMBER_ID),
                        fixture.imageStore())
                        .submitLost("loan-one", "Missing item"));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), excoSession(), fixture.imageStore())
                        .submitGoodReturn("loan-one"));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), new SessionManager(), fixture.imageStore())
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

    @Test
    void damagedReturnStoresEvidenceAndChangesOnlySelectedLoan(@TempDir Path temporaryDirectory)
            throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-damaged", "item-damaged", MEMBER_ID);
        addLoan(fixture.database(), "loan-sibling", "item-sibling", MEMBER_ID);
        byte[] imageBytes = writePng(temporaryDirectory.resolve("damage.png"));

        fixture.service().submitDamagedReturn("loan-damaged", "  Cracked handle.  ",
                temporaryDirectory.resolve("damage.png"));

        assertLoanState(fixture.database(), "loan-damaged", "item-damaged",
                LoanStatus.RETURN_PENDING, ReportedReturnCondition.DAMAGED,
                EquipmentAvailability.UNAVAILABLE, EquipmentCondition.GOOD, true);
        assertLoanState(fixture.database(), "loan-sibling", "item-sibling", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        DamageReport report = fixture.database().read(unit -> unit.damageReports()
                .findByLoanId(new LoanId("loan-damaged")).orElseThrow());
        assertEquals("Cracked handle.", report.description());
        assertEquals(imageBytes.length, report.imageReference().sizeBytes());
        assertEquals("PNG", report.imageReference().format().name());
        assertArrayEquals(imageBytes, fixture.imageStore().find(report.imageReference())
                .orElseThrow().bytes());

        VerificationService verification = new VerificationService(fixture.database(), excoSession(),
                fixture.imageStore());
        PendingVerification pending = verification.listPending().getFirst();
        assertEquals("loan-damaged", pending.loanId());
        assertEquals("RETURN", pending.reportKind());
        assertEquals("Cracked handle.", pending.description());
        assertTrue(pending.hasDamageImage());
        assertArrayEquals(imageBytes, verification.loadDamageEvidence("loan-damaged").bytes());
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitDamagedReturn("loan-damaged", "Repeated",
                        temporaryDirectory.resolve("damage.png")));

        fixture.imageStore().stage(temporaryDirectory.resolve("damage.png"));
        assertEquals(1, fixture.imageStore().stagedCount());
        DamageImageReference orphan = fixture.imageStore().finalizeImage(
                fixture.imageStore().stage(temporaryDirectory.resolve("damage.png")));

        ApplicationContext restarted = ApplicationContext.create(temporaryDirectory);
        establish(restarted.sessionManager(), Principal.exco());
        assertArrayEquals(imageBytes, restarted.verificationService()
                .loadDamageEvidence("loan-damaged").bytes());
        assertTrue(restarted.managedDamageImageStore().find(report.imageReference()).isPresent());
        assertTrue(restarted.managedDamageImageStore().find(orphan).isEmpty());
        assertEquals(0, fixture.imageStore().stagedCount());
    }

    @Test
    void damagedReturnRejectsInvalidInputOwnerRoleAndStatusBeforeStaging(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-owned", "item-owned", MEMBER_ID);
        addLoan(fixture.database(), "loan-other", "item-other", OTHER_MEMBER_ID);
        byte[] imageBytes = writePng(temporaryDirectory.resolve("damage.png"));
        Path source = temporaryDirectory.resolve("damage.png");
        Path malformedImage = temporaryDirectory.resolve("bad-image.png");
        writeBytes(malformedImage, new byte[] {1, 2, 3});

        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitDamagedReturn("loan-owned", " ", source));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitDamagedReturn("loan-owned", "Damage", null));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), memberSession(OTHER_MEMBER_ID),
                        fixture.imageStore()).submitDamagedReturn("loan-owned", "Damage", source));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), excoSession(), fixture.imageStore())
                        .submitDamagedReturn("loan-owned", "Damage", source));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submitDamagedReturn("loan-owned", "Damage",
                        malformedImage));
        assertEquals(1, fixture.imageStore().stageCount());

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> service(fixture.database(), memberSession(MEMBER_ID), fixture.imageStore())
                        .submitDamagedReturn("loan-other", "Damage", source));
        fixture.service().submitGoodReturn("loan-owned");
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().submitDamagedReturn("loan-owned", "Damage", source));
        assertEquals(imageBytes.length, Files.size(source));
        assertEquals(0, fixture.imageStore().finalizedCount());
    }

    @Test
    void damagedReturnCleansStageAfterFinalizeFailureAndKeepsDatabaseUnchanged(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-finalize", "item-finalize", MEMBER_ID);
        writePng(temporaryDirectory.resolve("damage.png"));
        fixture.imageStore().failFinalize = true;

        assertThrows(ApplicationException.class, () -> fixture.service().submitDamagedReturn(
                "loan-finalize", "Damage", temporaryDirectory.resolve("damage.png")));

        assertLoanState(fixture.database(), "loan-finalize", "item-finalize", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        assertEquals(0, fixture.imageStore().stagedCount());
        assertEquals(0, fixture.imageStore().finalizedCount());
    }

    @Test
    void damagedReturnSurfacesStageCleanupFailureAndStartupRemovesAbandonedStage(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-stage-cleanup", "item-stage-cleanup", MEMBER_ID);
        writePng(temporaryDirectory.resolve("damage.png"));
        fixture.imageStore().failFinalize = true;
        fixture.imageStore().failDiscardStaged = true;

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitDamagedReturn("loan-stage-cleanup", "Damage",
                        temporaryDirectory.resolve("damage.png")));

        assertEquals(ApplicationErrorCode.IMAGE_STORAGE_FAILURE, exception.errorCode());
        assertEquals(1, fixture.imageStore().stagedCount());
        assertLoanState(fixture.database(), "loan-stage-cleanup", "item-stage-cleanup",
                LoanStatus.ON_LOAN, null, EquipmentAvailability.ON_LOAN,
                EquipmentCondition.GOOD, false);

        ApplicationContext.create(temporaryDirectory);
        assertEquals(0, fixture.imageStore().stagedCount());
    }

    @Test
    void damagedReturnStageFailureLeavesLoanUnchanged(@TempDir Path temporaryDirectory)
            throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-stage", "item-stage", MEMBER_ID);
        writePng(temporaryDirectory.resolve("damage.png"));
        fixture.imageStore().failStage = true;

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitDamagedReturn("loan-stage", "Damage",
                        temporaryDirectory.resolve("damage.png")));

        assertEquals(ApplicationErrorCode.IMAGE_STORAGE_FAILURE, exception.errorCode());
        assertLoanState(fixture.database(), "loan-stage", "item-stage", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
        assertEquals(0, fixture.imageStore().finalizedCount());
    }

    @Test
    void damagedReportInsertFailureRollsBackRecordsAndDeletesFinalizedImage(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-rollback-damage", "item-rollback-damage", MEMBER_ID);
        createFailingDamageReportTrigger(temporaryDirectory.resolve("clubstock.db"));
        writePng(temporaryDirectory.resolve("damage.png"));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitDamagedReturn("loan-rollback-damage", "Damage",
                        temporaryDirectory.resolve("damage.png")));

        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                exception.transactionOutcome().orElseThrow());
        assertLoanState(fixture.database(), "loan-rollback-damage", "item-rollback-damage",
                LoanStatus.ON_LOAN, null, EquipmentAvailability.ON_LOAN,
                EquipmentCondition.GOOD, false);
        assertEquals(0, fixture.imageStore().finalizedCount());
        assertEquals(0, fixture.imageStore().stagedCount());
    }

    @Test
    void uncertainOutcomeDeletesOnlyAfterCommittedReferenceScanProvesAbsence(
            @TempDir Path temporaryDirectory) throws Exception {
        SqliteDatabase database = createDatabase(temporaryDirectory);
        addLoan(database, "loan-unknown", "item-unknown", MEMBER_ID);
        TestDamageImageStore imageStore = new TestDamageImageStore(
                temporaryDirectory.resolve("damage-evidence"));
        TransactionManager transactions = new FailingTransactionManager(database,
                FailingTransactionManager.Mode.UNKNOWN_BEFORE_WRITE);
        MemberLoanService service = new MemberLoanService(transactions,
                memberSession(MEMBER_ID), imageStore);
        writePng(temporaryDirectory.resolve("damage.png"));

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.submitDamagedReturn("loan-unknown", "Damage",
                        temporaryDirectory.resolve("damage.png")));

        assertEquals(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN,
                exception.transactionOutcome().orElseThrow());
        assertEquals(0, imageStore.finalizedCount());
        assertLoanState(database, "loan-unknown", "item-unknown", LoanStatus.ON_LOAN, null,
                EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
    }

    @Test
    void uncertainCommittedWriteRetainsImageAndCommittedOutcomeIsReloadedAsSuccess(
            @TempDir Path temporaryDirectory) throws Exception {
        for (FailingTransactionManager.Mode mode : List.of(
                FailingTransactionManager.Mode.UNKNOWN_AFTER_WRITE,
                FailingTransactionManager.Mode.COMMITTED_AFTER_WRITE)) {
            Path taskDirectory = Files.createDirectory(temporaryDirectory.resolve(mode.name()));
            SqliteDatabase database = createDatabase(taskDirectory);
            String loanId = "loan-" + mode.name().toLowerCase();
            String itemId = "item-" + mode.name().toLowerCase();
            addLoan(database, loanId, itemId, MEMBER_ID);
            TestDamageImageStore imageStore = new TestDamageImageStore(
                    taskDirectory.resolve("damage-evidence"));
            TransactionManager transactions = new FailingTransactionManager(database, mode);
            MemberLoanService service = new MemberLoanService(transactions,
                    memberSession(MEMBER_ID), imageStore);
            Path source = taskDirectory.resolve("damage.png");
            writePng(source);

            if (mode == FailingTransactionManager.Mode.COMMITTED_AFTER_WRITE) {
                service.submitDamagedReturn(loanId, "Damage", source);
            } else {
                ApplicationException exception = assertThrows(ApplicationException.class,
                        () -> service.submitDamagedReturn(loanId, "Damage", source));
                assertEquals(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN,
                        exception.transactionOutcome().orElseThrow());
            }

            assertEquals(1, imageStore.finalizedCount());
            assertLoanState(database, loanId, itemId, LoanStatus.RETURN_PENDING,
                    ReportedReturnCondition.DAMAGED, EquipmentAvailability.UNAVAILABLE,
                    EquipmentCondition.GOOD, true);
            DamageReport report = database.read(unit -> unit.damageReports()
                    .findByLoanId(new LoanId(loanId)).orElseThrow());
            assertTrue(imageStore.find(report.imageReference()).isPresent());
        }
    }

    @Test
    void uncertainWriteRetainsImageWhenCommittedReferenceScanFails(
            @TempDir Path temporaryDirectory) throws Exception {
        SqliteDatabase database = createDatabase(temporaryDirectory);
        addLoan(database, "loan-scan-fail", "item-scan-fail", MEMBER_ID);
        TestDamageImageStore imageStore = new TestDamageImageStore(
                temporaryDirectory.resolve("damage-evidence"));
        TransactionManager transactions = new FailingTransactionManager(database,
                FailingTransactionManager.Mode.UNKNOWN_AFTER_WRITE, true);
        MemberLoanService service = new MemberLoanService(transactions,
                memberSession(MEMBER_ID), imageStore);
        Path source = temporaryDirectory.resolve("damage.png");
        writePng(source);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.submitDamagedReturn("loan-scan-fail", "Damage", source));

        assertEquals(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN,
                exception.transactionOutcome().orElseThrow());
        assertEquals(1, imageStore.finalizedCount());
    }

    @Test
    void confirmedRollbackSurfacesCleanupFailureWithoutHidingOutcome(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-cleanup", "item-cleanup", MEMBER_ID);
        createFailingDamageReportTrigger(temporaryDirectory.resolve("clubstock.db"));
        writePng(temporaryDirectory.resolve("damage.png"));
        fixture.imageStore().failDiscardFinalized = true;

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitDamagedReturn("loan-cleanup", "Damage",
                        temporaryDirectory.resolve("damage.png")));

        assertEquals(ApplicationErrorCode.IMAGE_STORAGE_FAILURE, exception.errorCode());
        assertEquals(TransactionOutcome.CONFIRMED_ROLLBACK,
                exception.transactionOutcome().orElseThrow());
        assertEquals(1, fixture.imageStore().finalizedCount());
        assertLoanState(fixture.database(), "loan-cleanup", "item-cleanup", LoanStatus.ON_LOAN,
                null, EquipmentAvailability.ON_LOAN, EquipmentCondition.GOOD, false);
    }

    @Test
    void committedDamagedReturnCleanupFailureKeepsReportAndEvidenceRecoverable(
            @TempDir Path temporaryDirectory) throws Exception {
        Fixture fixture = createFixture(temporaryDirectory);
        addLoan(fixture.database(), "loan-committed-cleanup", "item-committed-cleanup",
                MEMBER_ID);
        Path source = temporaryDirectory.resolve("damage.png");
        byte[] imageBytes = writePng(source);
        fixture.imageStore().failDiscardStaged = true;

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> fixture.service().submitDamagedReturn("loan-committed-cleanup",
                        "Damaged grip", source));

        assertEquals(ApplicationErrorCode.IMAGE_STORAGE_FAILURE, exception.errorCode());
        assertEquals(TransactionOutcome.COMMITTED,
                exception.transactionOutcome().orElseThrow());
        assertLoanState(fixture.database(), "loan-committed-cleanup", "item-committed-cleanup",
                LoanStatus.RETURN_PENDING, ReportedReturnCondition.DAMAGED,
                EquipmentAvailability.UNAVAILABLE, EquipmentCondition.GOOD, true);
        DamageReport report = fixture.database().read(unit -> unit.damageReports()
                .findByLoanId(new LoanId("loan-committed-cleanup")).orElseThrow());
        assertEquals("Damaged grip", report.description());
        assertArrayEquals(imageBytes, fixture.imageStore().find(report.imageReference())
                .orElseThrow().bytes());

        VerificationService verification = new VerificationService(fixture.database(), excoSession(),
                fixture.imageStore());
        assertEquals("loan-committed-cleanup", verification.listPending().getFirst().loanId());
        assertArrayEquals(imageBytes,
                verification.loadDamageEvidence("loan-committed-cleanup").bytes());

        fixture.imageStore().stage(source);
        assertEquals(1, fixture.imageStore().stagedCount());
        ApplicationContext restarted = ApplicationContext.create(temporaryDirectory);
        establish(restarted.sessionManager(), Principal.exco());
        assertEquals(0, fixture.imageStore().stagedCount());
        assertArrayEquals(imageBytes, restarted.verificationService()
                .loadDamageEvidence("loan-committed-cleanup").bytes());
        assertTrue(restarted.managedDamageImageStore().find(report.imageReference()).isPresent());
    }

    private static byte[] writePng(Path path) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "PNG", output));
            return writeBytes(path, output.toByteArray());
        }
    }

    private static byte[] writeBytes(Path path, byte[] bytes) throws Exception {
        Files.write(path, bytes);
        return bytes;
    }

    private static void createFailingDamageReportTrigger(Path databasePath) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_damage_report_insert BEFORE INSERT ON damage_reports"
                    + " BEGIN SELECT RAISE(ABORT, 'simulated report insert failure'); END");
        }
    }

    private static SqliteDatabase createDatabase(Path temporaryDirectory) {
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
        return database;
    }

    private static Fixture createFixture(Path temporaryDirectory) {
        SqliteDatabase database = createDatabase(temporaryDirectory);
        TestDamageImageStore imageStore = new TestDamageImageStore(
                temporaryDirectory.resolve("damage-evidence"));
        return new Fixture(database, service(database, memberSession(MEMBER_ID), imageStore),
                imageStore);
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

    private static MemberLoanService service(SqliteDatabase database, SessionManager sessions,
            ManagedDamageImageStore imageStore) {
        return new MemberLoanService(database, sessions, imageStore);
    }

    private static SessionManager memberSession(MemberId memberId) {
        return session(Principal.member(memberId));
    }

    private static SessionManager excoSession() {
        return session(Principal.exco());
    }

    private static SessionManager session(Principal principal) {
        SessionManager sessions = new SessionManager();
        establish(sessions, principal);
        return sessions;
    }

    private static void establish(SessionManager sessions, Principal principal) {
        try {
            var establish = SessionManager.class.getDeclaredMethod("establish", Principal.class);
            establish.setAccessible(true);
            establish.invoke(sessions, principal);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertError(ApplicationErrorCode expectedCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(expectedCode, exception.errorCode());
    }

    private static final class TestDamageImageStore implements ManagedDamageImageStore {
        private final Path root;
        private final FileDamageEvidenceStore delegate;
        private int stageCount;
        private boolean failStage;
        private boolean failFinalize;
        private boolean failDiscardFinalized;
        private boolean failDiscardStaged;

        private TestDamageImageStore(Path root) {
            this.root = root.toAbsolutePath().normalize();
            delegate = new FileDamageEvidenceStore(this.root);
        }

        @Override
        public StagedDamageImage stage(Path sourcePath) {
            stageCount++;
            if (failStage) {
                throw storageFailure();
            }
            return delegate.stage(sourcePath);
        }

        @Override
        public DamageImageReference finalizeImage(StagedDamageImage stagedImage) {
            if (failFinalize) {
                throw storageFailure();
            }
            return delegate.finalizeImage(stagedImage);
        }

        @Override
        public void discardStaged(StagedDamageImage stagedImage) {
            if (failDiscardStaged) {
                throw storageFailure();
            }
            delegate.discardStaged(stagedImage);
        }

        @Override
        public void discardFinalized(DamageImageReference reference) {
            if (failDiscardFinalized) {
                throw storageFailure();
            }
            delegate.discardFinalized(reference);
        }

        @Override
        public void reconcile(Collection<DamageImageReference> committedReferences) {
            delegate.reconcile(committedReferences);
        }

        @Override
        public Optional<DamageEvidence> find(DamageImageReference reference) {
            return delegate.find(reference);
        }

        private int stageCount() {
            return stageCount;
        }

        private int stagedCount() {
            return countFiles(root.resolve(".staging"));
        }

        private int finalizedCount() {
            try (var files = Files.list(root)) {
                return (int) files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".png")
                                || path.getFileName().toString().endsWith(".jpg"))
                        .count();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private static int countFiles(Path directory) {
            try (var files = Files.list(directory)) {
                return (int) files.count();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private static ApplicationException storageFailure() {
            return new ApplicationException(ApplicationErrorCode.IMAGE_STORAGE_FAILURE,
                    "Simulated damage image storage failure.", null);
        }
    }

    private static final class FailingTransactionManager implements TransactionManager {
        private final SqliteDatabase delegate;
        private final Mode mode;
        private final boolean failReferenceScan;
        private boolean writeFailed;

        private FailingTransactionManager(SqliteDatabase delegate, Mode mode) {
            this(delegate, mode, false);
        }

        private FailingTransactionManager(SqliteDatabase delegate, Mode mode,
                boolean failReferenceScan) {
            this.delegate = delegate;
            this.mode = mode;
            this.failReferenceScan = failReferenceScan;
        }

        @Override
        public <T> T read(UnitOfWorkOperation<T> operation) {
            if (writeFailed && failReferenceScan) {
                throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                        "Simulated reference scan failure.", null);
            }
            return delegate.read(operation);
        }

        @Override
        public <T> T write(UnitOfWorkOperation<T> operation) {
            writeFailed = true;
            if (mode == Mode.UNKNOWN_BEFORE_WRITE) {
                throw outcomeFailure(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN);
            }
            T result = delegate.write(operation);
            if (mode == Mode.UNKNOWN_AFTER_WRITE) {
                throw outcomeFailure(TransactionOutcome.COMMIT_OUTCOME_UNKNOWN);
            }
            if (mode == Mode.COMMITTED_AFTER_WRITE) {
                throw outcomeFailure(TransactionOutcome.COMMITTED);
            }
            return result;
        }

        private static ApplicationException outcomeFailure(TransactionOutcome outcome) {
            return new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "Simulated transaction outcome.", null, outcome);
        }

        private enum Mode {
            UNKNOWN_BEFORE_WRITE,
            UNKNOWN_AFTER_WRITE,
            COMMITTED_AFTER_WRITE
        }
    }

    private record Fixture(SqliteDatabase database, MemberLoanService service,
            TestDamageImageStore imageStore) {
    }
}
