package clubstock.ui.controller;

import java.util.Optional;

import clubstock.ui.auth.MemberAuthenticationAction;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Handles Member credential entry and feedback.
 */
public final class MemberLoginController {
    private static final String UNEXPECTED_ERROR =
            "Member sign in could not be completed. Please try again.";
    private final MemberAuthenticationAction authenticationAction;
    private final NavigationService navigation;
    @FXML
    private Label memberIdLabel;
    @FXML
    private TextField memberIdField;
    @FXML
    private Label passwordLabel;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    /**
     * Creates the Member login controller.
     *
     * @param authenticationAction Member authentication action.
     * @param navigation Navigation boundary.
     */
    public MemberLoginController(MemberAuthenticationAction authenticationAction,
            NavigationService navigation) {
        if (authenticationAction == null || navigation == null) {
            throw new IllegalArgumentException("Member login dependencies cannot be null.");
        }
        this.authenticationAction = authenticationAction;
        this.navigation = navigation;
    }

    /**
     * Associates accessible labels with their credential fields.
     */
    @FXML
    private void initialize() {
        memberIdLabel.setLabelFor(memberIdField);
        passwordLabel.setLabelFor(passwordField);
    }

    /**
     * Submits Member credentials and clears the password field.
     */
    @FXML
    private void login() {
        errorLabel.setText("");
        String memberId = memberIdField.getText();
        char[] password = passwordField.getText().toCharArray();
        try {
            Optional<String> error = authenticationAction.login(memberId, password);
            error.ifPresent(errorLabel::setText);
        } catch (RuntimeException exception) {
            errorLabel.setText(UNEXPECTED_ERROR);
        } finally {
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    /**
     * Returns to role selection.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.ROLE_SELECTION);
    }
}
