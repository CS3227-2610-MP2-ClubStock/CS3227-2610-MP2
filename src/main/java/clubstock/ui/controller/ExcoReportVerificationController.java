package clubstock.ui.controller;

import java.util.Optional;

import clubstock.application.ApplicationException;
import clubstock.application.verification.PendingVerification;
import clubstock.application.verification.VerificationService;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/** Presents Exco's pending return and loss-report verification workflow. */
public final class ExcoReportVerificationController {
    private static final String RETURN_REPORT = "RETURN";
    private static final String LOSS_REPORT = "LOSS";

    private final VerificationService service;
    private final NavigationService navigation;

    @FXML
    private TableView<PendingVerification> reportsTable;
    @FXML
    private TableColumn<PendingVerification, String> memberColumn;
    @FXML
    private TableColumn<PendingVerification, String> typeColumn;
    @FXML
    private TableColumn<PendingVerification, String> equipmentIdColumn;
    @FXML
    private TableColumn<PendingVerification, String> kindColumn;
    @FXML
    private TableColumn<PendingVerification, String> conditionColumn;
    @FXML
    private TableColumn<PendingVerification, String> imageReferenceColumn;
    @FXML
    private TableColumn<PendingVerification, String> evidenceColumn;
    @FXML
    private Button goodButton;
    @FXML
    private Button damagedAvailableButton;
    @FXML
    private Button damagedUnavailableButton;
    @FXML
    private Button lostButton;
    @FXML
    private Label statusLabel;

    /**
     * Creates the controller with the service and navigation boundaries.
     *
     * @param service Exco verification service.
     * @param navigation Shared navigation service.
     */
    public ExcoReportVerificationController(VerificationService service,
            NavigationService navigation) {
        if (service == null || navigation == null) {
            throw new IllegalArgumentException("Report verification dependencies cannot be null.");
        }
        this.service = service;
        this.navigation = navigation;
    }

    @FXML
    private void initialize() {
        memberColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().memberName()));
        typeColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().equipmentTypeName()));
        equipmentIdColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().equipmentId()));
        kindColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().reportKind()));
        conditionColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().reportedCondition()));
        imageReferenceColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                blankAsDash(value.getValue().imageReference())));
        evidenceColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                blankAsDash(value.getValue().description())));
        reportsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateActions(selected));
        updateActions(null);
        refresh();
    }

    @FXML
    private void refresh() {
        try {
            var reports = service.listPending();
            reportsTable.setItems(FXCollections.observableArrayList(reports));
            statusLabel.setText(reports.isEmpty()
                    ? "No reports await verification."
                    : "Select a report to verify.");
            updateActions(reportsTable.getSelectionModel().getSelectedItem());
        } catch (ApplicationException exception) {
            statusLabel.setText(exception.displayMessage());
        } catch (RuntimeException exception) {
            statusLabel.setText("Reports could not be refreshed. Please try again.");
        }
    }

    @FXML
    private void verifyGood() {
        resolveSelected(report -> service.verifyGood(report.loanId()), "Return verified as good.");
    }

    @FXML
    private void verifyDamagedAvailable() {
        resolveSelected(report -> service.verifyDamaged(report.loanId(), true),
                "Return verified as damaged and available.");
    }

    @FXML
    private void verifyDamagedUnavailable() {
        resolveSelected(report -> service.verifyDamaged(report.loanId(), false),
                "Return verified as damaged and unavailable.");
    }

    @FXML
    private void confirmLost() {
        resolveSelected(report -> service.confirmLost(report.loanId()), "Loss report confirmed.");
    }

    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    private void resolveSelected(ReportResolution resolution, String successMessage) {
        selected().ifPresent(report -> {
            try {
                resolution.resolve(report);
                refresh();
                statusLabel.setText(successMessage);
            } catch (ApplicationException exception) {
                statusLabel.setText(exception.displayMessage());
            } catch (RuntimeException exception) {
                statusLabel.setText("The report could not be resolved. Please try again.");
            }
        });
    }

    private Optional<PendingVerification> selected() {
        PendingVerification value = reportsTable.getSelectionModel().getSelectedItem();
        if (value == null) {
            statusLabel.setText("Select a pending report first.");
        }
        return Optional.ofNullable(value);
    }

    private void updateActions(PendingVerification report) {
        boolean returnReport = report != null && RETURN_REPORT.equals(report.reportKind());
        boolean lossReport = report != null && LOSS_REPORT.equals(report.reportKind());
        goodButton.setDisable(!returnReport);
        damagedAvailableButton.setDisable(!returnReport);
        damagedUnavailableButton.setDisable(!returnReport);
        lostButton.setDisable(!lossReport);
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    @FunctionalInterface
    private interface ReportResolution {
        void resolve(PendingVerification report);
    }
}
