package clubstock.ui.navigation;

import java.util.Optional;

import clubstock.ui.auth.AuthenticatedPrincipal;

/**
 * Makes the route-authorization decision without loading JavaFX resources.
 */
public final class NavigationPolicy {
    /**
     * Creates the stateless navigation policy.
     */
    public NavigationPolicy() {
    }

    /**
     * Returns whether a principal may enter a route.
     *
     * @param route Requested route.
     * @param principal Current principal, when authenticated.
     * @return True when the route is public or the principal role matches.
     */
    public boolean permits(Route route, Optional<AuthenticatedPrincipal> principal) {
        if (route == null || principal == null) {
            throw new IllegalArgumentException("Navigation policy inputs cannot be null.");
        }
        return route.requiredRole()
                .map(required -> principal.map(current -> current.role() == required).orElse(false))
                .orElse(true);
    }
}
