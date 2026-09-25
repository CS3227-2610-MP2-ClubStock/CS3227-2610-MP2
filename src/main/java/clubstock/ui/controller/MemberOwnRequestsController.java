package clubstock.ui.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.TransactionOutcome;
import clubstock.application.request.MemberRequestService;
import clubstock.application.request.OwnRequest;
import clubstock.domain.request.LoanRequestStatus;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Displays the authenticated Member's own LoanRequests and pending cancellation action. */
public final class MemberOwnRequestsController {
    private static final String LOAD_FAILURE_TEXT =
            "Your requests could not be loaded. Try refreshing.";
    private static final String ACTION_FAILURE_TEXT =
            "The request could not be cancelled. Please try again.";
    private static final String UNKNOWN_CANCELLATION_OUTCOME_TEXT =
            "We could not confirm whether the request was cancelled. "
                    + "Check its current status before trying again.";
    private static final DateTimeFormatter REQUESTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

    private final MemberRequestService requestService;
    private final NavigationService navigation;

    @FXML
    private Button backButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label loadingLabel;
    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TableView<OwnRequest> requestsTable;
    @FXML
    private TableColumn<OwnRequest, String> equipmentTypeColumn;
    @FXML
    private TableColumn<OwnRequest, Number> requestedQuantityColumn;
    @FXML
    private TableColumn<OwnRequest, LocalDate> startDateColumn;
    @FXML
    private TableColumn<OwnRequest, LocalDate> endDateColumn;
    @FXML
    private TableColumn<OwnRequest, String> statusColumn;
    @FXML
    private TableColumn<OwnRequest, String> requestedAtColumn;
    @FXML
    private TableColumn<OwnRequest, String> approvedQuantityColumn;

    private boolean isBusy;

    /**
     * Creates the Member own-requests controller.
     *
     * @param requestService Member-authorized request query and cancellation service.
     * @param navigation Navigation boundary.
     * @throws IllegalArgumentException If either dependency is null.
     */
    public MemberOwnRequestsController(MemberRequestService requestService,
            NavigationService navigation) {
        if (requestService == null || navigation == null) {
            throw new IllegalArgumentException("Own-requests dependencies cannot be null.");
        }
        this.requestService = requestService;
        this.navigation = navigation;
    }

