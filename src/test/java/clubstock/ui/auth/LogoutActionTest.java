package clubstock.ui.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;

class LogoutActionTest {
    @Test
    void clearsSessionBeforeReturningToRoleSelection() {
        List<String> events = new ArrayList<>();
        RecordingAuthentication authentication = new RecordingAuthentication(events);
        NavigationService navigation = route -> {
            assertTrue(authentication.currentPrincipal().isEmpty());
            events.add("navigate:" + route.name());
        };

        new LogoutAction(authentication, navigation).execute();

        assertEquals(List.of("logout", "navigate:ROLE_SELECTION"), events);
    }

    private static final class RecordingAuthentication implements AuthenticationGateway {
        private final List<String> events;
        private AuthenticatedPrincipal principal = AuthenticatedPrincipal.exco();

        private RecordingAuthentication(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean isExcoSetupRequired() {
            return false;
        }

        @Override
        public void completeExcoSetup(char[] password, char[] confirmation) {
            principal = AuthenticatedPrincipal.exco();
        }

        @Override
        public void authenticateExco(char[] password) {
            principal = AuthenticatedPrincipal.exco();
        }

        @Override
        public void authenticateMember(String memberId, char[] password) {
            principal = AuthenticatedPrincipal.member(memberId);
        }

        @Override
        public Optional<AuthenticatedPrincipal> currentPrincipal() {
            return Optional.ofNullable(principal);
        }

        @Override
        public void logout() {
            events.add("logout");
            principal = null;
        }
    }
}
