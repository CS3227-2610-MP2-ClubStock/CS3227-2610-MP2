package clubstock.ui.controller;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.loan.LoanQueryService;
import clubstock.application.loan.MemberActiveLoan;
import clubstock.application.loan.MemberLoanService;
import clubstock.domain.loan.LoanStatus;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Presents the authenticated Member's unresolved individual Loans and reporting actions.
 */
public final class MemberActiveLoansController {
    /** Safe message shown when active Loans cannot be loaded. */
    private static final String LOAD_FAILURE_TEXT =
            "Your active Loans could not be loaded. Try refreshing.";
    /** Safe message shown when a Member action cannot be completed. */
    private static final String ACTION_FAILURE_TEXT =
            "Your action could not be completed. Refresh your Loans and try again.";
    /** Local date-time format for the loan start instant. */
    private static final DateTimeFormatter STARTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

    private final LoanQueryService loanQueryService;
    private final MemberLoanService memberLoanService;
    private final NavigationService navigation;
    /**
     * Groups the mutually exclusive reported return conditions.
     */
    private final ToggleGroup returnConditionGroup = new ToggleGroup();
    /**
     * Keeps the selected evidence image only while the damage form is open.
     */
    private Path damageImagePath;
    /**
     * Identifies the Loan associated with the currently open reporting form.
     */
    private String formLoanId;
    /**
     * Tracks whether an asynchronous Loan snapshot is being loaded.
     */
    private boolean isLoadingLoans;
    /**
     * Tracks whether an asynchronous Member report is being submitted.
     */
    private boolean isSubmissionPending;

    @FXML
    private TableView<MemberActiveLoan> loansTable;
    @FXML
    private TableColumn<MemberActiveLoan, String> equipmentTypeColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> equipmentIdColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> statusColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> startedAtColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> endDateColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> overdueColumn;
    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label loadingLabel;
    @FXML
    private Label operationMessageLabel;
    @FXML
    private Button refreshButton;
    @FXML
    private Button returnActionButton;
    @FXML
    private Button lostActionButton;
    @FXML
    private VBox returnForm;
    @FXML
    private VBox damagedReturnFields;
    @FXML
    private VBox lostForm;
    @FXML
    private RadioButton goodReturnRadioButton;
    @FXML
    private RadioButton damagedReturnRadioButton;
    @FXML
    private TextArea damageDescriptionArea;
    @FXML
    private Label selectedDamageImageLabel;
    @FXML
    private Button chooseDamageImageButton;
    @FXML
    private Button submitReturnButton;
    @FXML
    private Button cancelReturnButton;
    @FXML
    private TextArea lossDescriptionArea;
    @FXML
    private Button submitLostButton;
    @FXML
    private Button cancelLostButton;

    /**
     * Creates the Member active-Loans controller.
     *
     * @param loanQueryService Shared role-authorized Loan query service.
     * @param memberLoanService Shared authorized Member report service.
     * @param navigation Navigation boundary.
     * @throws IllegalArgumentException If a dependency is null.
     */
    public MemberActiveLoansController(LoanQueryService loanQueryService,
            MemberLoanService memberLoanService, NavigationService navigation) {
        if (loanQueryService == null || memberLoanService == null || navigation == null) {
            throw new IllegalArgumentException("Member active-Loans dependencies cannot be null.");
        }
        this.loanQueryService = loanQueryService;
        this.memberLoanService = memberLoanService;
        this.navigation = navigation;
    }

