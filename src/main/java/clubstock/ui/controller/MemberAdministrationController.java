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
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
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
    private Button editMemberButton;
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
        editMemberButton.setDisable(true);
        refreshMembers();
    }

    /**
     * Opens the create-account dialog on demand.
     */
    @FXML
    private void openCreateDialog() {
        Dialog<ButtonType> dialog = newDialog("Create Member");
        TextField memberIdField = new TextField();
        memberIdField.setPromptText("e.g. member-001");
        TextField memberNameField = new TextField();
        memberNameField.setPromptText("Full name");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("At least 8 characters");
        Label feedback = newFeedbackLabel();
        Button createButton = new Button("Create Member");
        createButton.getStyleClass().add("primary-button");
        createButton.setOnAction(event -> {
            char[] password = passwordField.getText().toCharArray();
            boolean completed = execute(() -> memberAccountService.createMember(memberIdField.getText(),
                    memberNameField.getText(), password), "Member account created.", feedback);
            passwordField.clear();
            if (completed) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(labelFor("Member ID", memberIdField), memberIdField,
                labelFor("Member name", memberNameField), memberNameField,
                labelFor("Initial password", passwordField), passwordField, createButton, feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Opens the selected Member's edit dialog on demand.
     */
    @FXML
    private void openEditDialog() {
        MemberSummary member = requireSelection();
        if (member == null) {
            return;
        }
        Dialog<ButtonType> dialog = newDialog("Edit Member");
        Label memberId = new Label(member.memberId());
        memberId.getStyleClass().add("selected-member-id");
        TextField memberNameField = new TextField(member.name());
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("At least 8 characters");
        CheckBox removalConfirmation = new CheckBox(
                "I understand this prevents the Member from signing in.");
        removalConfirmation.setWrapText(true);
        Label feedback = newFeedbackLabel();
        Button saveNameButton = new Button("Save name");
        saveNameButton.getStyleClass().add("secondary-button");
        saveNameButton.setOnAction(event -> {
            if (execute(() -> memberAccountService.renameMember(member.memberId(),
                    memberNameField.getText()), "Member name updated.", feedback)) {
                dialog.close();
            }
        });
        Button replacePasswordButton = new Button("Replace password");
        replacePasswordButton.getStyleClass().add("secondary-button");
        replacePasswordButton.setOnAction(event -> {
            char[] password = passwordField.getText().toCharArray();
            boolean completed = execute(() -> memberAccountService.replacePassword(member.memberId(),
                    password), "Member password replaced.", feedback);
            passwordField.clear();
            if (completed) {
                dialog.close();
            }
        });
        Button removeButton = new Button("Remove Member");
        removeButton.getStyleClass().add("danger-button");
        removeButton.setOnAction(event -> {
            if (!removalConfirmation.isSelected()) {
                showDialogError(feedback,
                        "Select the confirmation checkbox before removing this Member.");
                return;
            }
            if (execute(() -> memberAccountService.deactivateMember(member.memberId()),
                    "Member account removed and can no longer sign in.", feedback)) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(new Label("Member ID"), memberId,
                labelFor("Member name", memberNameField), memberNameField, saveNameButton,
                labelFor("Replacement password", passwordField), passwordField,
                replacePasswordButton, removalConfirmation, removeButton, feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
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
            editMemberButton.setDisable(true);
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

    private boolean execute(Runnable operation, String successMessage, Label dialogFeedback) {
        clearStatus();
        try {
            operation.run();
            refreshMembers();
            showSuccess(successMessage);
            return true;
        } catch (ApplicationException exception) {
            showDialogError(dialogFeedback, exception.displayMessage());
            showError(exception.displayMessage());
            return false;
        } catch (RuntimeException exception) {
            showDialogError(dialogFeedback, UNEXPECTED_ERROR);
            showError(UNEXPECTED_ERROR);
            return false;
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
        editMemberButton.setDisable(member == null);
    }

    private static Dialog<ButtonType> newDialog(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(430);
        dialog.setResizable(true);
        return dialog;
    }

    private static VBox formContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(4));
        content.getStyleClass().add("account-form-card");
        return content;
    }

    private static Label labelFor(String text, javafx.scene.Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setLabelFor(field);
        return label;
    }

    private static Label newFeedbackLabel() {
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("error-text");
        return feedback;
    }

    private static void showDialogError(Label feedback, String message) {
        feedback.getStyleClass().remove("success-text");
        if (!feedback.getStyleClass().contains("error-text")) {
            feedback.getStyleClass().add("error-text");
        }
        feedback.setText(message);
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