    /** Configures request columns and loads the initial Member-only snapshot. */
    @FXML
    private void initialize() {
        equipmentTypeColumn.setCellValueFactory(request ->
                new ReadOnlyStringWrapper(request.getValue().equipmentTypeName()));
        requestedQuantityColumn.setCellValueFactory(request ->
                new ReadOnlyIntegerWrapper(request.getValue().requestedQuantity()));
        startDateColumn.setCellValueFactory(request ->
                new ReadOnlyObjectWrapper<>(request.getValue().requestedStartDate()));
        endDateColumn.setCellValueFactory(request ->
                new ReadOnlyObjectWrapper<>(request.getValue().requestedEndDate()));
        statusColumn.setCellValueFactory(request ->
                new ReadOnlyStringWrapper(request.getValue().status().name()));
        requestedAtColumn.setCellValueFactory(request -> new ReadOnlyStringWrapper(
                formatRequestedAt(request.getValue().requestedAt())));
        approvedQuantityColumn.setCellValueFactory(request -> new ReadOnlyStringWrapper(
                request.getValue().approvedQuantity().isPresent()
                        ? Integer.toString(request.getValue().approvedQuantity().getAsInt()) : "—"));
        requestsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> updateActionStates());
        clearResults();
        clearStatus();
        loadRequests();
    }

    /** Reloads the current authenticated Member's request snapshot. */
    @FXML
    private void refreshRequests() {
        if (!isBusy) {
            clearStatus();
            loadRequests();
        }
    }

    /** Returns to the existing Member workspace. */
    @FXML
    private void goBack() {
        if (!isBusy) {
            navigation.show(Route.MEMBER_HOME);
        }
    }

    /** Confirms and cancels the selected pending request through the shared service. */
    @FXML
    private void cancelSelectedRequest() {
        OwnRequest selectedRequest = requestsTable.getSelectionModel().getSelectedItem();
        if (isBusy || selectedRequest == null
                || selectedRequest.status() != LoanRequestStatus.PENDING) {
            return;
        }

        ButtonType confirmCancellation = new ButtonType("Cancel request", ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Cancel request");
        confirmation.setHeaderText("Cancel this pending request?");
        confirmation.setContentText("This request will no longer be available for approval.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, confirmCancellation);
        if (confirmation.showAndWait().filter(response -> response == confirmCancellation).isEmpty()) {
            return;
        }

        performCancellation(selectedRequest);
    }

    /**
     * Performs cancellation and refreshes authoritative state after success or a stale-state result.
     *
     * @param selectedRequest Previously selected pending request.
     */
    private void performCancellation(OwnRequest selectedRequest) {
        clearStatus();
        setBusy(true);
        boolean cancelled = false;
        boolean shouldRefresh = false;
        String failureMessage = null;
        try {
            requestService.cancelRequest(selectedRequest.loanRequestId());
            cancelled = true;
        } catch (ApplicationException exception) {
            TransactionOutcome transactionOutcome = exception.transactionOutcome().orElse(null);
            if (transactionOutcome == TransactionOutcome.COMMITTED) {
                cancelled = true;
            } else if (transactionOutcome == TransactionOutcome.COMMIT_OUTCOME_UNKNOWN) {
                shouldRefresh = true;
                failureMessage = UNKNOWN_CANCELLATION_OUTCOME_TEXT;
            } else {
                failureMessage = exception.displayMessage();
                shouldRefresh = exception.errorCode() == ApplicationErrorCode.CONFLICT
                        || exception.errorCode() == ApplicationErrorCode.NOT_FOUND;
                if (exception.errorCode() == ApplicationErrorCode.AUTHORIZATION_DENIED) {
                    clearResults();
                }
            }
        } catch (RuntimeException exception) {
            failureMessage = ACTION_FAILURE_TEXT;
        } finally {
            setBusy(false);
        }

        if (cancelled) {
            showSuccess("Request cancelled.");
        }
        if (cancelled || shouldRefresh) {
            loadRequests();
        }
        if (failureMessage != null) {
            showError(failureMessage);
        }
    }

    /**
     * Replaces the displayed rows with a fresh service snapshot and clears rows on any load error.
     *
     * @return Whether the snapshot loaded successfully.
     */
    private boolean loadRequests() {
        if (isBusy) {
            return false;
        }
        setBusy(true);
        clearResults();
        loadingLabel.setVisible(true);
        loadingLabel.setManaged(true);
        try {
            List<OwnRequest> snapshot = requestService.listOwnRequests();
            requestsTable.getItems().setAll(snapshot);
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
            boolean isEmpty = snapshot.isEmpty();
            emptyStateLabel.setVisible(isEmpty);
            emptyStateLabel.setManaged(isEmpty);
            requestsTable.setVisible(!isEmpty);
            requestsTable.setManaged(!isEmpty);
            hideError();
            return true;
        } catch (ApplicationException exception) {
            showLoadFailure(exception.displayMessage());
            return false;
        } catch (RuntimeException exception) {
            showLoadFailure(LOAD_FAILURE_TEXT);
            return false;
        } finally {
            loadingLabel.setVisible(false);
            loadingLabel.setManaged(false);
            setBusy(false);
        }
    }

    /** Clears prior results before loading so a failed query cannot expose stale request data. */
    private void clearResults() {
        requestsTable.getItems().clear();
        requestsTable.getSelectionModel().clearSelection();
        requestsTable.setVisible(false);
        requestsTable.setManaged(false);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        hideError();
        loadingLabel.setVisible(false);
        loadingLabel.setManaged(false);
        updateActionStates();
    }

    /** Shows a safe load failure after clearing all previous rows. */
    private void showLoadFailure(String message) {
        requestsTable.getItems().clear();
        requestsTable.setVisible(false);
        requestsTable.setManaged(false);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /** Hides and clears the load error state. */
    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /** Updates selection actions and duplicate-operation controls. */
    private void updateActionStates() {
        OwnRequest selectedRequest = requestsTable.getSelectionModel().getSelectedItem();
        cancelButton.setDisable(isBusy || selectedRequest == null
                || selectedRequest.status() != LoanRequestStatus.PENDING);
        refreshButton.setDisable(isBusy);
        backButton.setDisable(isBusy);
        requestsTable.setDisable(isBusy);
    }

    /** Disables screen actions for the duration of a synchronous service operation. */
    private void setBusy(boolean busy) {
        isBusy = busy;
        updateActionStates();
    }

    /** Shows an error using the action status label. */
    private void showError(String message) {
        statusLabel.getStyleClass().removeAll("success-text", "supporting-text");
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
        statusLabel.setText(message);
    }

    /** Shows a successful cancellation using the action status label. */
    private void showSuccess(String message) {
        statusLabel.getStyleClass().removeAll("error-text", "supporting-text");
        if (!statusLabel.getStyleClass().contains("success-text")) {
            statusLabel.getStyleClass().add("success-text");
        }
        statusLabel.setText(message);
    }

    /** Clears action feedback and restores neutral styling. */
    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().removeAll("error-text", "success-text");
        if (!statusLabel.getStyleClass().contains("supporting-text")) {
            statusLabel.getStyleClass().add("supporting-text");
        }
    }

    /** Formats a submission timestamp in the current device timezone. */
    private static String formatRequestedAt(Instant requestedAt) {
        return REQUESTED_AT_FORMAT.format(requestedAt);
    }
}
