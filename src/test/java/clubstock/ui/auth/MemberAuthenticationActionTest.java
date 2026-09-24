package clubstock.ui.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.ui.navigation.Route;

class MemberAuthenticationActionTest {
    @Test
    void loginAuthenticatesBeforeRoutingAndClearsPassword() {
        RecordingGateway authentication = new RecordingGateway();
        List<Route> routes = new ArrayList<>();
        MemberAuthenticationAction action = new MemberAuthenticationAction(authentication,
                routes::add);
        char[] password = "member-password".toCharArray();

        Optional<String> error = action.login("member-01", password);

        assertTrue(error.isEmpty());
        assertEquals(List.of("member:member-01"), authentication.events);
        assertEquals(List.of(Route.MEMBER_HOME), routes);
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void rejectedLoginDoesNotNavigateAndClearsPassword() {
        RecordingGateway authentication = new RecordingGateway();
        authentication.failure = new ApplicationException(ApplicationErrorCode.AUTHENTICATION_FAILED,
                "Credentials were not accepted.", null);
        List<Route> routes = new ArrayList<>();
        MemberAuthenticationAction action = new MemberAuthenticationAction(authentication,
                routes::add);
        char[] password = "wrong-password".toCharArray();

        Optional<String> error = action.login("member-01", password);

        assertEquals("Credentials were not accepted.", error.orElseThrow());
        assertTrue(routes.isEmpty());
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void wrongRolePrincipalIsClearedWithoutOpeningMemberShell() {
        RecordingGateway authentication = new RecordingGateway();
        authentication.establishExco = true;
        List<Route> routes = new ArrayList<>();
        MemberAuthenticationAction action = new MemberAuthenticationAction(authentication,
                routes::add);

        Optional<String> error = action.login("member-01", "member-password".toCharArray());

        assertTrue(error.isPresent());
        assertTrue(authentication.currentPrincipal().isEmpty());
        assertTrue(routes.isEmpty());
    }

    private static final class RecordingGateway implements AuthenticationGateway {
        private final List<String> events = new ArrayList<>();
        private AuthenticatedPrincipal principal;
        private ApplicationException failure;
        private boolean establishExco;

        @Override
        public boolean isExcoSetupRequired() {
            return true;
        }

        @Override
        public void completeExcoSetup(char[] password, char[] confirmation) {
        }

        @Override
        public void authenticateExco(char[] password) {
            principal = AuthenticatedPrincipal.exco();
        }

        @Override
        public void authenticateMember(String memberId, char[] password) {
            events.add("member:" + memberId);
            if (failure != null) {
                throw failure;
            }
            principal = establishExco
                    ? AuthenticatedPrincipal.exco()
                    : AuthenticatedPrincipal.member(memberId.strip());
        }

        @Override
        public Optional<AuthenticatedPrincipal> currentPrincipal() {
            return Optional.ofNullable(principal);
        }

        @Override
        public void logout() {
            principal = null;
        }
    }
}
