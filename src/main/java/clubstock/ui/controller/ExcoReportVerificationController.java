package clubstock.ui.controller;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import clubstock.application.ApplicationException;
import clubstock.application.verification.DamageEvidence;
import clubstock.application.verification.PendingVerification;
import clubstock.application.verification.VerificationService;
import clubstock.ui.DialogStyling;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

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
    private TableColumn<PendingVerification, String> imageAvailableColumn;
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
    private Button viewImageButton;
    @FXML
    private Label statusLabel;
    @FXML
    private Label reportDetailsLabel;

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
        imageAvailableColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                value.getValue().hasDamageImage() ? "Available" : "—"));
        evidenceColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(
                blankAsDash(value.getValue().description())));
        reportsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> updateActions(selected));
        updateActions(null);
        refreshReports();
    }

    @FXML
    private void refresh() {
        refreshReports();
    }

    private boolean refreshReports() {
        try {
            var reports = service.listPending();
            reportsTable.setItems(FXCollections.observableArrayList(reports));
            statusLabel.setText(reports.isEmpty()
                    ? "No reports await verification."
                    : "Select a report to verify.");
            updateActions(reportsTable.getSelectionModel().getSelectedItem());
            return true;
        } catch (ApplicationException exception) {
            statusLabel.setText(exception.displayMessage());
        } catch (RuntimeException exception) {
            statusLabel.setText("Reports could not be refreshed. Please try again.");
        }
        reportsTable.getItems().clear();
        reportsTable.getSelectionModel().clearSelection();
        updateActions(null);
        return false;
    }

    @FXML
    private void verifyGood() {
        resolveSelected(report -> service.verifyGood(report.loanId()), "Return verified as good.",
                "GOOD and AVAILABLE");
    }

    @FXML
    private void verifyDamagedAvailable() {
        resolveSelected(report -> service.verifyDamaged(report.loanId(), true),
                "Return verified as damaged and available.", "DAMAGED and AVAILABLE");
    }

    @FXML
    private void verifyDamagedUnavailable() {
        resolveSelected(report -> service.verifyDamaged(report.loanId(), false),
                "Return verified as damaged and unavailable.", "DAMAGED and UNAVAILABLE");
    }

    @FXML
    private void confirmLost() {
        resolveSelected(report -> service.confirmLost(report.loanId()), "Loss report confirmed.",
                "LOST and UNAVAILABLE");
    }

    @FXML
    private void viewDamageImage() {
        selected().filter(PendingVerification::hasDamageImage).ifPresent(report -> {
            try {
                showDamageImage(service.loadDamageEvidence(report.loanId()));
            } catch (ApplicationException exception) {
                statusLabel.setText(exception.displayMessage());
            } catch (RuntimeException exception) {
                statusLabel.setText("The submitted damage image could not be displayed.");
            }
        });
    }

    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    private void resolveSelected(ReportResolution resolution, String successMessage,
            String finalItemState) {
        selected().ifPresent(report -> {
            Alert confirmation = new Alert(AlertType.CONFIRMATION);
            DialogStyling.apply(confirmation);
            confirmation.setTitle("Confirm verification");
            confirmation.setHeaderText("Resolve " + report.equipmentId() + " as "
                    + finalItemState + "?");
            confirmation.setContentText("Member: " + report.memberName()
                    + "\nEquipment: " + report.equipmentTypeName()
                    + "\nThis completes the Loan and changes the item's authoritative state.");
            confirmation.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                resolution.resolve(report);
                if (refreshReports()) {
                    statusLabel.setText(successMessage);
                }
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
        viewImageButton.setDisable(report == null || !report.hasDamageImage());
        reportDetailsLabel.setText(report == null
                ? "Select a report to review its advisory details."
                : "Loan ID: " + report.loanId()
                        + "   •   Reported condition: " + blankAsDash(report.reportedCondition())
                        + "   •   Damage image: " + (report.hasDamageImage() ? "Available" : "None")
                        + "\nDescription: " + blankAsDash(report.description()));
    }

    private void showDamageImage(DamageEvidence evidence) {
        Image image = new Image(new ByteArrayInputStream(evidence.bytes()));
        if (image.isError()) {
            statusLabel.setText("The submitted damage image could not be displayed.");
            return;
        }
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(600);
        imageView.setFitHeight(450);

        Dialog<Void> dialog = new Dialog<>();
        DialogStyling.apply(dialog);
        dialog.setTitle("Submitted damage image");
        dialog.setHeaderText("Review this evidence before resolving the return.");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new VBox(imageView));
        dialog.showAndWait();
    }

    private static String blankAsDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    @FunctionalInterface
    private interface ReportResolution {
        void resolve(PendingVerification report);
    }
}
