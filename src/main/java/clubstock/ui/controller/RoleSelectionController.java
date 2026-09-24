package clubstock.ui.controller;

import java.util.Optional;

import clubstock.ui.auth.RoleSelectionAction;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Handles the public role-selection screen.
 */
public final class RoleSelectionController {
    private static final String UNEXPECTED_ERROR =
            "The sign-in choice could not be opened. Please try again.";
    private final RoleSelectionAction roleSelection;
    @FXML
    private Label errorLabel;

    /**
     * Creates the controller.
     *
     * @param roleSelection Role routing action.
     */
    public RoleSelectionController(RoleSelectionAction roleSelection) {
        if (roleSelection == null) {
            throw new IllegalArgumentException("Role-selection action cannot be null.");
        }
        this.roleSelection = roleSelection;
    }

    @FXML
    private void selectExco() {
        errorLabel.setText("");
        try {
            Optional<String> error = roleSelection.selectExco();
            error.ifPresent(errorLabel::setText);
        } catch (RuntimeException exception) {
            errorLabel.setText(UNEXPECTED_ERROR);
        }
    }

    @FXML
    private void selectMember() {
        errorLabel.setText("");
        try {
            roleSelection.selectMember();
        } catch (RuntimeException exception) {
            errorLabel.setText(UNEXPECTED_ERROR);
        }
    }
}
