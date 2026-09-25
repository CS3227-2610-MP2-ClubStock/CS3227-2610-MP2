package clubstock.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import clubstock.ui.auth.AuthenticatedPrincipal;

class NavigationPolicyTest {
    private final NavigationPolicy policy = new NavigationPolicy();

    @Test
    void permitsEveryPublicAuthenticationRouteWithoutAPrincipal() {
        assertTrue(policy.permits(Route.ROLE_SELECTION, Optional.empty()));
        assertTrue(policy.permits(Route.EXCO_SETUP, Optional.empty()));
        assertTrue(policy.permits(Route.EXCO_LOGIN, Optional.empty()));
        assertTrue(policy.permits(Route.MEMBER_LOGIN, Optional.empty()));
    }

    @Test
    void excoHomeRequiresExcoPrincipal() {
        assertFalse(policy.permits(Route.EXCO_HOME, Optional.empty()));
        assertTrue(policy.permits(Route.EXCO_HOME,
                Optional.of(AuthenticatedPrincipal.exco())));
        assertFalse(policy.permits(Route.EXCO_HOME,
                Optional.of(AuthenticatedPrincipal.member("M-001"))));
    }

    @Test
    void excoRequestQueueRequiresExcoPrincipal() {
        assertFalse(policy.permits(Route.EXCO_REQUEST_QUEUE, Optional.empty()));
        assertTrue(policy.permits(Route.EXCO_REQUEST_QUEUE,
                Optional.of(AuthenticatedPrincipal.exco())));
        assertFalse(policy.permits(Route.EXCO_REQUEST_QUEUE,
                Optional.of(AuthenticatedPrincipal.member("M-001"))));
    }

    @Test
    void memberHomeRequiresMemberPrincipal() {
        assertFalse(policy.permits(Route.MEMBER_HOME, Optional.empty()));
        assertFalse(policy.permits(Route.MEMBER_HOME,
                Optional.of(AuthenticatedPrincipal.exco())));
        assertTrue(policy.permits(Route.MEMBER_HOME,
                Optional.of(AuthenticatedPrincipal.member("M-001"))));
    }

    @Test
    void memberRequestRoutesRequireMemberPrincipal() {
        for (Route route : new Route[] {Route.MEMBER_REQUEST_ENTRY, Route.MEMBER_OWN_REQUESTS}) {
            assertFalse(policy.permits(route, Optional.empty()), route.name());
            assertFalse(policy.permits(route,
                    Optional.of(AuthenticatedPrincipal.exco())), route.name());
            assertTrue(policy.permits(route,
                    Optional.of(AuthenticatedPrincipal.member("M-001"))), route.name());
        }
    }
}
