package clubstock.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.EquipmentItemAvailabilityPolicy;
import clubstock.application.port.IdGenerator;
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
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class MemberRequestServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);
    private static final MemberId MEMBER_ID = new MemberId("request-member");
    private static final EquipmentTypeId OFFERED_TYPE_ID = new EquipmentTypeId("type-offered");
    private static final EquipmentTypeId UNOFFERED_TYPE_ID = new EquipmentTypeId("type-unoffered");
    private static final String MEMBER_PASSWORD = "member-password";
    private static final String HASHED_MEMBER_PASSWORD = "test$" + MEMBER_PASSWORD;

    @Test
    void preview_returnsCurrentOfferedTypeAndNormalizedDraft(@TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 2);
        fixture.signInMember();
        RequestDraft draft = draft(OFFERED_TYPE_ID, 2, "  Training practice  ");

        RequestPreview preview = fixture.service().preview(draft);

        assertEquals(OFFERED_TYPE_ID, preview.equipmentTypeId());
        assertEquals("Court Balls", preview.equipmentTypeName());
        assertEquals(2, preview.availableQuantity());
        assertEquals(draft.equipmentTypeId(), preview.draft().equipmentTypeId());
        assertEquals(draft.quantity(), preview.draft().quantity());
        assertEquals(draft.startDate(), preview.draft().startDate());
        assertEquals(draft.endDate(), preview.draft().endDate());
        assertEquals("Training practice", preview.draft().details());
        assertEquals(2, availableItemCount(fixture.database()));
        assertTrue(hasNoRequests(fixture.database()));
    }

    @Test
    void submit_withPositiveStockCreatesPendingRequestWithoutChangingInventory(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 2);
        fixture.signInMember();
        RequestDraft draft = draft(OFFERED_TYPE_ID, 2, null);

        LoanRequestId requestId = fixture.service().submit(draft, false);

        LoanRequest request = fixture.database().read(unitOfWork ->
                unitOfWork.loanRequests().findById(requestId).orElseThrow());
        assertEquals("generated-request-1", requestId.value());
        assertEquals(MEMBER_ID, request.memberId());
        assertEquals(OFFERED_TYPE_ID, request.equipmentTypeId());
        assertEquals(2, request.requestedQuantity());
        assertEquals(LocalDate.of(2020, 1, 1), request.requestedStartDate());
        assertEquals(request.requestedStartDate(), request.requestedEndDate());
        assertEquals(LoanRequestStatus.PENDING, request.status());
        assertEquals(CLOCK.instant(), request.requestedAt());
        assertTrue(request.details().isEmpty());
        assertEquals(2, availableItemCount(fixture.database()));
        assertEquals(0, loanCount(fixture.database()));
        assertEquals(1, fixture.idGenerator().generatedCount());
    }

    @Test
    void submit_atZeroStockRequiresConfirmationAndThenCreatesRequestWithoutReservation(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 0);
        fixture.signInMember();
        RequestDraft draft = draft(OFFERED_TYPE_ID, 1, "   ");

        assertError(ApplicationErrorCode.ZERO_STOCK_CONFIRMATION_REQUIRED,
                () -> fixture.service().submit(draft, false));
        assertTrue(hasNoRequests(fixture.database()));
        assertEquals(0, fixture.idGenerator().generatedCount());

        LoanRequestId requestId = fixture.service().submit(draft, true);

        LoanRequest request = fixture.database().read(unitOfWork ->
                unitOfWork.loanRequests().findById(requestId).orElseThrow());
        assertEquals(LoanRequestStatus.PENDING, request.status());
        assertTrue(request.details().isEmpty());
        assertEquals(0, availableItemCount(fixture.database()));
        assertEquals(0, loanCount(fixture.database()));
        assertEquals(1, fixture.idGenerator().generatedCount());
    }

    @Test
    void submit_recountsStockAfterPreviewAndRequiresFreshZeroStockConfirmation(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 1);
        fixture.signInMember();
        RequestDraft draft = draft(OFFERED_TYPE_ID, 1, null);

        assertEquals(1, fixture.service().preview(draft).availableQuantity());
        fixture.database().write(unitOfWork -> {
            EquipmentItem item = unitOfWork.equipmentItems()
                    .findById(new EquipmentId("available-item-1")).orElseThrow();
            item.retire(CLOCK.instant());
            unitOfWork.equipmentItems().update(item);
            return null;
        });

        assertError(ApplicationErrorCode.ZERO_STOCK_CONFIRMATION_REQUIRED,
                () -> fixture.service().submit(draft, false));

        assertTrue(hasNoRequests(fixture.database()));
        assertEquals(0, availableItemCount(fixture.database()));
        assertEquals(0, fixture.idGenerator().generatedCount());
        assertEquals(EquipmentAvailability.UNAVAILABLE, fixture.database().read(unitOfWork ->
                unitOfWork.equipmentItems().findById(new EquipmentId("available-item-1"))
                        .orElseThrow().availability()));
    }

    @Test
    void previewAndSubmit_rejectInvalidDatesQuantitiesAndMissingTypesWithoutMutation(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 1);
        fixture.signInMember();
        List<RequestDraft> invalidDrafts = List.of(
                new RequestDraft(null, 1, LocalDate.of(2020, 1, 1),
                        LocalDate.of(2020, 1, 1), null),
                new RequestDraft(OFFERED_TYPE_ID, 0, LocalDate.of(2020, 1, 1),
                        LocalDate.of(2020, 1, 1), null),
                new RequestDraft(OFFERED_TYPE_ID, -1, LocalDate.of(2020, 1, 1),
                        LocalDate.of(2020, 1, 1), null),
                new RequestDraft(OFFERED_TYPE_ID, 1, null, LocalDate.of(2020, 1, 1), null),
                new RequestDraft(OFFERED_TYPE_ID, 1, LocalDate.of(2020, 1, 1), null, null),
                new RequestDraft(OFFERED_TYPE_ID, 1, LocalDate.of(2020, 1, 2),
                        LocalDate.of(2020, 1, 1), null));

        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().preview(null));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submit(null, false));
        for (RequestDraft invalidDraft : invalidDrafts) {
            assertError(ApplicationErrorCode.VALIDATION_FAILED,
                    () -> fixture.service().preview(invalidDraft));
            assertError(ApplicationErrorCode.VALIDATION_FAILED,
                    () -> fixture.service().submit(invalidDraft, false));
        }

        RequestDraft missingType = draft(new EquipmentTypeId("type-missing"), 1, null);
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> fixture.service().preview(missingType));
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> fixture.service().submit(missingType, false));
        RequestDraft unofferedType = draft(UNOFFERED_TYPE_ID, 1, null);
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().preview(unofferedType));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().submit(unofferedType, false));

        assertTrue(hasNoRequests(fixture.database()));
        assertEquals(1, availableItemCount(fixture.database()));
        assertEquals(0, fixture.idGenerator().generatedCount());
    }

    @Test
    void previewAndSubmit_requireMemberRoleAndAnActiveAccount(@TempDir Path temporaryDirectory) {
        Fixture signedOutFixture = createFixture(temporaryDirectory.resolve("signed-out"), 1);
        RequestDraft draft = draft(OFFERED_TYPE_ID, 1, null);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> signedOutFixture.service().preview(draft));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> signedOutFixture.service().submit(draft, false));

        Fixture excoFixture = createFixture(temporaryDirectory.resolve("exco"), 1);
        excoFixture.authentication().completeExcoSetup(
                "exco-password".toCharArray(), "exco-password".toCharArray());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> excoFixture.service().preview(draft));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> excoFixture.service().submit(draft, false));

        Fixture inactiveFixture = createFixture(temporaryDirectory.resolve("inactive"), 1);
        inactiveFixture.signInMember();
        inactiveFixture.database().write(unitOfWork -> {
            Member member = unitOfWork.members().findById(MEMBER_ID).orElseThrow();
            member.deactivate(CLOCK.instant());
            unitOfWork.members().update(member);
            return null;
        });
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> inactiveFixture.service().preview(draft));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> inactiveFixture.service().submit(draft, false));
        assertTrue(hasNoRequests(inactiveFixture.database()));
        assertEquals(1, availableItemCount(inactiveFixture.database()));
    }

    @Test
    void listOwnRequests_returnsOnlyOwnRequestsWithCurrentTypeAndStableOrder(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 0);
        fixture.signInMember();
        seedRequestScenario(fixture.database());

        List<OwnRequest> requests = fixture.service().listOwnRequests();

        assertEquals(List.of("own-pending", "own-rejected", "own-approved-a",
                        "own-approved-z", "own-cancelled"),
                requests.stream().map(request -> request.loanRequestId().value()).toList());
        assertEquals(List.of(LoanRequestStatus.PENDING, LoanRequestStatus.REJECTED,
                        LoanRequestStatus.APPROVED, LoanRequestStatus.APPROVED,
                        LoanRequestStatus.CANCELLED),
                requests.stream().map(OwnRequest::status).toList());
        assertTrue(requests.stream().allMatch(request ->
                request.equipmentTypeId().equals(OFFERED_TYPE_ID)
                        && request.equipmentTypeName().equals("Renamed Court Balls")));
        assertEquals(3, requests.get(0).requestedQuantity());
        assertEquals(1, requests.get(2).approvedQuantity().orElseThrow());
        assertEquals(3, requests.get(2).requestedQuantity());
        assertEquals(1, requests.get(3).approvedQuantity().orElseThrow());
        assertTrue(requests.get(0).approvedQuantity().isEmpty());
        assertEquals(Instant.parse("2026-09-24T12:00:00Z"), requests.get(0).requestedAt());
        assertThrows(UnsupportedOperationException.class, requests::clear);
    }

    @Test
    void cancelRequest_checksOwnershipAndStateAndRemovesRequestFromExcoQueue(
            @TempDir Path temporaryDirectory) {
        Fixture fixture = createFixture(temporaryDirectory, 0);
        fixture.authentication().completeExcoSetup(
                "exco-password".toCharArray(), "exco-password".toCharArray());
        RequestScenario scenario = seedRequestScenario(fixture.database());
        ExcoRequestService excoRequestService = new ExcoRequestService(fixture.database(),
                fixture.sessionManager(), new EquipmentItemAvailabilityPolicy());
        assertEquals(List.of(scenario.ownPending().loanRequestId().value(),
                        scenario.otherPending().loanRequestId().value()),
                excoRequestService.listPendingRequests().stream()
                        .map(ExcoPendingRequest::loanRequestId).toList());
        fixture.authentication().logout();
        fixture.signInMember();
        List<String> equipmentStateBefore = equipmentState(fixture.database());
        int loanCountBefore = loanCount(fixture.database());

        fixture.service().cancelRequest(scenario.ownPending().loanRequestId());

        assertEquals(LoanRequestStatus.CANCELLED,
                requestStatus(fixture.database(), scenario.ownPending().loanRequestId()));
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> fixture.service().cancelRequest(scenario.otherApproved().loanRequestId()));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().cancelRequest(scenario.ownApprovedA().loanRequestId()));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().cancelRequest(scenario.ownRejected().loanRequestId()));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().cancelRequest(scenario.ownCancelled().loanRequestId()));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> fixture.service().cancelRequest(scenario.ownPending().loanRequestId()));
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> fixture.service().cancelRequest(new LoanRequestId("missing-request")));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> fixture.service().cancelRequest(null));

        assertEquals(LoanRequestStatus.APPROVED,
                requestStatus(fixture.database(), scenario.ownApprovedA().loanRequestId()));
        assertEquals(LoanRequestStatus.REJECTED,
                requestStatus(fixture.database(), scenario.ownRejected().loanRequestId()));
        assertEquals(LoanRequestStatus.CANCELLED,
                requestStatus(fixture.database(), scenario.ownCancelled().loanRequestId()));
        assertEquals(LoanRequestStatus.APPROVED,
                requestStatus(fixture.database(), scenario.otherApproved().loanRequestId()));
        assertEquals(equipmentStateBefore, equipmentState(fixture.database()));
        assertEquals(loanCountBefore, loanCount(fixture.database()));

        fixture.authentication().logout();
        fixture.authentication().authenticateExco("exco-password".toCharArray());
        assertEquals(List.of(scenario.otherPending().loanRequestId().value()),
                excoRequestService.listPendingRequests().stream()
                        .map(ExcoPendingRequest::loanRequestId).toList());
    }

    @Test
    void ownRequestOperations_requireMemberRoleAndActiveAccount(@TempDir Path temporaryDirectory) {
        LoanRequestId requestId = new LoanRequestId("guard-request");
        Fixture signedOutFixture = createFixture(temporaryDirectory.resolve("signed-out"), 0);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                signedOutFixture.service()::listOwnRequests);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> signedOutFixture.service().cancelRequest(requestId));

        Fixture excoFixture = createFixture(temporaryDirectory.resolve("exco"), 0);
        excoFixture.authentication().completeExcoSetup(
                "exco-password".toCharArray(), "exco-password".toCharArray());
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                excoFixture.service()::listOwnRequests);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> excoFixture.service().cancelRequest(requestId));

        Fixture inactiveFixture = createFixture(temporaryDirectory.resolve("inactive"), 0);
        inactiveFixture.signInMember();
        inactiveFixture.database().write(unitOfWork -> {
            Member member = unitOfWork.members().findById(MEMBER_ID).orElseThrow();
            member.deactivate(CLOCK.instant());
            unitOfWork.members().update(member);
            return null;
        });
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                inactiveFixture.service()::listOwnRequests);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> inactiveFixture.service().cancelRequest(requestId));
        assertTrue(hasNoRequests(inactiveFixture.database()));
    }

    private static Fixture createFixture(Path directory, int availableItemCount) {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("clubstock.db"));
        database.initialize();
        Member member = Member.create(MEMBER_ID, "Request Member",
                new PasswordHash(HASHED_MEMBER_PASSWORD));
        EquipmentType offeredType = EquipmentType.create(OFFERED_TYPE_ID,
                new EquipmentTypeName("Court Balls"));
        offeredType.offer();
        EquipmentType unofferedType = EquipmentType.create(UNOFFERED_TYPE_ID,
                new EquipmentTypeName("Training Cones"));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(member);
            unitOfWork.equipmentTypes().insert(offeredType);
            unitOfWork.equipmentTypes().insert(unofferedType);
            for (int index = 1; index <= availableItemCount; index++) {
                EquipmentItem item = EquipmentItem.create(
                        new EquipmentId("available-item-" + index), OFFERED_TYPE_ID);
                item.release();
                unitOfWork.equipmentItems().insert(item);
            }
            return null;
        });

        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        SequentialIdGenerator idGenerator = new SequentialIdGenerator();
        MemberRequestService service = new MemberRequestService(database, sessionManager,
                new EquipmentItemAvailabilityPolicy(), idGenerator, CLOCK);
        return new Fixture(database, authentication, sessionManager, idGenerator, service);
    }

    private static RequestScenario seedRequestScenario(SqliteDatabase database) {
        MemberId secondMemberId = new MemberId("request-member-second");
        Member secondMember = Member.create(secondMemberId, "Second Request Member",
                new PasswordHash("test$second-member"));
        LoanRequest ownPending = request("own-pending", MEMBER_ID, "2026-09-24T12:00:00Z",
                LoanRequestStatus.PENDING);
        LoanRequest ownRejected = request("own-rejected", MEMBER_ID, "2026-09-24T11:30:00Z",
                LoanRequestStatus.REJECTED);
        ApprovedRequest ownApprovedZ = approvedRequest("own-approved-z", MEMBER_ID,
                "2026-09-24T11:00:00Z");
        ApprovedRequest ownApprovedA = approvedRequest("own-approved-a", MEMBER_ID,
                "2026-09-24T11:00:00Z");
        LoanRequest ownCancelled = request("own-cancelled", MEMBER_ID, "2026-09-24T10:00:00Z",
                LoanRequestStatus.CANCELLED);
        LoanRequest otherPending = request("other-pending", secondMemberId,
                "2026-09-24T12:30:00Z", LoanRequestStatus.PENDING);
        ApprovedRequest otherApproved = approvedRequest("other-approved", secondMemberId,
                "2026-09-24T10:30:00Z");
        List<ApprovedRequest> approvals = List.of(ownApprovedZ, ownApprovedA, otherApproved);

        database.write(unitOfWork -> {
            unitOfWork.members().insert(secondMember);
            EquipmentType type = unitOfWork.equipmentTypes().findById(OFFERED_TYPE_ID)
                    .orElseThrow();
            type.rename(new EquipmentTypeName("Renamed Court Balls"));
            unitOfWork.equipmentTypes().update(type);
            approvals.forEach(approved -> unitOfWork.equipmentItems().insert(approved.item()));
            unitOfWork.loanRequests().insert(ownPending);
            unitOfWork.loanRequests().insert(ownRejected);
            unitOfWork.loanRequests().insert(ownApprovedZ.request());
            unitOfWork.loanRequests().insert(ownApprovedA.request());
            unitOfWork.loanRequests().insert(ownCancelled);
            unitOfWork.loanRequests().insert(otherPending);
            unitOfWork.loanRequests().insert(otherApproved.request());
            approvals.forEach(approved -> unitOfWork.loans().insert(approved.loan()));
            return null;
        });
        return new RequestScenario(ownPending, ownRejected, ownApprovedA.request(),
                ownCancelled, otherPending, otherApproved.request());
    }

    private static LoanRequest request(String requestId, MemberId memberId, String requestedAt,
            LoanRequestStatus status) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId(requestId), memberId,
                OFFERED_TYPE_ID, 3, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2), null,
                Clock.fixed(Instant.parse(requestedAt), ZoneOffset.UTC));
        if (status == LoanRequestStatus.REJECTED) {
            request.reject();
        } else if (status == LoanRequestStatus.CANCELLED) {
            request.cancelBy(memberId);
        } else if (status != LoanRequestStatus.PENDING) {
            throw new IllegalArgumentException("Unsupported request status for this test fixture.");
        }
        return request;
    }

    private static ApprovedRequest approvedRequest(String requestId, MemberId memberId,
            String requestedAt) {
        LoanRequest request = LoanRequest.submit(new LoanRequestId(requestId), memberId,
                OFFERED_TYPE_ID, 3, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2), null,
                Clock.fixed(Instant.parse(requestedAt), ZoneOffset.UTC));
        request.approve(1);
        EquipmentItem item = EquipmentItem.create(new EquipmentId("item-for-" + requestId),
                OFFERED_TYPE_ID);
        item.release();
        item.allocate();
        Loan loan = Loan.start(new LoanId("loan-for-" + requestId), request.loanRequestId(),
                memberId, item.equipmentId(), request.requestedEndDate(), CLOCK);
        return new ApprovedRequest(request, item, loan);
    }

    private static RequestDraft draft(EquipmentTypeId typeId, int quantity, String details) {
        return new RequestDraft(typeId, quantity, LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 1), details);
    }

    private static int availableItemCount(SqliteDatabase database) {
        return Math.toIntExact(database.read(unitOfWork -> unitOfWork.equipmentItems().findAll().stream()
                .filter(item -> item.availability() == EquipmentAvailability.AVAILABLE)
                .filter(item -> !item.isRetired())
                .count()));
    }

    private static boolean hasNoRequests(SqliteDatabase database) {
        return database.read(unitOfWork -> unitOfWork.loanRequests().findAll().isEmpty());
    }

    private static int loanCount(SqliteDatabase database) {
        return database.read(unitOfWork -> unitOfWork.loans().findAll().size());
    }

    private static List<String> equipmentState(SqliteDatabase database) {
        return database.read(unitOfWork -> unitOfWork.equipmentItems().findAll().stream()
                .map(item -> item.equipmentId().value() + ":" + item.availability()
                        + ":" + item.isRetired())
                .sorted()
                .toList());
    }

    private static LoanRequestStatus requestStatus(SqliteDatabase database, LoanRequestId requestId) {
        return database.read(unitOfWork -> unitOfWork.loanRequests().findById(requestId)
                .orElseThrow().status());
    }

    private static void assertError(ApplicationErrorCode expectedCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(expectedCode, exception.errorCode());
    }

    private record Fixture(SqliteDatabase database, AuthenticationService authentication,
            SessionManager sessionManager, SequentialIdGenerator idGenerator,
            MemberRequestService service) {
        private void signInMember() {
            authentication.authenticateMember(MEMBER_ID.value(), MEMBER_PASSWORD.toCharArray());
        }
    }

    private record ApprovedRequest(LoanRequest request, EquipmentItem item, Loan loan) {
    }

    private record RequestScenario(LoanRequest ownPending, LoanRequest ownRejected,
            LoanRequest ownApprovedA, LoanRequest ownCancelled, LoanRequest otherPending,
            LoanRequest otherApproved) {
    }

    private static final class SequentialIdGenerator implements IdGenerator {
        private int generatedCount;

        @Override
        public String generateId() {
            generatedCount++;
            return "generated-request-" + generatedCount;
        }

        private int generatedCount() {
            return generatedCount;
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
