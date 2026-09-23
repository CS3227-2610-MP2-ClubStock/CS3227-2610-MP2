package clubstock.ui.navigation;

/**
 * Navigation seam used by controllers and authentication actions.
 */
public interface NavigationService {
    /**
     * Shows a route after applying its authorization policy.
     *
     * @param route Destination route.
     */
    void show(Route route);
}
