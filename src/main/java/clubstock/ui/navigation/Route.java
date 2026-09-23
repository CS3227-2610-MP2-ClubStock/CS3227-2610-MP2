package clubstock.ui.navigation;

import java.util.Optional;

import clubstock.ui.auth.UserRole;

/**
 * Application routes and their authorization requirements.
 */
public enum Route {
    /** Public role selection. */
    ROLE_SELECTION("/clubstock/ui/view/role-selection.fxml", "Choose role", null),
    /** Public first-run Exco setup. */
    EXCO_SETUP("/clubstock/ui/view/exco-setup.fxml", "Set up Exco access", null),
    /** Public configured Exco login. */
    EXCO_LOGIN("/clubstock/ui/view/exco-login.fxml", "Exco sign in", null),
    /** Public Member-login integration route. */
    MEMBER_LOGIN("/clubstock/ui/view/member-login.fxml", "Member sign in", null),
    /** Exco-protected shell. */
    EXCO_HOME("/clubstock/ui/view/exco-home.fxml", "Exco home", UserRole.EXCO),
    /** Member-protected shell. */
    MEMBER_HOME("/clubstock/ui/view/member-home.fxml", "Member home", UserRole.MEMBER);

    private final String resourcePath;
    private final String windowTitle;
    private final UserRole requiredRole;

    Route(String resourcePath, String windowTitle, UserRole requiredRole) {
        this.resourcePath = resourcePath;
        this.windowTitle = windowTitle;
        this.requiredRole = requiredRole;
    }

    /**
     * Returns the FXML classpath location.
     *
     * @return Absolute classpath resource path.
     */
    public String resourcePath() {
        return resourcePath;
    }

    /**
     * Returns the route-specific window title.
     *
     * @return Window title.
     */
    public String windowTitle() {
        return windowTitle;
    }

    /**
     * Returns the role required to enter the route.
     *
     * @return Required role, or empty for a public route.
     */
    public Optional<UserRole> requiredRole() {
        return Optional.ofNullable(requiredRole);
    }
}
