package clubstock.ui.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.ui.navigation.Route;

class RoleSelectionActionTest {
    @Test
    void sendsFreshAndConfiguredExcoAccountsToDifferentRoutes() {
        StubAuthentication authentication = new StubAuthentication();
        List<Route> routes = new ArrayList<>();
        RoleSelectionAction action = new RoleSelectionAction(authentication, routes::add);

        assertTrue(action.selectExco().isEmpty());
        authentication.setupRequired = false;
        assertTrue(action.selectExco().isEmpty());

        assertEquals(List.of(Route.EXCO_SETUP, Route.EXCO_LOGIN), routes);
    }

    @Test
    void exposesOnlySafeSetupQueryFailure() {
        StubAuthentication authentication = new StubAuthentication();
        authentication.failure = new ApplicationException(
                ApplicationErrorCode.PERSISTENCE_FAILURE,
                "Account state could not be read.", new IllegalStateException("technical"));
        List<Route> routes = new ArrayList<>();
        RoleSelectionAction action = new RoleSelectionAction(authentication, routes::add);

        Optional<String> error = action.selectExco();

        assertEquals("Account state could not be read.", error.orElseThrow());
        assertTrue(routes.isEmpty());
    }

    @Test
    void sendsMemberChoiceToMemberLoginIntegrationRoute() {
        List<Route> routes = new ArrayList<>();
        RoleSelectionAction action = new RoleSelectionAction(new StubAuthentication(), routes::add);

        action.selectMember();

        assertEquals(List.of(Route.MEMBER_LOGIN), routes);
    }

    private static final class StubAuthentication implements AuthenticationGateway {
        private boolean setupRequired = true;
        private ApplicationException failure;

        @Override
        public boolean isExcoSetupRequired() {
            if (failure != null) {
                throw failure;
            }
            return setupRequired;
        }

        @Override
        public void completeExcoSetup(char[] password, char[] confirmation) {
        }

        @Override
        public void authenticateExco(char[] password) {
        }

        @Override
        public Optional<AuthenticatedPrincipal> currentPrincipal() {
            return Optional.empty();
        }

        @Override
        public void logout() {
        }
    }
}
