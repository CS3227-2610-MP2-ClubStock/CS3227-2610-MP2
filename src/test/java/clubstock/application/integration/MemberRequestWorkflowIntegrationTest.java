package clubstock.application.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.inventory.EquipmentTypeSummary;
import clubstock.application.request.ApprovalSelection;
import clubstock.application.request.ExcoPendingRequest;
import clubstock.application.request.OwnRequest;
import clubstock.application.request.PendingRequestSelection;
import clubstock.application.request.RequestDraft;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.loan.Loan;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;

class MemberRequestWorkflowIntegrationTest {
    private static final String EXCO_PASSWORD = "workflow-exco-password";
    private static final String FIRST_MEMBER_ID = "MEMBER-A";
    private static final String FIRST_MEMBER_PASSWORD = "workflow-member-a-password";
    private static final String SECOND_MEMBER_ID = "MEMBER-B";
    private static final String SECOND_MEMBER_PASSWORD = "workflow-member-b-password";
    private static final String TYPE_NAME = "Practice balls";
    private static final LocalDate START_DATE = LocalDate.of(2026, 9, 25);
    private static final LocalDate END_DATE = LocalDate.of(2026, 10, 2);

    @Test
    void memberRequestsShareExcoDecisionsAndRemainIsolatedByOwner(@TempDir Path temporaryDirectory) {
        ApplicationContext context = ApplicationContext.create(temporaryDirectory);
        context.authentication().completeExcoSetup(EXCO_PASSWORD.toCharArray(),
                EXCO_PASSWORD.toCharArray());
        context.memberAccountService().createMember(FIRST_MEMBER_ID, "Member A",
                FIRST_MEMBER_PASSWORD.toCharArray());
        context.memberAccountService().createMember(SECOND_MEMBER_ID, "Member B",
                SECOND_MEMBER_PASSWORD.toCharArray());

        context.inventoryService().createType(TYPE_NAME);
        EquipmentTypeSummary equipmentType = context.inventoryService().listTypes().stream()
                .filter(type -> type.name().equals(TYPE_NAME)).findFirst().orElseThrow();
        context.inventoryService().offerType(equipmentType.equipmentTypeId());
        context.inventoryService().addItem("practice-ball-01", equipmentType.equipmentTypeId());
        context.inventoryService().releaseItem("practice-ball-01");
        context.inventoryService().addItem("practice-ball-02", equipmentType.equipmentTypeId());
        context.inventoryService().releaseItem("practice-ball-02");

        EquipmentTypeId equipmentTypeId = new EquipmentTypeId(equipmentType.equipmentTypeId());
        RequestDraft rejectedDraft = draft(equipmentTypeId, 1);
        context.authentication().logout();
        authenticateMember(context, FIRST_MEMBER_ID, FIRST_MEMBER_PASSWORD);
        LoanRequestId rejectedRequestId = context.memberRequestService().submit(rejectedDraft, false);

        context.authentication().logout();
        authenticateExco(context);
        ExcoPendingRequest submittedToQueue = findPendingRequest(context.excoRequestService()
                .listPendingRequests(), rejectedRequestId);
        assertEquals("Member A", submittedToQueue.memberName());
        assertEquals(2, submittedToQueue.availableQuantity(),
                "Submitting a request must not reserve available items.");
        context.excoRequestService().rejectRequest(
                new PendingRequestSelection(rejectedRequestId.value()));

        context.authentication().logout();
        authenticateMember(context, FIRST_MEMBER_ID, FIRST_MEMBER_PASSWORD);
        OwnRequest rejectedRequest = findOwnRequest(context.memberRequestService().listOwnRequests(),
                rejectedRequestId);
        assertEquals(LoanRequestStatus.REJECTED, rejectedRequest.status());
        assertEquals(TYPE_NAME, rejectedRequest.equipmentTypeName());

        LoanRequestId partialApprovalId = context.memberRequestService()
                .submit(draft(equipmentTypeId, 2), false);
        context.authentication().logout();
        authenticateExco(context);
        List<String> selectedItems = context.approvalService()
                .listAvailableEquipmentIds(partialApprovalId.value()).subList(0, 1);
        context.approvalService().approve(new ApprovalSelection(partialApprovalId.value(),
                selectedItems));

        context.transactionManager().read(unitOfWork -> {
            LoanRequest request = unitOfWork.loanRequests().findById(partialApprovalId).orElseThrow();
            List<Loan> allocatedLoans = unitOfWork.loans().findAll().stream()
                    .filter(loan -> loan.loanRequestId().equals(partialApprovalId)).toList();
            assertEquals(LoanRequestStatus.APPROVED, request.status());
            assertEquals(1, request.approvedQuantity().orElseThrow());
            assertEquals(1, allocatedLoans.size());
            assertEquals(selectedItems.getFirst(), allocatedLoans.getFirst().equipmentId().value());
            assertEquals(EquipmentAvailability.ON_LOAN, unitOfWork.equipmentItems()
                    .findById(allocatedLoans.getFirst().equipmentId()).orElseThrow().availability());
            return null;
        });

        context.authentication().logout();
        authenticateMember(context, FIRST_MEMBER_ID, FIRST_MEMBER_PASSWORD);
        List<OwnRequest> firstMemberRequests = context.memberRequestService().listOwnRequests();
        OwnRequest partialRequest = findOwnRequest(firstMemberRequests, partialApprovalId);
        assertEquals(LoanRequestStatus.APPROVED, partialRequest.status());
        assertEquals(1, partialRequest.approvedQuantity().orElseThrow());
        assertEquals(2, partialRequest.requestedQuantity());
        assertEquals(TYPE_NAME, partialRequest.equipmentTypeName());
        assertEquals(2, firstMemberRequests.size());

        context.authentication().logout();
        authenticateMember(context, SECOND_MEMBER_ID, SECOND_MEMBER_PASSWORD);
        assertTrue(context.memberRequestService().listOwnRequests().isEmpty(),
                "A different Member must not see the first Member's request history.");

        LoanRequestId exhaustionRequestId = context.memberRequestService()
                .submit(draft(equipmentTypeId, 1), false);
        context.authentication().logout();
        authenticateExco(context);
        List<String> lastAvailableItem = context.approvalService()
                .listAvailableEquipmentIds(exhaustionRequestId.value());
        assertEquals(1, lastAvailableItem.size());
        context.approvalService().approve(new ApprovalSelection(exhaustionRequestId.value(),
                lastAvailableItem));

        context.authentication().logout();
        authenticateMember(context, SECOND_MEMBER_ID, SECOND_MEMBER_PASSWORD);
        List<OwnRequest> secondMemberRequests = context.memberRequestService().listOwnRequests();
        assertEquals(1, secondMemberRequests.size());
        assertEquals(LoanRequestStatus.APPROVED, secondMemberRequests.getFirst().status());
        assertFalse(secondMemberRequests.stream().anyMatch(request -> request.loanRequestId()
                .equals(rejectedRequestId) || request.loanRequestId().equals(partialApprovalId)));

        RequestDraft zeroStockDraft = draft(equipmentTypeId, 1);
        assertEquals(0, context.memberRequestService().preview(zeroStockDraft).availableQuantity());
        ApplicationException confirmationRequired = assertThrows(ApplicationException.class,
                () -> context.memberRequestService().submit(zeroStockDraft, false));
        assertEquals(ApplicationErrorCode.ZERO_STOCK_CONFIRMATION_REQUIRED,
                confirmationRequired.errorCode());
        assertEquals(1, context.memberRequestService().listOwnRequests().size(),
                "A missing zero-stock confirmation must not insert a request.");
        LoanRequestId zeroStockRequestId = context.memberRequestService()
                .submit(zeroStockDraft, true);
        List<OwnRequest> secondMemberHistory = context.memberRequestService().listOwnRequests();
        assertEquals(2, secondMemberHistory.size());
        assertEquals(LoanRequestStatus.PENDING,
                findOwnRequest(secondMemberHistory, zeroStockRequestId).status());
        assertFalse(secondMemberHistory.stream().anyMatch(request -> request.loanRequestId()
                .equals(rejectedRequestId) || request.loanRequestId().equals(partialApprovalId)));

        context.authentication().logout();
        authenticateExco(context);
        ExcoPendingRequest zeroStockPending = findPendingRequest(
                context.excoRequestService().listPendingRequests(), zeroStockRequestId);
        assertEquals(0, zeroStockPending.availableQuantity());
        int allocatedLoanCount = context.transactionManager().read(unitOfWork ->
                unitOfWork.loans().findAll().size());
        assertEquals(2, allocatedLoanCount);
    }

    private static RequestDraft draft(EquipmentTypeId equipmentTypeId, int quantity) {
        return new RequestDraft(equipmentTypeId, quantity, START_DATE, END_DATE, null);
    }

    private static void authenticateMember(ApplicationContext context, String memberId,
            String password) {
        context.authentication().authenticateMember(memberId, password.toCharArray());
    }

    private static void authenticateExco(ApplicationContext context) {
        context.authentication().authenticateExco(EXCO_PASSWORD.toCharArray());
    }

    private static ExcoPendingRequest findPendingRequest(List<ExcoPendingRequest> requests,
            LoanRequestId requestId) {
        return requests.stream().filter(request -> request.loanRequestId().equals(requestId.value()))
                .findFirst().orElseThrow();
    }

    private static OwnRequest findOwnRequest(List<OwnRequest> requests, LoanRequestId requestId) {
        return requests.stream().filter(request -> request.loanRequestId().equals(requestId))
                .findFirst().orElseThrow();
    }
}
