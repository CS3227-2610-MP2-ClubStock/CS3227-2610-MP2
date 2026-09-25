package clubstock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.auth.AccountRole;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.ReportedReturnCondition;
import clubstock.domain.report.DamageImageFormat;
import clubstock.domain.report.DamageImageReference;
import clubstock.domain.report.DamageReport;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.ui.auth.UserRole;

class ApplicationContextTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndReopensAnIsolatedApplicationDatabase() {
        ApplicationContext first = ApplicationContext.create(temporaryDirectory);
        ApplicationContext second = ApplicationContext.create(temporaryDirectory);

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), first.dataDirectory());
        assertEquals(first.dataDirectory(), second.dataDirectory());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("clubstock.db")));

        char[] password = "initial-exco-password".toCharArray();
        char[] confirmation = "initial-exco-password".toCharArray();
        first.authentication().completeExcoSetup(password, confirmation);

        assertEquals(UserRole.EXCO,
                first.authentication().currentPrincipal().orElseThrow().role());
        assertEquals(AccountRole.EXCO, first.sessionManager().requireExco().role());
        assertNotNull(first.transactionManager());
        assertNotNull(first.availabilityPolicy());
        assertNotNull(first.inventoryService());
        assertNotNull(first.memberCatalogService());
        assertNotNull(first.memberRequestService());
        assertNotNull(first.excoRequestService());
        assertNotNull(first.approvalService());
        assertNotNull(first.loanQueryService());
        assertNotNull(first.memberLoanService());
        assertNotNull(first.verificationService());
        assertNotNull(first.damageEvidenceStore());
        assertFalse(second.authentication().isExcoSetupRequired());
        assertTrue(second.sessionManager().currentPrincipal().isEmpty());
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }

    @Test
    void startupRecoveryPreservesCommittedLegacyEvidenceAndRemovesOrphans() throws Exception {
        ApplicationContext first = ApplicationContext.create(temporaryDirectory);
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        Path legacyDirectory = evidenceDirectory.resolve("archive");
        Files.createDirectories(legacyDirectory);
        Path legacyImage = legacyDirectory.resolve("legacy-report.jpg");
        byte[] imageBytes = {1, 2, 3};
        Files.write(legacyImage, imageBytes);
        DamageImageReference reference = new DamageImageReference("archive/legacy-report.jpg",
                DamageImageFormat.JPEG, imageBytes.length);
        insertPendingDamageReport(first, reference);

        String orphanName = UUID.randomUUID() + ".png";
        Path orphanImage = evidenceDirectory.resolve(orphanName);
        Files.write(orphanImage, new byte[] {4});
        Path abandonedStage = evidenceDirectory.resolve(".staging/interrupted.stage");
        Files.write(abandonedStage, new byte[] {5});

        ApplicationContext reopened = ApplicationContext.create(temporaryDirectory);

        assertTrue(Files.exists(legacyImage));
        assertTrue(reopened.damageEvidenceStore().find(reference).isPresent());
        assertFalse(Files.exists(orphanImage));
        assertFalse(Files.exists(abandonedStage));
        assertNotNull(reopened.managedDamageImageStore());
    }

    @Test
    void startupReconciliationWaitsForAnInFlightImageSubmission() throws Exception {
        ApplicationContext submittingContext = ApplicationContext.create(temporaryDirectory);
        Path evidenceDirectory = temporaryDirectory.resolve("damage-evidence");
        String imageKey = UUID.randomUUID() + ".png";
        byte[] imageBytes = {4, 5, 6};
        Path finalizedImage = evidenceDirectory.resolve(imageKey);
        Files.write(finalizedImage, imageBytes);
        DamageImageReference reference = new DamageImageReference(imageKey,
                DamageImageFormat.PNG, imageBytes.length);
        CountDownLatch startupStarted = new CountDownLatch(1);
        AtomicReference<Thread> startupThread = new AtomicReference<>();
        FutureTask<ApplicationContext> startup = new FutureTask<>(() -> {
            startupThread.set(Thread.currentThread());
            startupStarted.countDown();
            return ApplicationContext.create(temporaryDirectory);
        });

        submittingContext.managedDamageImageStore().withExclusiveAccess(() -> {
            new Thread(startup, "clubstock-startup-reconciliation-test").start();
            try {
                assertTrue(startupStarted.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            awaitWaitingForImageLock(startupThread);
            insertPendingDamageReport(submittingContext, reference);
            return null;
        });

        ApplicationContext reopened = startup.get(5, TimeUnit.SECONDS);

        assertTrue(Files.isRegularFile(finalizedImage));
        assertTrue(reopened.damageEvidenceStore().find(reference).isPresent());
    }

    private static void insertPendingDamageReport(ApplicationContext context,
            DamageImageReference imageReference) {
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
        MemberId memberId = new MemberId("member-recovery");
        EquipmentTypeId typeId = new EquipmentTypeId("type-recovery");
        EquipmentId equipmentId = new EquipmentId("item-recovery");
        LoanRequestId requestId = new LoanRequestId("request-recovery");
        LoanId loanId = new LoanId("loan-recovery");
        EquipmentType type = EquipmentType.create(typeId, new EquipmentTypeName("Rackets"));
        type.offer();
        EquipmentItem item = EquipmentItem.create(equipmentId, typeId);
        item.release();
        item.allocate();
        item.holdForVerification();
        LoanRequest request = LoanRequest.submit(requestId, memberId, typeId, 1,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 1), null, clock);
        request.approve(1);
        Loan loan = Loan.start(loanId, requestId, memberId, equipmentId,
                LocalDate.of(2026, 10, 1), clock);
        loan.submitReturn(ReportedReturnCondition.DAMAGED);
        DamageReport report = DamageReport.create(loanId, imageReference, "Damaged handle");

        context.transactionManager().write(unit -> {
            unit.members().insert(Member.create(memberId, "Recovery Member",
                    new PasswordHash("test-hash")));
            unit.equipmentTypes().insert(type);
            unit.equipmentItems().insert(item);
            unit.loanRequests().insert(request);
            unit.loans().insert(loan);
            unit.damageReports().insert(report);
            return null;
        });
    }

    private static void awaitWaitingForImageLock(AtomicReference<Thread> startupThread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Thread thread;
        while ((thread = startupThread.get()) == null
                || thread.getState() != Thread.State.WAITING) {
            if (thread != null && !thread.isAlive()) {
                throw new AssertionError("Startup completed before it acquired the image lock.");
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Startup did not wait for the image operation lock.");
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }
    }
}
