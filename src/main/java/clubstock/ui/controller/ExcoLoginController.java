package clubstock.ui.controller;

import java.util.Optional;

import clubstock.ui.auth.ExcoAuthenticationAction;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

/**
 * Handles configured Exco login.
 */
public final class ExcoLoginController {
    private static final String UNEXPECTED_ERROR =
            "Exco sign in could not be completed. Please try again.";
    private final ExcoAuthenticationAction authenticationAction;
    private final NavigationService navigation;
    @FXML
    private Label passwordLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    /**
     * Creates the controller.
     *
     * @param authenticationAction Exco authentication action.
     * @param navigation Navigation boundary.
     */
    public ExcoLoginController(ExcoAuthenticationAction authenticationAction,
            NavigationService navigation) {
        if (authenticationAction == null || navigation == null) {
            throw new IllegalArgumentException("Exco login dependencies cannot be null.");
        }
        this.authenticationAction = authenticationAction;
        this.navigation = navigation;
    }

    @FXML
    private void initialize() {
        passwordLabel.setLabelFor(passwordField);
    }

    @FXML
    private void login() {
        errorLabel.setText("");
        char[] password = passwordField.getText().toCharArray();
        try {
            Optional<String> error = authenticationAction.login(password);
            error.ifPresent(errorLabel::setText);
        } catch (RuntimeException exception) {
            errorLabel.setText(UNEXPECTED_ERROR);
        } finally {
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    @FXML
    private void goBack() {
        navigation.show(Route.ROLE_SELECTION);
    }
}
