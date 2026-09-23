package clubstock.ui.controller;

import java.util.Optional;

import clubstock.ui.auth.ExcoAuthenticationAction;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

/**
 * Handles one-time Exco password setup.
 */
public final class ExcoSetupController {
    private static final String UNEXPECTED_ERROR =
            "Exco setup could not be completed. Please try again.";
    private final ExcoAuthenticationAction authenticationAction;
    private final NavigationService navigation;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmationField;
    @FXML
    private Label errorLabel;

    /**
     * Creates the controller.
     *
     * @param authenticationAction Exco authentication action.
     * @param navigation Navigation boundary.
     */
    public ExcoSetupController(ExcoAuthenticationAction authenticationAction,
            NavigationService navigation) {
        if (authenticationAction == null || navigation == null) {
            throw new IllegalArgumentException("Exco setup dependencies cannot be null.");
        }
        this.authenticationAction = authenticationAction;
        this.navigation = navigation;
    }

    @FXML
    private void completeSetup() {
        errorLabel.setText("");
        char[] password = passwordField.getText().toCharArray();
        char[] confirmation = confirmationField.getText().toCharArray();
        try {
            Optional<String> error = authenticationAction.completeSetup(password, confirmation);
            error.ifPresent(errorLabel::setText);
        } catch (RuntimeException exception) {
            errorLabel.setText(UNEXPECTED_ERROR);
        } finally {
            passwordField.clear();
            confirmationField.clear();
            passwordField.requestFocus();
        }
    }

    @FXML
    private void goBack() {
        navigation.show(Route.ROLE_SELECTION);
    }
}