    /**
     * Configures Loan columns and loads the current Member's initial snapshot.
     */
    @FXML
    private void initialize() {
        equipmentTypeColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().equipmentTypeName()));
        equipmentIdColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().equipmentId()));
        statusColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().status().name()));
        startedAtColumn.setCellValueFactory(loan -> new ReadOnlyStringWrapper(
                formatStartedAt(loan.getValue().startedAt())));
        endDateColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().endDate().toString()));
        overdueColumn.setCellValueFactory(loan -> new ReadOnlyStringWrapper(
                loan.getValue().overdue() ? "Overdue" : "—"));

        goodReturnRadioButton.setToggleGroup(returnConditionGroup);
        damagedReturnRadioButton.setToggleGroup(returnConditionGroup);
        goodReturnRadioButton.setSelected(true);
        returnConditionGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) ->
                updateDamagedReturnFields(newValue == damagedReturnRadioButton));
        loansTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, selectedLoan) -> onLoanSelectionChanged(selectedLoan));
        closeActionForms();
        setOperationMessage("", false);
        updateActionAvailability(null);
        loadLoans(false);
    }

    /**
     * Reloads the current authenticated Member's Loan snapshot without blocking the UI thread.
     */
    @FXML
    private void refresh() {
        if (!isLoadingLoans && !isSubmissionPending) {
            loadLoans(false);
        }
    }

    /**
     * Opens the return form for the selected active Loan.
     */
    @FXML
    private void openReturnForm() {
        MemberActiveLoan selectedLoan = selectedOnLoan();
        if (selectedLoan == null) {
            setOperationMessage("Select an ON_LOAN item to return.", true);
            return;
        }
        closeActionForms();
        formLoanId = selectedLoan.loanId();
        returnForm.setVisible(true);
        returnForm.setManaged(true);
        setOperationMessage("", false);
        updateActionAvailability(selectedLoan);
    }

    /**
     * Opens the loss form for the selected active Loan.
     */
    @FXML
    private void openLostForm() {
        MemberActiveLoan selectedLoan = selectedOnLoan();
        if (selectedLoan == null) {
            setOperationMessage("Select an ON_LOAN item to report lost.", true);
            return;
        }
        closeActionForms();
        formLoanId = selectedLoan.loanId();
        lostForm.setVisible(true);
        lostForm.setManaged(true);
        setOperationMessage("", false);
        updateActionAvailability(selectedLoan);
    }

    /**
     * Opens an image chooser for the selected damaged-return evidence.
     */
    @FXML
    private void chooseDamageImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select damage evidence");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "JPEG and PNG images", "*.jpg", "*.jpeg", "*.png"));
        File selectedFile = fileChooser.showOpenDialog(loansTable.getScene().getWindow());
        if (selectedFile != null) {
            damageImagePath = selectedFile.toPath();
            selectedDamageImageLabel.setText(selectedFile.getName());
            setOperationMessage("", false);
        }
    }

    /**
     * Submits a good or damaged return for the selected Loan.
     */
    @FXML
    private void submitReturn() {
        if (formLoanId == null) {
            setOperationMessage("Select an ON_LOAN item to return.", true);
            return;
        }
        boolean isDamaged = damagedReturnRadioButton.isSelected();
        String description = damageDescriptionArea.getText();
        Path selectedImagePath = damageImagePath;
        if (isDamaged && (description == null || description.isBlank())) {
            setOperationMessage("Enter a description of the damage.", true);
            damageDescriptionArea.requestFocus();
            return;
        }
        if (isDamaged && selectedImagePath == null) {
            setOperationMessage("Select a JPEG or PNG image of the damage.", true);
            chooseDamageImageButton.requestFocus();
            return;
        }

        String selectedLoanId = formLoanId;
        startSubmission(isDamaged ? "Damaged return submitted." : "Return submitted.", () -> {
            if (isDamaged) {
                memberLoanService.submitDamagedReturn(selectedLoanId, description,
                        selectedImagePath);
            } else {
                memberLoanService.submitGoodReturn(selectedLoanId);
            }
        });
    }

    /**
     * Submits a loss report for the selected Loan.
     */
    @FXML
    private void submitLost() {
        if (formLoanId == null) {
            setOperationMessage("Select an ON_LOAN item to report lost.", true);
            return;
        }
        String description = lossDescriptionArea.getText();
        if (description == null || description.isBlank()) {
            setOperationMessage("Enter a description of the loss.", true);
            lossDescriptionArea.requestFocus();
            return;
        }

        String selectedLoanId = formLoanId;
        startSubmission("Loss report submitted.",
                () -> memberLoanService.submitLost(selectedLoanId, description));
    }

    /**
     * Closes the return form and clears its selected evidence.
     */
    @FXML
    private void cancelReturn() {
        closeActionForms();
        updateActionAvailability(selectedLoan());
    }

    /**
     * Closes the loss form and clears its description.
     */
    @FXML
    private void cancelLost() {
        closeActionForms();
        updateActionAvailability(selectedLoan());
    }

    /**
     * Returns to the existing Member workspace.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.MEMBER_HOME);
    }

    /**
     * Starts one service operation on a worker thread and handles its result on the FX thread.
     */
    private void startSubmission(String successMessage, Runnable operation) {
        setSubmissionPending(true);
        showSubmissionPendingMessage();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                operation.run();
                return null;
            }
        };
        task.setOnSucceeded(event -> showSubmissionSaved(successMessage));
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            if (failure instanceof ApplicationException applicationException
                    && applicationException.transactionOutcome().orElse(null)
                            == TransactionOutcome.COMMITTED) {
                showSubmissionSaved(successMessage);
                return;
            }
            setSubmissionPending(false);
            setOperationMessage(actionFailureMessage(failure), true);
            updateActionAvailability(selectedLoan());
            if (requiresRefreshAfterFailure(failure)) {
                loadLoans(true);
            }
        });
        startWorker(task);
    }

    /**
     * Shows a saved submission independently of the subsequent Loan snapshot refresh.
     */
    private void showSubmissionSaved(String successMessage) {
        setSubmissionPending(false);
        closeActionForms();
        setOperationMessage(successMessage, false);
        loadLoans(true);
    }

    /**
     * Starts one role-screen operation on a daemon worker so file and database I/O stay off FX.
     */
    private static void startWorker(Task<?> task) {
        Thread worker = new Thread(task, "clubstock-member-loans");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Loads the current Member's loans on a worker and applies the snapshot on the FX thread.
     */
    private void loadLoans(boolean isPreservingOperationMessage) {
        if (isLoadingLoans) {
            return;
        }
        isLoadingLoans = true;
        if (!isPreservingOperationMessage) {
            setOperationMessage("", false);
        }
        loadingLabel.setVisible(true);
        loadingLabel.setManaged(true);
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        loansTable.setDisable(true);
        returnForm.setDisable(true);
        lostForm.setDisable(true);
        refreshButton.setDisable(true);
        updateActionAvailability(selectedLoan());

        Task<List<MemberActiveLoan>> task = new Task<>() {
            @Override
            protected List<MemberActiveLoan> call() {
                return loanQueryService.listActiveForMember();
            }
        };
        task.setOnSucceeded(event -> {
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
            loansTable.getSelectionModel().clearSelection();
            List<MemberActiveLoan> snapshot = task.getValue();
            loansTable.getItems().setAll(snapshot);
            boolean isEmpty = snapshot.isEmpty();
            emptyStateLabel.setVisible(isEmpty);
            emptyStateLabel.setManaged(isEmpty);
            loansTable.setVisible(!isEmpty);
            loansTable.setManaged(!isEmpty);
            isLoadingLoans = false;
            loansTable.setDisable(false);
            returnForm.setDisable(false);
            lostForm.setDisable(false);
            refreshButton.setDisable(false);
            updateActionAvailability(null);
        });
        task.setOnFailed(event -> {
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
            showLoadFailure(loadFailureMessage(task.getException()));
            isLoadingLoans = false;
            loansTable.setDisable(false);
            returnForm.setDisable(false);
            lostForm.setDisable(false);
            refreshButton.setDisable(false);
            updateActionAvailability(null);
        });
        startWorker(task);
    }

    /**
     * Returns the selected Loan only when its current status allows Member actions.
     */
    private MemberActiveLoan selectedOnLoan() {
        MemberActiveLoan selectedLoan = selectedLoan();
        if (selectedLoan == null || selectedLoan.status() != LoanStatus.ON_LOAN) {
            return null;
        }
        return selectedLoan;
    }

    /**
     * Returns the selected unresolved Loan, if one is selected.
     */
    private MemberActiveLoan selectedLoan() {
        return loansTable.getSelectionModel().getSelectedItem();
    }

    /**
     * Updates button enablement using the selected Loan's current lifecycle status.
     */
    private void updateActionAvailability(MemberActiveLoan selectedLoan) {
        boolean canAct = !isLoadingLoans && !isSubmissionPending && selectedLoan != null
                && selectedLoan.status() == LoanStatus.ON_LOAN;
        returnActionButton.setDisable(!canAct);
        lostActionButton.setDisable(!canAct);
        refreshButton.setDisable(isLoadingLoans || isSubmissionPending);
    }

    /**
     * Closes both forms and clears values that belong to their previously selected Loan.
     */
    private void closeActionForms() {
        returnForm.setVisible(false);
        returnForm.setManaged(false);
        lostForm.setVisible(false);
        lostForm.setManaged(false);
        formLoanId = null;
        damageImagePath = null;
        damageDescriptionArea.clear();
        lossDescriptionArea.clear();
        selectedDamageImageLabel.setText("No image selected");
        goodReturnRadioButton.setSelected(true);
        updateDamagedReturnFields(false);
    }

    /**
     * Shows or hides the image and description required for a damaged return.
     */
    private void updateDamagedReturnFields(boolean isDamaged) {
        damagedReturnFields.setVisible(isDamaged);
        damagedReturnFields.setManaged(isDamaged);
    }

    /**
     * Updates the active-action buttons after the selected Loan changes.
     */
    private void onLoanSelectionChanged(MemberActiveLoan selectedLoan) {
        if (formLoanId != null && (selectedLoan == null
                || !formLoanId.equals(selectedLoan.loanId()))) {
            closeActionForms();
            if (!isLoadingLoans) {
                setOperationMessage("", false);
            }
        }
        updateActionAvailability(selectedLoan);
    }

    /**
     * Disables controls while a report is being submitted on a worker thread.
     */
    private void setSubmissionPending(boolean isPending) {
        isSubmissionPending = isPending;
        loansTable.setDisable(isPending);
        returnForm.setDisable(isPending);
        lostForm.setDisable(isPending);
        updateActionAvailability(selectedLoan());
    }

    /**
     * Displays a safe message for the current Member action.
     */
    private void setOperationMessage(String message, boolean isError) {
        operationMessageLabel.setText(message);
        operationMessageLabel.getStyleClass().removeAll(
                "error-text", "success-text", "supporting-text");
        if (message == null || message.isBlank()) {
            operationMessageLabel.setVisible(false);
            operationMessageLabel.setManaged(false);
        } else {
            operationMessageLabel.getStyleClass().add(isError ? "error-text" : "success-text");
            operationMessageLabel.setVisible(true);
            operationMessageLabel.setManaged(true);
        }
    }

    /**
     * Shows neutral progress feedback while a report is being submitted.
     */
    private void showSubmissionPendingMessage() {
        operationMessageLabel.setText("Submitting report…");
        operationMessageLabel.getStyleClass().removeAll(
                "error-text", "success-text", "supporting-text");
        operationMessageLabel.getStyleClass().add("supporting-text");
        operationMessageLabel.setVisible(true);
        operationMessageLabel.setManaged(true);
    }

    /**
     * Shows a safe load failure after clearing all previous rows.
     */
    private void showLoadFailure(String message) {
        loansTable.getItems().clear();
        loansTable.setVisible(false);
        loansTable.setManaged(false);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Returns a display message from a load failure.
     */
    private static String loadFailureMessage(Throwable failure) {
        if (failure instanceof ApplicationException applicationException) {
            return applicationException.displayMessage();
        }
        return LOAD_FAILURE_TEXT;
    }

    /**
     * Returns a safe message from a submission failure.
     */
    private static String actionFailureMessage(Throwable failure) {
        if (failure instanceof ApplicationException applicationException) {
            return applicationException.displayMessage();
        }
        return ACTION_FAILURE_TEXT;
    }

    /**
     * Returns whether a failed action needs a fresh view because the displayed Loan state
     * may be stale.
     */
    private static boolean requiresRefreshAfterFailure(Throwable failure) {
        if (!(failure instanceof ApplicationException applicationException)) {
            return false;
        }
        TransactionOutcome outcome = applicationException.transactionOutcome().orElse(null);
        return outcome == TransactionOutcome.COMMIT_OUTCOME_UNKNOWN
                || applicationException.errorCode() == ApplicationErrorCode.CONFLICT
                || applicationException.errorCode() == ApplicationErrorCode.NOT_FOUND;
    }

    /**
     * Formats the loan start instant in the local timezone.
     */
    private static String formatStartedAt(Instant startedAt) {
        return STARTED_AT_FORMAT.format(startedAt);
    }
}
