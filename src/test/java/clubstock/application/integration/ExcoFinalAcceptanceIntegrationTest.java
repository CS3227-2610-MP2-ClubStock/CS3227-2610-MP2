package clubstock.application.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.catalog.CatalogType;
import clubstock.application.inventory.EquipmentItemSummary;
import clubstock.application.inventory.EquipmentTypeSummary;
import clubstock.application.loan.ExcoActiveLoan;
import clubstock.application.loan.MemberActiveLoan;
import clubstock.application.request.ApprovalSelection;
import clubstock.application.request.OwnRequest;
import clubstock.application.request.PendingRequestSelection;
import clubstock.application.request.RequestDraft;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

class ExcoFinalAcceptanceIntegrationTest {
    private static final String EXCO_PASSWORD = "acceptance-exco-password";
    private static final String MEMBER_A = "ACCEPT-MEMBER-A";
    private static final String MEMBER_B = "ACCEPT-MEMBER-B";
    private static final String MEMBER_PASSWORD = "acceptance-member-password";

    @Test
    void firstRunAuthenticationAndRoleGuardsUseTheSharedContext(@TempDir Path dataDirectory) {
        ApplicationContext context = ApplicationContext.create(dataDirectory);

        assertTrue(context.authentication().isExcoSetupRequired());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                context.inventoryService()::listTypes);
        context.authentication().completeExcoSetup(EXCO_PASSWORD.toCharArray(),
                EXCO_PASSWORD.toCharArray());
        assertFalse(context.authentication().isExcoSetupRequired());
        context.memberAccountService().createMember(MEMBER_A, "Acceptance Member",
                MEMBER_PASSWORD.toCharArray());

