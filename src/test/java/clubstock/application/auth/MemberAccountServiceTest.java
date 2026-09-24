package clubstock.application.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import clubstock.application.member.MemberAccountService;
import clubstock.application.member.MemberSummary;
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
import clubstock.domain.request.LoanRequest;
import clubstock.domain.request.LoanRequestId;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class MemberAccountServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void excoCanCreateUpdateAndDeactivateAnAccountConsumedByAuthentication(
            @TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        TestPasswordHasher hasher = new TestPasswordHasher();
        SessionManager sessionManager = signedInExco(database, hasher);
        MemberAccountService accounts = new MemberAccountService(database, sessionManager, hasher,
                CLOCK);
        char[] initialPassword = "member-password".toCharArray();

        accounts.createMember(" member-01 ", " Initial Member ", initialPassword);

        assertArrayEquals(new char[initialPassword.length], initialPassword);
        assertEquals(java.util.List.of(new MemberSummary("member-01", "Initial Member", true)),
                accounts.listMembers());
        accounts.renameMember("member-01", " Renamed Member ");
        char[] replacementPassword = "replacement-password".toCharArray();
        accounts.replacePassword("member-01", replacementPassword);
        assertArrayEquals(new char[replacementPassword.length], replacementPassword);
        assertEquals("Renamed Member", accounts.listMembers().getFirst().name());

        AuthenticationService authentication = new AuthenticationService(database, hasher,
                sessionManager);
        sessionManager.logout();
        assertMemberAuthenticationFails(authentication, "member-01", "member-password");
        authentication.authenticateMember("member-01", "replacement-password".toCharArray());
        assertEquals(new MemberId("member-01"), sessionManager.requireMember());

        sessionManager.logout();
        authentication.authenticateExco("exco-password".toCharArray());
        accounts.deactivateMember("member-01");
        assertFalse(accounts.listMembers().getFirst().active());
        assertEquals(CLOCK.instant(), database.read(unitOfWork -> unitOfWork.members()
                .findById(new MemberId("member-01")).orElseThrow().removedAt().orElseThrow()));

        sessionManager.logout();
        assertMemberAuthenticationFails(authentication, "member-01", "replacement-password");
    }

    @Test
    void validationAndAuthorizationFailuresLeaveAccountsUnchanged(
            @TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        TestPasswordHasher hasher = new TestPasswordHasher();
        SessionManager sessionManager = signedInExco(database, hasher);
        MemberAccountService accounts = new MemberAccountService(database, sessionManager, hasher,
                CLOCK);

        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> accounts.createMember(" ", "Name", "member-password".toCharArray()));
        assertError(ApplicationErrorCode.VALIDATION_FAILED,
                () -> accounts.createMember("member-01", " ", "member-password".toCharArray()));
        accounts.createMember("member-01", "Member", "member-password".toCharArray());
        assertError(ApplicationErrorCode.CONFLICT,
                () -> accounts.createMember("member-01", "Other", "member-password".toCharArray()));
        assertEquals(1, accounts.listMembers().size());

        sessionManager.logout();
        assertError(ApplicationErrorCode.AUTHORIZATION_DENIED, accounts::listMembers);
        int persistedMemberCount = database.read(
                unitOfWork -> unitOfWork.members().findAll().size());
        assertEquals(1, persistedMemberCount);
    }

    @Test
    void pendingRequestsAndUnresolvedLoansBlockDeactivation(
            @TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        TestPasswordHasher hasher = new TestPasswordHasher();
        SessionManager sessionManager = signedInExco(database, hasher);
        MemberAccountService accounts = new MemberAccountService(database, sessionManager, hasher,
                CLOCK);
        accounts.createMember("member-01", "Member", "member-password".toCharArray());
        seedType(database);
        database.write(unitOfWork -> {
            unitOfWork.loanRequests().insert(request("pending-request", LoanRequestStatus.PENDING));
            return null;
        });
        assertError(ApplicationErrorCode.CONFLICT, () -> accounts.deactivateMember("member-01"));
        assertTrue(accounts.listMembers().getFirst().active());

        database.write(unitOfWork -> {
            unitOfWork.loanRequests().update(request("pending-request", LoanRequestStatus.CANCELLED));
            unitOfWork.equipmentItems().insert(EquipmentItem.restore(new EquipmentId("item-01"),
                    new EquipmentTypeId("type-01"), EquipmentCondition.GOOD,
                    EquipmentAvailability.ON_LOAN, false, false, null));
            unitOfWork.loanRequests().insert(request("loan-request", LoanRequestStatus.APPROVED));
            unitOfWork.loans().insert(Loan.restore(new LoanId("loan-01"),
                    new LoanRequestId("loan-request"), new MemberId("member-01"),
                    new EquipmentId("item-01"), CLOCK.instant(), LocalDate.of(2026, 10, 1),
                    LoanStatus.ON_LOAN, null));
            return null;
        });
        assertError(ApplicationErrorCode.CONFLICT, () -> accounts.deactivateMember("member-01"));
        assertTrue(accounts.listMembers().getFirst().active());
    }

    private static SessionManager signedInExco(SqliteDatabase database, PasswordHasher hasher) {
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database, hasher,
                sessionManager);
        authentication.completeExcoSetup("exco-password".toCharArray(),
                "exco-password".toCharArray());
        return sessionManager;
    }

    private static void seedType(SqliteDatabase database) {
        database.write(unitOfWork -> {
            unitOfWork.equipmentTypes().insert(EquipmentType.create(new EquipmentTypeId("type-01"),
                    new EquipmentTypeName("Test type")));
            return null;
        });
    }

    private static LoanRequest request(String requestId, LoanRequestStatus status) {
        return LoanRequest.restore(new LoanRequestId(requestId), new MemberId("member-01"),
                new EquipmentTypeId("type-01"), 1, LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 1), null, CLOCK.instant(), status,
                status == LoanRequestStatus.APPROVED ? 1 : null);
    }

    private static SqliteDatabase initializedDatabase(java.nio.file.Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        return database;
    }

    private static void assertMemberAuthenticationFails(AuthenticationService authentication,
            String memberId, String password) {
        assertError(ApplicationErrorCode.AUTHENTICATION_FAILED,
                () -> authentication.authenticateMember(memberId, password.toCharArray()));
    }

    private static void assertError(ApplicationErrorCode errorCode, Runnable operation) {
        ApplicationException exception = assertThrows(ApplicationException.class, operation::run);
        assertEquals(errorCode, exception.errorCode());
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] password) {
            try {
                if (password.length < 8) {
                    throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED,
                            "Password must contain at least eight characters.", null);
                }
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
