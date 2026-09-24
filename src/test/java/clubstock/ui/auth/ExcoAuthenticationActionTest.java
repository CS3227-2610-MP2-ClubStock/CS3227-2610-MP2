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
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

class ExcoAuthenticationActionTest {
    @Test
    void completesSetupBeforeNavigatingAndClearsCredentialArrays() {
        List<String> events = new ArrayList<>();
        RecordingAuthentication authentication = new RecordingAuthentication(events);
        RecordingNavigation navigation = new RecordingNavigation(events);
        ExcoAuthenticationAction action = new ExcoAuthenticationAction(authentication, navigation);
        char[] password = "secure-pass".toCharArray();
        char[] confirmation = "secure-pass".toCharArray();

        Optional<String> error = action.completeSetup(password, confirmation);

        assertTrue(error.isEmpty());
        assertEquals(List.of("setup", "navigate:EXCO_HOME"), events);
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }

    @Test
    void returnsSafeSetupFailureWithoutNavigatingAndClearsCredentials() {
        RecordingAuthentication authentication = new RecordingAuthentication(new ArrayList<>());
        authentication.failure = authenticationFailure();
        RecordingNavigation navigation = new RecordingNavigation(new ArrayList<>());
        ExcoAuthenticationAction action = new ExcoAuthenticationAction(authentication, navigation);
        char[] password = "wrong-pass".toCharArray();
        char[] confirmation = "different!".toCharArray();

        Optional<String> error = action.completeSetup(password, confirmation);

        assertEquals("Credentials were not accepted.", error.orElseThrow());
        assertTrue(navigation.events.isEmpty());
        assertArrayEquals(new char[password.length], password);
        assertArrayEquals(new char[confirmation.length], confirmation);
    }

    @Test
    void authenticatesBeforeNavigatingAndClearsPassword() {
        List<String> events = new ArrayList<>();
        RecordingAuthentication authentication = new RecordingAuthentication(events);
        RecordingNavigation navigation = new RecordingNavigation(events);
        ExcoAuthenticationAction action = new ExcoAuthenticationAction(authentication, navigation);
        char[] password = "secure-pass".toCharArray();

        Optional<String> error = action.login(password);

        assertTrue(error.isEmpty());
        assertEquals(List.of("login", "navigate:EXCO_HOME"), events);
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void rejectsSuccessfulServiceCallThatDidNotEstablishAnExcoPrincipal() {
        RecordingAuthentication authentication = new RecordingAuthentication(new ArrayList<>());
        authentication.establishPrincipal = false;
        RecordingNavigation navigation = new RecordingNavigation(new ArrayList<>());
        ExcoAuthenticationAction action = new ExcoAuthenticationAction(authentication, navigation);
        char[] password = "secure-pass".toCharArray();

        Optional<String> error = action.login(password);

        assertEquals("Sign in could not be completed. Please try again.", error.orElseThrow());
        assertTrue(authentication.currentPrincipal().isEmpty());
        assertTrue(navigation.events.isEmpty());
        assertArrayEquals(new char[password.length], password);
    }

    private static ApplicationException authenticationFailure() {
        return new ApplicationException(ApplicationErrorCode.AUTHENTICATION_FAILED,
                "Credentials were not accepted.", null);
    }

    private static final class RecordingAuthentication implements AuthenticationGateway {
        private final List<String> events;
        private ApplicationException failure;
        private AuthenticatedPrincipal principal;
        private boolean establishPrincipal = true;

        private RecordingAuthentication(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean isExcoSetupRequired() {
            return principal == null;
        }

        @Override
        public void completeExcoSetup(char[] password, char[] confirmation) {
            events.add("setup");
            failIfConfigured();
            establishPrincipalIfConfigured();
        }

        @Override
        public void authenticateExco(char[] password) {
            events.add("login");
            failIfConfigured();
            establishPrincipalIfConfigured();
        }

        @Override
        public void authenticateMember(String memberId, char[] password) {
            events.add("member-login");
            failIfConfigured();
            if (establishPrincipal) {
                principal = AuthenticatedPrincipal.member(memberId);
            }
        }

        @Override
        public Optional<AuthenticatedPrincipal> currentPrincipal() {
            return Optional.ofNullable(principal);
        }

        @Override
        public void logout() {
            principal = null;
        }

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }

        private void establishPrincipalIfConfigured() {
            if (establishPrincipal) {
                principal = AuthenticatedPrincipal.exco();
            }
        }
    }

    private static final class RecordingNavigation implements NavigationService {
        private final List<String> events;

        private RecordingNavigation(List<String> events) {
            this.events = events;
        }

        @Override
        public void show(Route route) {
            events.add("navigate:" + route.name());
        }
    }
}
