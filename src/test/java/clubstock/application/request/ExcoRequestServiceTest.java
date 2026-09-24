package clubstock.application.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import clubstock.application.port.TransactionManager;
import clubstock.application.port.UnitOfWorkOperation;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentId;
import clubstock.domain.equipment.EquipmentAvailability;
import clubstock.domain.equipment.EquipmentCondition;
import clubstock.domain.equipment.EquipmentItem;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.domain.loan.Loan;
import clubstock.domain.loan.LoanId;
import clubstock.domain.loan.LoanStatus;
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class ExcoRequestServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);
    private static final String MEMBER_PASSWORD = "member-password";

    @Test
    void pendingQueue_isCompleteDeterministicAndRecalculatesAvailability(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        seedRequests(database);
        ExcoRequestService service = service(database, signedInExco(database));

        List<ExcoPendingRequest> queue = service.listPendingRequests();

        assertEquals(List.of("request-early", "request-alpha", "request-beta"),
                queue.stream().map(ExcoPendingRequest::loanRequestId).toList());
        ExcoPendingRequest first = queue.getFirst();
        assertEquals("member-01", first.memberId());
        assertEquals("Ada Member", first.memberName());
        assertEquals("type-balls", first.equipmentTypeId());
        assertEquals("Court Balls", first.equipmentTypeName());
        assertEquals(2, first.requestedQuantity());
        assertEquals(1, first.availableQuantity());
        assertEquals(LocalDate.of(2026, 9, 25), first.requestedStartDate());
        assertEquals(LocalDate.of(2026, 9, 28), first.requestedEndDate());
        assertEquals(Instant.parse("2026-09-24T09:00:00Z"), first.requestedAt());
        assertEquals("Tournament practice", first.details().orElseThrow());
        assertEquals(0, queue.get(1).availableQuantity());
        assertTrue(queue.get(2).details().isEmpty());
    }

    @Test
    void rejectPendingRequest_changesOnlyTheSelectedSharedRequest(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        seedRequests(database);
        ExcoRequestService service = service(database, signedInExco(database));

        service.rejectRequest(new PendingRequestSelection("request-alpha"));

        assertEquals(LoanRequestStatus.REJECTED, requestStatus(database, "request-alpha"));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(database, "request-early"));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(database, "request-beta"));
        assertEquals(EquipmentAvailability.AVAILABLE, database.read(unitOfWork ->
                unitOfWork.equipmentItems().findById(new EquipmentId("ball-available"))
                        .orElseThrow().availability()));
        assertEquals(List.of("request-early", "request-beta"), service.listPendingRequests().stream()
                .map(ExcoPendingRequest::loanRequestId).toList());
    }

    @Test
    void unauthorizedAndInvalidRejectionsLeaveRequestsUnchanged(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        seedRequests(database);
        SessionManager memberSession = signedInMember(database, "member-01");
        ExcoRequestService memberService = service(database, memberSession);

        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED, memberService::listPendingRequests);
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED,
                () -> memberService.rejectRequest(new PendingRequestSelection("request-early")));
        assertEquals(LoanRequestStatus.PENDING, requestStatus(database, "request-early"));

        SessionManager excoSession = signedInExco(database);
        ExcoRequestService excoService = service(database, excoSession);
        assertError(ApplicationErrorCode.NOT_FOUND,
                () -> excoService.rejectRequest(new PendingRequestSelection("missing-request")));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> excoService.rejectRequest(new PendingRequestSelection("request-rejected")));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> excoService.rejectRequest(new PendingRequestSelection("request-cancelled")));
        assertError(ApplicationErrorCode.CONFLICT,
                () -> excoService.rejectRequest(new PendingRequestSelection("request-approved")));
        assertEquals(LoanRequestStatus.REJECTED, requestStatus(database, "request-rejected"));
        assertEquals(LoanRequestStatus.CANCELLED, requestStatus(database, "request-cancelled"));
        assertEquals(LoanRequestStatus.APPROVED, requestStatus(database, "request-approved"));
    }

    @Test
    void rejectionRollback_preservesPendingRequestWhenTransactionFails(
            @TempDir java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = initializedDatabase(temporaryDirectory);
        seedRequests(database);
        TransactionManager failingTransactions = new TransactionManager() {
            @Override
            public <T> T read(UnitOfWorkOperation<T> operation) {
                return database.read(operation);
            }

            @Override
            public <T> T write(UnitOfWorkOperation<T> operation) {
                return database.write(unitOfWork -> {
                    operation.execute(unitOfWork);
                    throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                            "Injected transaction failure.", null);
                });
            }
        };
        ExcoRequestService service = new ExcoRequestService(failingTransactions,
                signedInExco(database), new EquipmentItemAvailabilityPolicy());

        assertError(ApplicationErrorCode.PERSISTENCE_FAILURE,
                () -> service.rejectRequest(new PendingRequestSelection("request-early")));

        assertEquals(LoanRequestStatus.PENDING, requestStatus(database, "request-early"));
    }

    private static ExcoRequestService service(TransactionManager transactionManager,
            SessionManager sessionManager) {
        return new ExcoRequestService(transactionManager, sessionManager,
                new EquipmentItemAvailabilityPolicy());
    }

    private static SqliteDatabase initializedDatabase(java.nio.file.Path temporaryDirectory) {
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("clubstock.db"));
        database.initialize();
        return database;
    }

    private static SessionManager signedInExco(SqliteDatabase database) {
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        authentication.completeExcoSetup("exco-password".toCharArray(),
                "exco-password".toCharArray());
        return sessionManager;
    }

    private static SessionManager signedInMember(SqliteDatabase database, String memberId) {
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        authentication.authenticateMember(memberId, MEMBER_PASSWORD.toCharArray());
        return sessionManager;
    }

    private static void seedRequests(SqliteDatabase database) {
        MemberId memberOne = new MemberId("member-01");
        MemberId memberTwo = new MemberId("member-02");
        EquipmentTypeId balls = new EquipmentTypeId("type-balls");
        EquipmentTypeId cones = new EquipmentTypeId("type-cones");
        EquipmentType ballType = EquipmentType.create(balls, new EquipmentTypeName("Court Balls"));
        ballType.offer();
        EquipmentType coneType = EquipmentType.create(cones, new EquipmentTypeName("Training Cones"));
        coneType.offer();
        EquipmentItem availableBall = EquipmentItem.create(new EquipmentId("ball-available"), balls);
        availableBall.release();
        EquipmentItem loanedBall = EquipmentItem.restore(new EquipmentId("ball-loaned"), balls,
                EquipmentCondition.GOOD, EquipmentAvailability.ON_LOAN, false, false, null);

        database.write(unitOfWork -> {
            unitOfWork.members().insert(Member.create(memberOne, "Ada Member",
                    new PasswordHash("test$" + MEMBER_PASSWORD)));
            unitOfWork.members().insert(Member.create(memberTwo, "Ben Member",
                    new PasswordHash("test$" + MEMBER_PASSWORD)));
            unitOfWork.equipmentTypes().insert(ballType);
            unitOfWork.equipmentTypes().insert(coneType);
            unitOfWork.equipmentItems().insert(availableBall);
            unitOfWork.equipmentItems().insert(loanedBall);
            unitOfWork.loanRequests().insert(request("request-early", memberOne, balls,
                    Instant.parse("2026-09-24T09:00:00Z"), LoanRequestStatus.PENDING,
                    "Tournament practice", null));
            unitOfWork.loanRequests().insert(request("request-alpha", memberTwo, cones,
                    Instant.parse("2026-09-24T10:00:00Z"), LoanRequestStatus.PENDING, null, null));
            unitOfWork.loanRequests().insert(request("request-beta", memberOne, balls,
                    Instant.parse("2026-09-24T10:00:00Z"), LoanRequestStatus.PENDING, null, null));
            unitOfWork.loanRequests().insert(request("request-rejected", memberOne, balls,
                    Instant.parse("2026-09-24T11:00:00Z"), LoanRequestStatus.REJECTED, null, null));
            unitOfWork.loanRequests().insert(request("request-cancelled", memberOne, balls,
                    Instant.parse("2026-09-24T11:01:00Z"), LoanRequestStatus.CANCELLED, null, null));
            unitOfWork.loanRequests().insert(request("request-approved", memberOne, balls,
                    Instant.parse("2026-09-24T11:02:00Z"), LoanRequestStatus.APPROVED, null, 1));
            unitOfWork.loans().insert(Loan.restore(new LoanId("loan-approved"),
                    new LoanRequestId("request-approved"), memberOne,
                    new EquipmentId("ball-loaned"), CLOCK.instant(), LocalDate.of(2026, 9, 28),
                    LoanStatus.ON_LOAN, null));
            return null;
        });
    }

    private static LoanRequest request(String requestId, MemberId memberId, EquipmentTypeId typeId,
            Instant requestedAt, LoanRequestStatus status, String details, Integer approvedQuantity) {
        return LoanRequest.restore(new LoanRequestId(requestId), memberId, typeId, 2,
                LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 28), details, requestedAt, status,
                approvedQuantity);
    }

    private static LoanRequestStatus requestStatus(SqliteDatabase database, String requestId) {
        return database.read(unitOfWork -> unitOfWork.loanRequests()
                .findById(new LoanRequestId(requestId)).orElseThrow().status());
    }

    private static void assertError(ApplicationErrorCode expectedCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(expectedCode, exception.errorCode());
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