        context.authentication().logout();
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                context.memberAccountService()::listMembers);
        authenticateMember(context, MEMBER_A);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                context.inventoryService()::listItems);
        context.authentication().logout();
        assertTrue(context.authentication().currentPrincipal().isEmpty());

        authenticateExco(context);
        assertEquals(1, context.memberAccountService().listMembers().size());
    }

    @Test
    void memberAdministrationHonoursProtectedRemoval(@TempDir Path dataDirectory) {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        context.memberAccountService().createMember(MEMBER_A, "Original name",
                MEMBER_PASSWORD.toCharArray());
        context.memberAccountService().createMember(MEMBER_B, "Disposable member",
                MEMBER_PASSWORD.toCharArray());
        context.memberAccountService().renameMember(MEMBER_A, "Renamed member");
        context.memberAccountService().replacePassword(MEMBER_A,
                "replacement-member-password".toCharArray());
        EquipmentTypeId typeId = offeredType(context, "Administration balls");

        context.authentication().logout();
        context.authentication().authenticateMember(MEMBER_A,
                "replacement-member-password".toCharArray());
        submit(context, typeId, 1, currentDate(context).plusDays(1), currentDate(context).plusDays(2),
                true);

        context.authentication().logout();
        authenticateExco(context);
        assertError(ApplicationErrorCode.CONFLICT,
                () -> context.memberAccountService().deactivateMember(MEMBER_A));
        assertTrue(memberIsActive(context, MEMBER_A));
        context.memberAccountService().deactivateMember(MEMBER_B);
        assertFalse(memberIsActive(context, MEMBER_B));
        assertEquals("Renamed member", context.memberAccountService().listMembers().stream()
                .filter(member -> member.memberId().equals(MEMBER_A)).findFirst().orElseThrow().name());
    }

    @Test
    void inventoryLifecycleAndProtectionAreSharedAcrossRoles(@TempDir Path dataDirectory) {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        createMember(context, MEMBER_A);
        EquipmentTypeId typeId = offeredType(context, "Lifecycle rackets");
        addAndRelease(context, "lifecycle-racket", typeId);
        context.inventoryService().addItem("unreleased-racket", typeId.value());

        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        assertEquals(1, catalog(context, typeId).availableQuantity());
        LoanRequestId requestId = submit(context, typeId, 1, currentDate(context).minusDays(1),
                currentDate(context).plusDays(2), false);

        context.authentication().logout();
        authenticateExco(context);
        context.approvalService().approve(new ApprovalSelection(requestId.value(),
                List.of("lifecycle-racket")));
        assertEquals(EquipmentAvailability.ON_LOAN.name(),
                item(context, "lifecycle-racket").availability());
        assertError(ApplicationErrorCode.CONFLICT,
                () -> context.inventoryService().retireItem("lifecycle-racket"));

        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        assertEquals(0, catalog(context, typeId).availableQuantity());
        assertEquals("lifecycle-racket", context.loanQueryService().listActiveForMember()
                .getFirst().equipmentId());
    }

    @Test
    void explicitAllocationRejectsInvalidAndStaleSelectionsAtomically(@TempDir Path dataDirectory) {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        createMember(context, MEMBER_A);
        EquipmentTypeId requestedType = offeredType(context, "Allocation balls");
        EquipmentTypeId otherType = offeredType(context, "Allocation cones");
        addAndRelease(context, "allocation-one", requestedType);
        addAndRelease(context, "allocation-two", requestedType);
        context.inventoryService().addItem("allocation-unavailable", requestedType.value());
        addAndRelease(context, "allocation-other-type", otherType);
        addAndRelease(context, "allocation-stale", requestedType);

        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        LoanRequestId requestId = submit(context, requestedType, 1, currentDate(context),
                currentDate(context).plusDays(2), false);
        LoanRequestId rejectedRequestId = submit(context, requestedType, 1, currentDate(context),
                currentDate(context).plusDays(2), false);

        context.authentication().logout();
        authenticateExco(context);
        context.excoRequestService().rejectRequest(
                new PendingRequestSelection(rejectedRequestId.value()));
        assertEquals(LoanRequestStatus.REJECTED, requestStatus(context, rejectedRequestId));
        assertError(ApplicationErrorCode.CONFLICT, () -> context.excoRequestService().rejectRequest(
                new PendingRequestSelection(rejectedRequestId.value())));
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of())));
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-one", "allocation-one"))));
        assertError(ApplicationErrorCode.CONFLICT, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-unavailable"))));
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-other-type"))));
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-one", "allocation-two"))));
        context.inventoryService().retireItem("allocation-stale");
        assertError(ApplicationErrorCode.CONFLICT, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-stale"))));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(context, requestId));
        assertEquals(0, loanCount(context));

        context.approvalService().approve(new ApprovalSelection(requestId.value(),
                List.of("allocation-one")));
        assertEquals(LoanRequestStatus.APPROVED, requestStatus(context, requestId));
        assertEquals(EquipmentAvailability.ON_LOAN.name(),
                item(context, "allocation-one").availability());
        assertError(ApplicationErrorCode.CONFLICT, () -> context.approvalService().approve(
                new ApprovalSelection(requestId.value(), List.of("allocation-two"))));
        assertEquals(1, loanCount(context));

        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        assertEquals(LoanRequestStatus.REJECTED, context.memberRequestService().listOwnRequests()
                .stream().filter(request -> request.loanRequestId().equals(rejectedRequestId))
                .findFirst().orElseThrow().status());
    }

    @Test
    void exhaustionRejectsOnlyExistingSameTypeRequestsAndRejectsNoStockAllocation(
            @TempDir Path dataDirectory) {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        createMember(context, MEMBER_A);
        createMember(context, MEMBER_B);
        EquipmentTypeId exhaustedType = offeredType(context, "Exhaustion balls");
        EquipmentTypeId unaffectedType = offeredType(context, "Unaffected cones");
        addAndRelease(context, "last-exhaustion-ball", exhaustedType);
        addAndRelease(context, "unaffected-cone", unaffectedType);

        LoanRequestId firstRequest = submitAs(context, MEMBER_A, exhaustedType, 1, false);
        LoanRequestId competingRequest = submitAs(context, MEMBER_B, exhaustedType, 1, false);
        LoanRequestId otherTypeRequest = submitAs(context, MEMBER_B, unaffectedType, 1, false);

        context.authentication().logout();
        authenticateExco(context);
        context.approvalService().approve(new ApprovalSelection(firstRequest.value(),
                List.of("last-exhaustion-ball")));
        assertEquals(LoanRequestStatus.REJECTED, requestStatus(context, competingRequest));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(context, otherTypeRequest));

        LoanRequestId laterZeroStockRequest = submitAs(context, MEMBER_B, exhaustedType, 1, true);
        context.authentication().logout();
        authenticateExco(context);
        assertTrue(context.approvalService().listAvailableEquipmentIds(laterZeroStockRequest.value())
                .isEmpty());
        assertError(ApplicationErrorCode.VALIDATION_FAILED, () -> context.approvalService().approve(
                new ApprovalSelection(laterZeroStockRequest.value(), List.of())));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(context, laterZeroStockRequest));
        assertEquals(1, loanCount(context));
    }

    @Test
    void activeLoanViewsExposeTheSameAssignedItemAndOverdueState(@TempDir Path dataDirectory) {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        createMember(context, MEMBER_A);
        EquipmentTypeId typeId = offeredType(context, "Overdue tents");
        addAndRelease(context, "overdue-tent", typeId);
        LocalDate endDate = currentDate(context).minusDays(2);
        LoanRequestId requestId = submitAs(context, MEMBER_A, typeId, 1,
                endDate.minusDays(2), endDate, false);

        context.authentication().logout();
        authenticateExco(context);
        context.approvalService().approve(new ApprovalSelection(requestId.value(),
                List.of("overdue-tent")));
        ExcoActiveLoan excoLoan = context.loanQueryService().listActiveForExco().getFirst();
        assertTrue(excoLoan.overdue());
        assertEquals("overdue-tent", excoLoan.equipmentId());

        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        MemberActiveLoan memberLoan = context.loanQueryService().listActiveForMember().getFirst();
        assertEquals(excoLoan.loanId(), memberLoan.loanId());
        assertEquals(excoLoan.equipmentId(), memberLoan.equipmentId());
        assertTrue(memberLoan.overdue());
        assertEquals(LoanStatus.ON_LOAN, memberLoan.status());
    }

    @Test
    void returnDamageAndLossVerificationUseAuthoritativeOutcomes(@TempDir Path dataDirectory)
            throws Exception {
        ApplicationContext context = initializedExcoContext(dataDirectory);
        createMember(context, MEMBER_A);
        EquipmentTypeId typeId = offeredType(context, "Verification rackets");
        List<String> itemIds = List.of("good-racket", "damage-available-racket",
                "damage-unavailable-racket", "lost-racket");
        for (String itemId : itemIds) {
            addAndRelease(context, itemId, typeId);
        }
        LoanRequestId requestId = submitAs(context, MEMBER_A, typeId, itemIds.size(), false);

        context.authentication().logout();
        authenticateExco(context);
        context.approvalService().approve(new ApprovalSelection(requestId.value(), itemIds));
        context.authentication().logout();
        authenticateMember(context, MEMBER_A);
        Map<String, String> loanIdsByItem = context.loanQueryService().listActiveForMember().stream()
                .collect(java.util.stream.Collectors.toMap(MemberActiveLoan::equipmentId,
                        MemberActiveLoan::loanId));
        Path image = writePng(dataDirectory.resolve("damage-evidence-source.png"));
        context.memberLoanService().submitGoodReturn(loanIdsByItem.get("good-racket"));
        context.memberLoanService().submitDamagedReturn(loanIdsByItem.get("damage-available-racket"),
                "Scratched frame", image);
        context.memberLoanService().submitDamagedReturn(loanIdsByItem.get("damage-unavailable-racket"),
                "Split handle", image);
        context.memberLoanService().submitLost(loanIdsByItem.get("lost-racket"), "Missing after trip");
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                context.verificationService()::listPending);

        context.authentication().logout();
        authenticateExco(context);
        assertEquals(4, context.verificationService().listPending().size());
        assertFalse(context.verificationService().loadDamageEvidence(
                loanIdsByItem.get("damage-available-racket")).bytes().length == 0);
        context.verificationService().verifyGood(loanIdsByItem.get("good-racket"));
        context.verificationService().verifyDamaged(loanIdsByItem.get("damage-available-racket"), true);
        context.verificationService().verifyDamaged(
                loanIdsByItem.get("damage-unavailable-racket"), false);
        context.verificationService().confirmLost(loanIdsByItem.get("lost-racket"));

        assertItemState(context, "good-racket", EquipmentCondition.GOOD,
                EquipmentAvailability.AVAILABLE);
        assertItemState(context, "damage-available-racket", EquipmentCondition.DAMAGED,
                EquipmentAvailability.AVAILABLE);
        assertItemState(context, "damage-unavailable-racket", EquipmentCondition.DAMAGED,
                EquipmentAvailability.UNAVAILABLE);
        assertItemState(context, "lost-racket", EquipmentCondition.LOST,
                EquipmentAvailability.UNAVAILABLE);
        assertError(ApplicationErrorCode.CONFLICT,
                () -> context.inventoryService().releaseItem("lost-racket"));
    }

    @Test
    void restartPreservesMemberVisibleSharedOutcomes(@TempDir Path dataDirectory) {
        ApplicationContext initial = initializedExcoContext(dataDirectory);
        createMember(initial, MEMBER_A);
        EquipmentTypeId typeId = offeredType(initial, "Persistent bags");
        addAndRelease(initial, "persistent-bag", typeId);
        LoanRequestId requestId = submitAs(initial, MEMBER_A, typeId, 1, false);
        initial.authentication().logout();
        authenticateExco(initial);
        initial.approvalService().approve(new ApprovalSelection(requestId.value(),
                List.of("persistent-bag")));
        initial.authentication().logout();

        ApplicationContext reopened = ApplicationContext.create(dataDirectory);
        authenticateMember(reopened, MEMBER_A);
        OwnRequest request = reopened.memberRequestService().listOwnRequests().getFirst();
        MemberActiveLoan loan = reopened.loanQueryService().listActiveForMember().getFirst();
        assertEquals(LoanRequestStatus.APPROVED, request.status());
        assertEquals(1, request.approvedQuantity().orElseThrow());
        assertEquals("persistent-bag", loan.equipmentId());

        reopened.authentication().logout();
        authenticateExco(reopened);
        assertEquals(1, reopened.loanQueryService().listActiveForExco().size());
        assertEquals(EquipmentAvailability.ON_LOAN.name(),
                item(reopened, "persistent-bag").availability());
    }

    private static ApplicationContext initializedExcoContext(Path dataDirectory) {
        ApplicationContext context = ApplicationContext.create(dataDirectory);
        context.authentication().completeExcoSetup(EXCO_PASSWORD.toCharArray(),
                EXCO_PASSWORD.toCharArray());
        return context;
    }

    private static void createMember(ApplicationContext context, String memberId) {
        context.memberAccountService().createMember(memberId, "Member " + memberId,
                MEMBER_PASSWORD.toCharArray());
    }

    private static EquipmentTypeId offeredType(ApplicationContext context, String name) {
        context.inventoryService().createType(name);
        EquipmentTypeSummary type = context.inventoryService().listTypes().stream()
                .filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
        context.inventoryService().offerType(type.equipmentTypeId());
        return new EquipmentTypeId(type.equipmentTypeId());
    }

    private static void addAndRelease(ApplicationContext context, String itemId, EquipmentTypeId typeId) {
        context.inventoryService().addItem(itemId, typeId.value());
        context.inventoryService().releaseItem(itemId);
    }

    private static LoanRequestId submitAs(ApplicationContext context, String memberId,
            EquipmentTypeId typeId, int quantity, boolean zeroStockConfirmed) {
        return submitAs(context, memberId, typeId, quantity, currentDate(context),
                currentDate(context).plusDays(2), zeroStockConfirmed);
    }

    private static LoanRequestId submitAs(ApplicationContext context, String memberId,
            EquipmentTypeId typeId, int quantity, LocalDate startDate, LocalDate endDate,
            boolean zeroStockConfirmed) {
        context.authentication().logout();
        authenticateMember(context, memberId);
        return submit(context, typeId, quantity, startDate, endDate, zeroStockConfirmed);
    }

    private static LoanRequestId submit(ApplicationContext context, EquipmentTypeId typeId,
            int quantity, LocalDate startDate, LocalDate endDate, boolean zeroStockConfirmed) {
        return context.memberRequestService().submit(new RequestDraft(typeId, quantity, startDate,
                endDate, null), zeroStockConfirmed);
    }

    private static void authenticateExco(ApplicationContext context) {
        context.authentication().authenticateExco(EXCO_PASSWORD.toCharArray());
    }

    private static void authenticateMember(ApplicationContext context, String memberId) {
        context.authentication().authenticateMember(memberId, MEMBER_PASSWORD.toCharArray());
    }

    private static LocalDate currentDate(ApplicationContext context) {
        return LocalDate.now(context.clock().withZone(context.zoneId()));
    }

    private static CatalogType catalog(ApplicationContext context, EquipmentTypeId typeId) {
        return context.memberCatalogService().listOfferedTypes().stream()
                .filter(type -> type.equipmentTypeId().equals(typeId)).findFirst().orElseThrow();
    }

    private static EquipmentItemSummary item(ApplicationContext context, String itemId) {
        return context.inventoryService().listItems().stream()
                .filter(candidate -> candidate.equipmentId().equals(itemId)).findFirst().orElseThrow();
    }

    private static boolean memberIsActive(ApplicationContext context, String memberId) {
        return context.memberAccountService().listMembers().stream()
                .filter(member -> member.memberId().equals(memberId)).findFirst().orElseThrow().active();
    }

    private static LoanRequestStatus requestStatus(ApplicationContext context, LoanRequestId requestId) {
        return context.transactionManager().read(unit -> unit.loanRequests().findById(requestId)
                .orElseThrow().status());
    }

    private static int loanCount(ApplicationContext context) {
        return context.transactionManager().read(unit -> unit.loans().findAll().size());
    }

    private static void assertItemState(ApplicationContext context, String itemId,
            EquipmentCondition condition, EquipmentAvailability availability) {
        EquipmentItemSummary summary = item(context, itemId);
        assertEquals(condition.name(), summary.condition());
        assertEquals(availability.name(), summary.availability());
    }

    private static Path writePng(Path path) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(image, "PNG", output));
            Files.write(path, output.toByteArray());
        }
        return path;
    }

    private static void assertError(ApplicationErrorCode expected, Executable action) {
        ApplicationException exception = assertThrows(ApplicationException.class, action);
        assertEquals(expected, exception.errorCode());
    }
}
