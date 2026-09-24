package clubstock.ui.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import clubstock.application.ApplicationException;
import clubstock.application.request.ExcoPendingRequest;
import clubstock.application.request.ExcoRequestService;
import clubstock.application.request.PendingRequestSelection;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Presents the Exco pending LoanRequest queue and manual rejection action.
 */
public final class ExcoRequestQueueController {
    private static final String UNEXPECTED_ERROR =
            "Pending-request administration could not be completed. Please try again.";
    private final ExcoRequestService excoRequestService;
    private final NavigationService navigation;
    @FXML
    private TableView<ExcoPendingRequest> requestsTable;
    @FXML
    private TableColumn<ExcoPendingRequest, String> requestIdColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, String> memberColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, String> equipmentTypeColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, Number> requestedQuantityColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, Number> availableQuantityColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, LocalDate> startDateColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, LocalDate> endDateColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, Instant> requestedAtColumn;
    @FXML
    private TableColumn<ExcoPendingRequest, String> detailsColumn;
    @FXML
    private Button rejectRequestButton;
    @FXML
    private Label emptyQueueLabel;
    @FXML
    private Label statusLabel;
    private ExcoPendingRequest selectedRequest;

    /**
     * Creates the Exco request-queue controller.
     *
     * @param excoRequestService Exco-authorized request query and rejection operations.
     * @param navigation Navigation boundary.
     */
    public ExcoRequestQueueController(ExcoRequestService excoRequestService,
            NavigationService navigation) {
        if (excoRequestService == null || navigation == null) {
            throw new IllegalArgumentException("Exco request queue dependencies cannot be null.");
        }
        this.excoRequestService = excoRequestService;
        this.navigation = navigation;
    }

    /**
     * Configures the queue columns and loads current pending requests.
     */
    @FXML
    private void initialize() {
        requestIdColumn.setCellValueFactory(request ->
                new ReadOnlyStringWrapper(request.getValue().loanRequestId()));
        memberColumn.setCellValueFactory(request -> new ReadOnlyStringWrapper(
                request.getValue().memberName() + " (" + request.getValue().memberId() + ")"));
        equipmentTypeColumn.setCellValueFactory(request -> new ReadOnlyStringWrapper(
                request.getValue().equipmentTypeName() + " ("
                        + request.getValue().equipmentTypeId() + ")"));
        requestedQuantityColumn.setCellValueFactory(request ->
                new ReadOnlyIntegerWrapper(request.getValue().requestedQuantity()));
        availableQuantityColumn.setCellValueFactory(request ->
                new ReadOnlyIntegerWrapper(request.getValue().availableQuantity()));
        startDateColumn.setCellValueFactory(request -> new ReadOnlyObjectWrapper<>(
                request.getValue().requestedStartDate()));
        endDateColumn.setCellValueFactory(request -> new ReadOnlyObjectWrapper<>(
                request.getValue().requestedEndDate()));
        requestedAtColumn.setCellValueFactory(request -> new ReadOnlyObjectWrapper<>(
                request.getValue().requestedAt()));
        detailsColumn.setCellValueFactory(request -> new ReadOnlyStringWrapper(
                request.getValue().details().orElse("")));
        requestsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> selectRequest(current));
        rejectRequestButton.setDisable(true);
        refreshQueue();
    }

    /**
     * Requests confirmation before rejecting the selected pending request.
     */
    @FXML
    private void rejectSelectedRequest() {
        if (selectedRequest == null) {
            showError("Select a pending request first.");
            return;
        }
        ButtonType reject = new ButtonType("Reject request", ButtonData.OK_DONE);
        Alert confirmation = new Alert(AlertType.CONFIRMATION);
        confirmation.setTitle("Reject LoanRequest");
        confirmation.setHeaderText("Reject the selected pending request?");
        confirmation.setContentText("This changes the request to REJECTED. It cannot be approved later.");
        confirmation.getButtonTypes().setAll(ButtonType.CANCEL, reject);
        confirmation.showAndWait().filter(response -> response == reject).ifPresent(response ->
                reject(new PendingRequestSelection(selectedRequest.loanRequestId())));
    }

    /**
     * Reloads the pending-request queue from shared state.
     */
    @FXML
    private void refreshRequests() {
        refreshQueue();
    }

    private boolean refreshQueue() {
        try {
            List<ExcoPendingRequest> requests = excoRequestService.listPendingRequests();
            requestsTable.setItems(FXCollections.observableArrayList(requests));
            emptyQueueLabel.setVisible(requests.isEmpty());
            emptyQueueLabel.setManaged(requests.isEmpty());
            selectedRequest = null;
            rejectRequestButton.setDisable(true);
            clearStatus();
            return true;
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
            return false;
        } catch (RuntimeException exception) {
            showError(UNEXPECTED_ERROR);
            return false;
        }
    }

    /**
     * Returns to the Exco workspace.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    private void reject(PendingRequestSelection selection) {
        try {
            excoRequestService.rejectRequest(selection);
            if (refreshQueue()) {
                showSuccess("LoanRequest rejected.");
            }
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showError(UNEXPECTED_ERROR);
        }
    }

    private void selectRequest(ExcoPendingRequest request) {
        selectedRequest = request;
        rejectRequestButton.setDisable(request == null);
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
