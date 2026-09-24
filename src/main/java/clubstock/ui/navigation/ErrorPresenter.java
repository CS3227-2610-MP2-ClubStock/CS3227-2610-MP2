package clubstock.ui.navigation;

/**
 * Presents safe application-level errors at the navigation boundary.
 */
public interface ErrorPresenter {
    /**
     * Shows a safe message.
     *
     * @param title Dialog title.
     * @param message Safe user-facing content.
     */
    void showError(String title, String message);
}
