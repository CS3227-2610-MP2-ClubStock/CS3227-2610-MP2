package clubstock.ui.controller;

import java.util.List;

import clubstock.application.ApplicationException;
import clubstock.application.member.MemberAccountService;
import clubstock.application.member.MemberSummary;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Presents Exco Member-account administration while delegating account rules to the service.
 */
public final class MemberAdministrationController {
    private static final String UNEXPECTED_ERROR =
            "Member administration could not be completed. Please try again.";
    private final MemberAccountService memberAccountService;
    private final NavigationService navigation;
    @FXML
    private TableView<MemberSummary> membersTable;
    @FXML
    private TableColumn<MemberSummary, String> memberIdColumn;
    @FXML
    private TableColumn<MemberSummary, String> memberNameColumn;
    @FXML
    private TableColumn<MemberSummary, String> memberStatusColumn;
    @FXML
    private TextField newMemberIdField;
    @FXML
    private TextField newMemberNameField;
    @FXML
    private PasswordField newMemberPasswordField;
    @FXML
    private Label selectedMemberLabel;
    @FXML
    private TextField selectedMemberNameField;
    @FXML
    private PasswordField replacementPasswordField;
    @FXML
    private CheckBox deactivateConfirmation;
    @FXML
    private VBox createMemberPane;
    @FXML
    private VBox editMemberPane;
    @FXML
    private Label statusLabel;
    private MemberSummary selectedMember;

    /**
     * Creates the controller.
     *
     * @param memberAccountService Exco-authorized Member account service.
     * @param navigation Navigation boundary.
     */
    public MemberAdministrationController(MemberAccountService memberAccountService,
            NavigationService navigation) {
        if (memberAccountService == null || navigation == null) {
            throw new IllegalArgumentException("Member administration dependencies cannot be null.");
        }
        this.memberAccountService = memberAccountService;
        this.navigation = navigation;
    }

    /**
     * Configures the table and loads the initial Member list.
     */
    @FXML
    private void initialize() {
        memberIdColumn.setCellValueFactory(member ->
                new ReadOnlyStringWrapper(member.getValue().memberId()));
        memberNameColumn.setCellValueFactory(member ->
                new ReadOnlyStringWrapper(member.getValue().name()));
        memberStatusColumn.setCellValueFactory(member -> new ReadOnlyStringWrapper(
                member.getValue().active() ? "Active" : "Inactive"));
        membersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> selectMember(current));
        showCreateForm();
        refreshMembers();
    }

    /**
     * Creates an account from the create form.
     */
    @FXML
    private void createMember() {
        char[] password = newMemberPasswordField.getText().toCharArray();
        execute(() -> memberAccountService.createMember(newMemberIdField.getText(),
                newMemberNameField.getText(), password), "Member account created.", true);
        newMemberPasswordField.clear();
    }

    /**
     * Shows the new-Member form without changing the selected account.
     */
    @FXML
    private void showCreateForm() {
        setVisiblePane(createMemberPane, true);
        setVisiblePane(editMemberPane, false);
        newMemberIdField.requestFocus();
    }

    /**
     * Shows the selected-Member editing form when a Member is selected.
     */
    @FXML
    private void showEditForm() {
        if (requireSelection() == null) {
            return;
        }
        setVisiblePane(createMemberPane, false);
        setVisiblePane(editMemberPane, true);
        selectedMemberNameField.requestFocus();
    }

    /**
     * Saves the selected Member's replacement name.
     */
    @FXML
    private void saveName() {
        MemberSummary member = requireSelection();
        if (member == null) {
            return;
        }
        execute(() -> memberAccountService.renameMember(member.memberId(),
                selectedMemberNameField.getText()), "Member name updated.", false);
    }

    /**
     * Replaces the selected Member's password.
     */
    @FXML
    private void replacePassword() {
        MemberSummary member = requireSelection();
        if (member == null) {
            return;
        }
        char[] password = replacementPasswordField.getText().toCharArray();
        execute(() -> memberAccountService.replacePassword(member.memberId(), password),
                "Member password replaced.", false);
        replacementPasswordField.clear();
    }

    /**
     * Soft-deactivates the selected Member after explicit confirmation.
     */
    @FXML
    private void deactivateMember() {
        MemberSummary member = requireSelection();
        if (member == null) {
            return;
        }
        if (!deactivateConfirmation.isSelected()) {
            showError("Select the confirmation checkbox before removing this Member.");
            return;
        }
        execute(() -> memberAccountService.deactivateMember(member.memberId()),
                "Member account removed and can no longer sign in.", false);
    }

    /**
     * Refreshes the table from the shared service state.
     */
    @FXML
    private void refreshMembers() {
        try {
            List<MemberSummary> members = memberAccountService.listMembers();
            membersTable.setItems(FXCollections.observableArrayList(members));
            selectedMember = null;
            clearSelectionDetails();
            clearStatus();
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showError(UNEXPECTED_ERROR);
        }
    }

    /**
     * Returns to the Exco workspace.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    private void execute(Runnable operation, String successMessage, boolean clearCreateForm) {
        clearStatus();
        try {
            operation.run();
            if (clearCreateForm) {
                newMemberIdField.clear();
                newMemberNameField.clear();
            }
            refreshMembers();
            showSuccess(successMessage);
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showError(UNEXPECTED_ERROR);
        }
    }

    private MemberSummary requireSelection() {
        if (selectedMember == null) {
            showError("Select a Member account first.");
        }
        return selectedMember;
    }

    private void selectMember(MemberSummary member) {
        selectedMember = member;
        if (member == null) {
            clearSelectionDetails();
            return;
        }
        selectedMemberLabel.setText(member.memberId());
        selectedMemberNameField.setText(member.name());
        replacementPasswordField.clear();
        deactivateConfirmation.setSelected(false);
    }

    private void clearSelectionDetails() {
        selectedMemberLabel.setText("No Member selected");
        selectedMemberNameField.clear();
        replacementPasswordField.clear();
        deactivateConfirmation.setSelected(false);
    }

    private static void setVisiblePane(VBox pane, boolean visible) {
        pane.setVisible(visible);
        pane.setManaged(visible);
    }

    private void showError(String message) {
        statusLabel.getStyleClass().remove("success-text");
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
        statusLabel.setText(message);
    }

    private void showSuccess(String message) {
        statusLabel.getStyleClass().remove("error-text");
        if (!statusLabel.getStyleClass().contains("success-text")) {
            statusLabel.getStyleClass().add("success-text");
        }
        statusLabel.setText(message);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().remove("success-text");
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
    }
}
