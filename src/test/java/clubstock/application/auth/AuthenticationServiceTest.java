package clubstock.application.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.infrastructure.sqlite.SqliteDatabase;
import clubstock.ui.auth.AuthenticationGatewayAdapter;
import clubstock.ui.auth.UserRole;

class AuthenticationServiceTest {
    @Test
    void setupIsOneTimeAndExcoCanAuthenticateAfterLogout(@TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        char[] password = "setup-password".toCharArray();
        char[] confirmation = "setup-password".toCharArray();

        assertTrue(authentication.requiresExcoSetup());
        authentication.completeExcoSetup(password, confirmation);

        assertFalse(authentication.requiresExcoSetup());
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
        assertEquals(AccountRole.EXCO,
                sessionManager.currentPrincipal().orElseThrow().role());
        sessionManager.logout();

        ApplicationException repeatedSetup = assertThrows(ApplicationException.class,
                () -> authentication.completeExcoSetup(
                        "replacement-pass".toCharArray(), "replacement-pass".toCharArray()));
        assertEquals(ApplicationErrorCode.CONFLICT, repeatedSetup.errorCode());
        assertTrue(sessionManager.currentPrincipal().isEmpty());

        ApplicationException wrongPassword = assertThrows(ApplicationException.class,
                () -> authentication.authenticateExco("wrong-password".toCharArray()));
        assertEquals(ApplicationErrorCode.AUTHENTICATION_FAILED, wrongPassword.errorCode());
        assertTrue(sessionManager.currentPrincipal().isEmpty());

        authentication.authenticateExco("setup-password".toCharArray());
        assertEquals(AccountRole.EXCO,
                sessionManager.requireExco().role());
    }

    @Test
    void passwordConfirmationMismatchDoesNotPersistOrEstablishASession(
            @TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database,
                new TestPasswordHasher(), sessionManager);
        char[] password = "setup-password".toCharArray();
        char[] confirmation = "different-pass".toCharArray();

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> authentication.completeExcoSetup(password, confirmation));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.errorCode());
        assertTrue(authentication.requiresExcoSetup());
        assertTrue(sessionManager.currentPrincipal().isEmpty());
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }

    @Test
    void memberFailuresAreGenericAndSuccessfulLoginIsOwnerScoped(
            @TempDir java.nio.file.Path tempDirectory) {
        SqliteDatabase database = initializedDatabase(tempDirectory);
        TestPasswordHasher hasher = new TestPasswordHasher();
        Member activeMember = Member.create(new MemberId("member-01"), "Active Member",
                hasher.hash("member-password".toCharArray()));
        Member inactiveMember = Member.create(new MemberId("member-02"), "Inactive Member",
                hasher.hash("member-password".toCharArray()));
        inactiveMember.deactivate(Instant.parse("2026-09-24T00:00:00Z"));
        database.write(unitOfWork -> {
            unitOfWork.members().insert(activeMember);
            unitOfWork.members().insert(inactiveMember);
            return null;
        });

        SessionManager sessionManager = new SessionManager();
        AuthenticationService authentication = new AuthenticationService(database, hasher,
                sessionManager);
        ApplicationException unknown = assertMemberFailure(authentication, "missing", "anything");
        ApplicationException inactive = assertMemberFailure(authentication, "member-02",
                "member-password");
        ApplicationException wrongPassword = assertMemberFailure(authentication, "member-01",
                "wrong-password");

        assertEquals(unknown.errorCode(), inactive.errorCode());
        assertEquals(unknown.errorCode(), wrongPassword.errorCode());
        assertEquals(unknown.displayMessage(), inactive.displayMessage());
        assertEquals(unknown.displayMessage(), wrongPassword.displayMessage());
        assertTrue(sessionManager.currentPrincipal().isEmpty());

        authentication.authenticateMember(" member-01 ", "member-password".toCharArray());
        assertEquals(activeMember.memberId(), sessionManager.requireMember());
        assertEquals(activeMember.memberId(), sessionManager.requireMember(activeMember.memberId()));
        assertEquals(UserRole.MEMBER,
                new AuthenticationGatewayAdapter(authentication)
                        .currentPrincipal().orElseThrow().role());
        ApplicationException wrongRole = assertThrows(ApplicationException.class,
                sessionManager::requireExco);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, wrongRole.errorCode());
        ApplicationException wrongOwner = assertThrows(ApplicationException.class,
                () -> sessionManager.requireMember(new MemberId("member-02")));
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, wrongOwner.errorCode());

        authentication.logout();
        ApplicationException afterLogout = assertThrows(ApplicationException.class,
                sessionManager::requireMember);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, afterLogout.errorCode());
    }

    @Test
    void onlyOnePrincipalCanBeActiveAndLogoutIsIdempotent() {
        SessionManager sessionManager = new SessionManager();
        sessionManager.establish(Principal.exco());

        ApplicationException conflict = assertThrows(ApplicationException.class,
                () -> sessionManager.establish(Principal.member(new MemberId("member-01"))));
        assertEquals(ApplicationErrorCode.CONFLICT, conflict.errorCode());

        sessionManager.logout();
        sessionManager.logout();
        assertTrue(sessionManager.currentPrincipal().isEmpty());
    }

    private static ApplicationException assertMemberFailure(AuthenticationService authentication,
            String memberId, String password) {
        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> authentication.authenticateMember(memberId, password.toCharArray()));
        assertEquals(ApplicationErrorCode.AUTHENTICATION_FAILED, failure.errorCode());
        return failure;
    }

    private static SqliteDatabase initializedDatabase(java.nio.file.Path tempDirectory) {
        SqliteDatabase database = new SqliteDatabase(tempDirectory.resolve("clubstock.db"));
        database.initialize();
        return database;
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] password) {
            try {
                return new PasswordHash("test$" + Base64.getEncoder().encodeToString(
                        new String(password).getBytes(StandardCharsets.UTF_8)));
            } finally {
                Arrays.fill(password, '\0');
            }
        }

        @Override
        public boolean matches(char[] password, PasswordHash passwordHash) {
            try {
                String expected = new String(Base64.getDecoder().decode(
                        passwordHash.encodedHash().substring("test$".length())),
                        StandardCharsets.UTF_8);
                return expected.equals(new String(password));
            } finally {
                Arrays.fill(password, '\0');
            }
        }
    }
}
